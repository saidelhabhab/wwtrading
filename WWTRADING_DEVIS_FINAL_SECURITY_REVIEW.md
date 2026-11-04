# Rapport d'Audit Technique Final : Durcissement de Sécurité /devis

> **Document de référence :** Revue technique de sécurité post-implémentation  
> **Cible :** Modifications décrites dans `WWTRADING_DEVIS_SECURITY_HARDENING_REPORT.md`  
> **Date de réalisation :** 27 Octobre 2026  
> **Statut de l'audit :** AUDIT EXCLUSIF — AUCUN FICHIER APPLICATIF, AUCUNE CONFIGURATION ET AUCUNE DONNÉE N'ONT ÉTÉ MODIFIÉS  
> **Environnement :** Architecture microservices Spring Boot 3.5 / Spring Cloud Gateway / Keycloak / Angular 19  

---

## 1. Synthèse Globale et Décision Recommandée

### Avis d'Audit :
🟢 **APPROBATION CONDITIONNELLE POUR L'ENVIRONNEMENT DE STAGING / PRÉ-PRODUCTION**  
⚠️ **RÉSERVE AVANT OUVERTURE SUR L'INTERNET PUBLIC SANS REVERSE-PROXY DE TÊTE (INGRESS/WAF)**

Les vulnérabilités majeures identifiées dans le premier audit ont été traitées avec rigueur et élégance :
- L'attaque par usurpation de devis invité via un compte Keycloak non vérifié (**SEC-02**) est **totalement neutralisée**.
- Le format du téléphone et les quantités excessives (**SEC-03**, **SEC-04**) sont **strictement validés**.
- L'unicité des requêtes par `Idempotency-Key` est **garantie au niveau persistant** par PostgreSQL.
- Un limiteur de débit Token-Bucket (`PublicQuotationRateLimitFilter`) a été introduit sur `api-gateway`.

Cependant, l'audit approfondi du code et de l'infrastructure met en évidence **deux risques architecturaux à connaître** avant un déploiement public en production :
1. **Risque d'usurpation d'adresse IP (`X-Forwarded-For`) :** En l'absence d'un reverse-proxy de confiance (Cloudflare, Nginx, AWS ALB) configuré pour assainir ou remplacer cet en-tête avant d'atteindre `api-gateway`, un attaquant peut facilement contourner le rate limiting en envoyant une fausse adresse IP arbitraire.
2. **Réutilisation de clé d'idempotence avec un payload différent :** Le backend renvoie le devis existant dès que la clé d'idempotence correspond, sans vérifier que l'auteur ou le payload est identique, ce qui pourrait divulguer un devis si deux appelants distincts génèrent la même clé d'idempotence.

---

## 2. Analyse Approfondie par Axe Technique

### 2.1. Axe 1 — Rate Limiting (`api-gateway`)

#### 1. Capacité, Débit de Recharge et Calcul de `Retry-After`
- **Implémentation :** [PublicQuotationRateLimitFilter.java](file:///backend/services/api-gateway/src/main/java/ma/wwtrading/gateway/security/PublicQuotationRateLimitFilter.java#L39-L56)
- **Constat :**
  - Capacité par défaut : `capacity = 10` jetons (tolère une rafale initiale de 10 demandes).
  - Débit de recharge : `refillSeconds = 60` secondes, soit `refillRatePerNano = 10 / (60 * 10^9) = 1 jeton toutes les 6 secondes`.
  - Calcul de l'attente :
    ```java
    double missingTokens = 1.0 - availableTokens;
    double waitNanos = missingTokens / refillRatePerNano;
    return Math.max(1, (long) Math.ceil(waitNanos / 1_000_000_000.0));
    ```
  - **Évaluation :** Le calcul est mathématiquement exact. Dès que le seau est vide, chaque requête rejetée reçoit la durée exacte nécessaire pour reconstituer 1 jeton complet (entre 1 et 60 secondes).

#### 2. Comportement en Cas de Requêtes Concurrentes
- **Implémentation :** Méthode `TokenBucket.tryConsume()` déclarée `synchronized`.
- **Évaluation :** Thread-safe. L'obtention du seau par IP `buckets.get(clientIp, key -> new TokenBucket(...))` via l'API Caffeine est atomique. Deux threads concurrents pour la même IP ne peuvent pas décrémenter simultanément sous le seuil zéro.

#### 3. Déploiement Multi-Instance (Effet Cluster)
- **Constat :** `Caffeine.newBuilder()...build()` est un cache local in-memory à la JVM.
- **Conséquence :** Si `api-gateway` est déployée en $N$ instances (ex. 3 réplicas sous Kubernetes ou derrière un Round-Robin sans affinité de session) :
  - Chaque instance possède son propre seau de 10 jetons.
  - La capacité réelle pour un attaquant dont les requêtes sont distribuées de manière uniforme est de $N \times 10$ requêtes par minute (soit 30 requêtes/min pour 3 instances).
- **Gravité :** Risque faible à moyen. Même multipliée par 3 ou 4, la charge reste de l'ordre de 30 à 40 requêtes par minute, ce qui protège efficacement `quotation-service` d'un déni de service massif.

#### 4. Gestion de `X-Forwarded-For` et Risque de Falsification d'IP (P1 Conditionnel)
- **Preuve dans le code :** [PublicQuotationRateLimitFilter.java](file:///backend/services/api-gateway/src/main/java/ma/wwtrading/gateway/security/PublicQuotationRateLimitFilter.java#L88-L96)
  ```java
  String xForwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
  if (xForwardedFor != null && !xForwardedFor.isBlank()) {
      String candidate = xForwardedFor.split(",")[0].trim();
      if (isValidIp(candidate)) {
          return candidate;
      }
  }
  ```
- **Vulnérabilité démontrée :**
  - Le code prend le **premier** élément de l'en-tête `X-Forwarded-For`.
  - Dans l'architecture `compose.yaml`, `api-gateway` expose directement son port `8080:8080` à l'extérieur, sans reverse-proxy intermédiaire.
  - Un attaquant lançant un script peut envoyer :
    ```bash
    curl -X POST http://localhost:8080/api/quotations -H "X-Forwarded-For: 198.51.100.1" ...
    curl -X POST http://localhost:8080/api/quotations -H "X-Forwarded-For: 198.51.100.2" ...
    ```
    Chaque requête sera considérée comme émanant d'une adresse IP distincte valide, créant un nouveau seau et **contournant intégralement le rate limiting**.
- **Condition de sécurité en production :** Cette implémentation n'est sécurisée que si le reverse-proxy de bordure (ex. Cloudflare, AWS CloudFront/ALB, Nginx) **écrase systématiquement** l'en-tête `X-Forwarded-For` avec l'adresse IP cliente réelle (`proxy_set_header X-Forwarded-For $remote_addr;`).

#### 5. Comportement après Redémarrage et Gestion d'Erreurs
- **Redémarrage :** Le cache Caffeine étant en mémoire, un redémarrage réinitialise immédiatement les quotas à 10 jetons.
- **Erreurs internes :** Aucune exception non gérée n'est levée dans le filtre ; si le service est désactivé via `wwtrading.gateway.rate-limit.quotations.enabled=false`, le trafic passe de manière transparente.

#### 6. Conformité HTTP 429 et RFC 7807
- **Constat :** En cas de blocage, le filtre renvoie :
  - Statut : `HTTP 429 Too Many Requests`.
  - En-tête : `Retry-After: <secondes>`.
  - `Content-Type: application/problem+json`.
  - Corps au format Problem Details RFC 7807 avec les champs `type`, `title`, `status`, `detail`, `retryAfterSeconds`.
- **Évaluation :** Parfaitement conforme aux spécifications.

---

### 2.2. Axe 2 — Propriété et Isolation des Devis (`quotation-service`)

#### 1. Provenance et Validation de `email_verified`
- **Preuve dans le code :** [QuotationApplicationService.java](file:///backend/services/quotation-service/src/main/java/ma/wwtrading/quotations/application/QuotationApplicationService.java#L342-L348)
  ```java
  private boolean isEmailVerified(JwtAuthenticationToken authentication) {
      if (authentication == null || authentication.getToken() == null) {
          return false;
      }
      Boolean verified = authentication.getToken().getClaimAsBoolean("email_verified");
      return Boolean.TRUE.equals(verified);
  }
  ```
- **Évaluation :**
  - Le JWT est préalablement validé cryptographiquement par Nimbus (`SecurityConfig.java`) via les clés publiques JWK de Keycloak (`jwt.jwk-set-uri`).
  - L'utilisation de `Boolean.TRUE.equals(verified)` assure un traitement sûr : si la claim est absente (`null`), de type chaîne (`"true"` non converti), ou `false`, la méthode retourne `false`.

#### 2. Protection Anti-Usurpation de Devis Invités (Correction SEC-02)
- **Preuve dans le code :** [QuotationApplicationService.java](file:///backend/services/quotation-service/src/main/java/ma/wwtrading/quotations/application/QuotationApplicationService.java#L320-L340)
  - Si un devis a été créé par un prospect invité (`customerSubject == null`) :
    - Un utilisateur authentifié dont l'email n'est **pas vérifié** se voit renvoyer `false` (`isOwner == false`), ce qui déclenche un `HTTP 403 Forbidden`.
    - L'attaquant ne peut ni lire, ni modifier, ni annuler le devis.
  - Si l'email est vérifié (`email_verified == true`) et correspond à l'email du devis invité :
    - La consultation est autorisée.
    - Le devis est immédiatement lié de façon irréversible à son compte : `quotation.assignCustomerSubject(jwtSubject)`.
    - Les requêtes ultérieures utilisent exclusivement `jwtSubject.equals(quotation.getCustomerSubject())`.

#### 3. Protection Anti-IDOR (Insecure Direct Object Reference)
- **Constat :**
  - Un client A (`sub-user-A`) qui tente d'accéder au devis d'un client B (`sub-user-B`) via `GET /api/quotations/{id}` est bloqué par la condition `jwtSubject.equals(quotation.getCustomerSubject())`.
  - La réponse est `HTTP 403 Forbidden` (`Access denied to this quotation request`).
  - Aucune information du devis (nom, coordonnées, matériel, montant, notes) n'est renvoyée.

#### 4. Permissions Internes (Staff)
- **Constat :** Les rôles `ROLE_ADMIN`, `ROLE_MANAGER` et `ROLE_COMMERCIAL` sont explicitement autorisés par `isStaff(...)`. Ils peuvent consulter tous les devis, éditer les statuts et établir les propositions tarifaires. Les clients n'ont pas accès aux endpoints d'administration.

---

### 2.3. Axe 3 — Idempotence et Anti-Doublons

#### 1. Compatibilité PostgreSQL et Enregistrements Historiques
- **Migration :** [V7__add_idempotency_key.sql](file:///backend/services/quotation-service/src/main/resources/db/migration/V7__add_idempotency_key.sql)
  ```sql
  ALTER TABLE quotation ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(128);
  CREATE UNIQUE INDEX IF NOT EXISTS idx_quotation_idempotency_key ON quotation (idempotency_key) WHERE idempotency_key IS NOT NULL;
  ```
- **Évaluation :**
  - L'index partiel `WHERE idempotency_key IS NOT NULL` est standard en PostgreSQL.
  - Tous les devis historiques créés avant la migration possèdent `idempotency_key = NULL` et ne violent pas la contrainte d'unicité.

#### 2. Comportement en Cas de Requêtes Concurrentes avec la Même Clé
- **Scénario :** Deux requêtes simultanées arrivent avec la même clé `idemp-12345` sur deux instances distinctes.
  1. Les deux requêtes ne trouvent pas la clé en base (`findByIdempotencyKey` vide).
  2. Les deux tentent `repository.save(...)`.
  3. L'une réussit sa transaction SQL, l'autre déclenche une violation d'index unique PostgreSQL (`DataIntegrityViolationException`).
  4. [ApiExceptionHandler.java](file:///backend/services/quotation-service/src/main/java/ma/wwtrading/quotations/api/ApiExceptionHandler.java#L41-L45) intercepte `DataIntegrityViolationException` et renvoie un statut propre `HTTP 409 Conflict`.
- **Évaluation :** Aucun risque d'enregistrer deux devis avec la même clé, même en environnement distribué.

#### 3. Risque Identifié : Réutilisation d'une Clé avec Payload Différent (SEC-IDEMP-01)
- **Preuve dans le code :** [QuotationApplicationService.java](file:///backend/services/quotation-service/src/main/java/ma/wwtrading/quotations/application/QuotationApplicationService.java#L70-L75)
  ```java
  if (effectiveIdempotencyKey != null) {
      var existing = repository.findByIdempotencyKey(effectiveIdempotencyKey);
      if (existing.isPresent()) {
          return mapper.toResponse(existing.get());
      }
  }
  ```
- **Problème :**
  - Contrairement à `order-service` qui vérifie `if (!customerReference.equalsIgnoreCase(existingOrder.getCustomerReference())) throw Conflict;`, `quotation-service` ne compare pas l'email de la nouvelle requête avec l'email du devis déjà enregistré sous cette clé.
  - **Scénario :** Si un client B soumet un devis avec une clé statique ou prévisible déjà utilisée par un client A (ex. `IDEM-1`), le serveur renvoie le devis de A à B avec le code HTTP 200/201.
  - **Correction recommandée :** Vérifier que `existing.getEmail().equalsIgnoreCase(request.email())` avant de renvoyer le devis, sinon renvoyer `HTTP 409 Conflict`.

#### 4. Cache Anti-Rebond en Mémoire (30 secondes)
- **Constat :** Le cache `rapidSubmissions` (basé sur un hash `email|phone|serviceType|products`) intercepte les doubles-clics rapides dans une même instance JVM sans solliciter PostgreSQL ni `product-service`.
- **Évaluation :**
  - Il est **utile** pour absorber les rafales de double-clic avant même d'atteindre la couche JPA.
  - Il est **redondant** par rapport à l'index unique SQL pour les requêtes possédant une `Idempotency-Key`.
  - Il est **local à chaque instance** : il ne protège pas contre un double clic dont les deux requêtes seraient réparties sur deux pods différents (dans ce cas, c'est l'index SQL qui tranche).

#### 5. Comportement du Frontend lors d'un Réessai
- **Preuve dans le code :** [demande-devis.component.ts](file:///frontend/src/app/features/services/demande-devis/demande-devis.component.ts#L200)
  ```typescript
  idempotencyKey: crypto.randomUUID()
  ```
- **Constat :** À chaque clic sur « Envoyer la demande », un nouvel UUID est généré dans l'objet `request`.
- **Conséquence :** Si une requête échoue suite à un timeout réseau et que l'utilisateur clique à nouveau, une nouvelle clé est envoyée. L'anti-doublon repose alors sur le cache anti-rebond serveur de 30 secondes (qui compare le contenu `email + phone + produits`).
- **Amélioration recommandée :** Conserver la clé d'idempotence dans le formulaire ou le signal du composant tant que la soumission n'a pas réussi, et ne la renouveler qu'après confirmation de création ou réinitialisation du formulaire.

---

### 2.4. Axe 4 — Validation et État Réel des Tests

#### 1. Validations Testées avec Succès
- **Téléphone :**
  - Validés avec succès : `0612345678`, `0700123456`, `0522123456`, `+212612345678`, `+212 6 12 34 56 78`, `+212 522-123456`, `+33 1 42 68 00 00`, `+1 (555) 123-4567`.
  - Rejetés avec succès : `abc-non-tel`, `phone123456`, `1234`, `06-abc-1234`, `++212612345678`.
- **Quantités :** Plafond à 9999 unités testé et validé.
- **Sécurité OIDC / IDOR :**
  - Rejet des comptes non vérifiés sur devis invité : testé et validé.
  - Rattachement automatique d'un compte vérifié : testé et validé.
  - Rejet d'un compte accédant au devis d'un tiers (IDOR) : testé et validé.

#### 2. Scénarios Non Couverts par des Tests Automatisés (Vérification de Preuve)
En inspectant les fichiers de test du projet, les points suivants **ne disposent pas de tests unitaires ou d'intégration automatisés** :
1. **Test multi-threadé de concurrence pure :** Aucun test n'utilise `ExecutorService`, `CountDownLatch` ou `CompletableFuture` pour soumettre 50 requêtes simultanées sur le token bucket ou sur l'index d'idempotence.
2. **Test de falsification d'IP valide :** Aucun test ne vérifie le comportement lorsque le client transmet un faux `X-Forwarded-For` valide (le test existant teste uniquement une chaîne d'injection XSS `<script>`).
3. **Test d'indisponibilité ou d'éviction de cache :** Aucun test ne simule une saturation mémoire de Caffeine ou une panne de config.

---

## 3. Tableau Récapitulatif des Constats

| Identifiant | Sévérité | Domaine | Constat | Impact |
|---|---|---|---|---|
| **OBS-01** | **P2 (Conditionnel)** | `api-gateway` | `X-Forwarded-For` lu sans vérification de chaîne de proxy de confiance | Contournement possible du rate limit si la gateway est exposée directement à Internet sans WAF/reverse-proxy de tête. |
| **OBS-02** | **P2 (Moyen)** | `quotation-service` | Réutilisation d'une `Idempotency-Key` sans vérification du propriétaire | Risque de collision ou de divulgation de devis si une clé identique est réémise avec un autre email. |
| **OBS-03** | **P3 (Mineur)** | `frontend` | Génération d'un nouvel UUID d'idempotence à chaque appel de soumission | Sur retry manuel après coupure réseau, la clé change (l'anti-doublon dépend alors du cache fingerprint serveur). |
| **OBS-04** | **Info** | Architecture | Limite Caffeine locale à chaque instance JVM | En cluster multi-instances, la capacité globale équivaut à $N \times \text{seuil}$. |
| **OBS-05** | **Info** | Tests | Absence de tests automatisés multi-threads sous concurrence | La robustesse concurrentielle repose sur les garanties formelles du moteur JVM (`synchronized`) et de PostgreSQL. |

---

## 4. Recommandations et Plan d'Action avant Production Publique

Avant l'ouverture complète du service sur un domaine Internet public :

1. **Sécurisation de la chaîne de confiance proxy (Résolution OBS-01) :**
   - Configurer le serveur Web frontal (Nginx, Traefik, Cloudflare ou AWS Ingress) avec la directive écrasant `X-Forwarded-For` :
     ```nginx
     proxy_set_header X-Forwarded-For $remote_addr;
     ```
   - Ou activer dans `api-gateway` :
     ```yaml
     server:
       forward-headers-strategy: framework
     ```
2. **Contrôle du propriétaire de la clé d'idempotence (Résolution OBS-02) :**
   - Dans `QuotationApplicationService.java`, lors de la détection d'un devis existant par clé d'idempotence, vérifier :
     ```java
     if (existing.isPresent()) {
         QuotationEntity q = existing.get();
         if (!q.getEmail().equalsIgnoreCase(request.email())) {
             throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency key already used for another quotation");
         }
         return mapper.toResponse(q);
     }
     ```
3. **Persistance de la clé côté frontend (Résolution OBS-03) :**
   - Stocker la clé d'idempotence dans l'état du composant lors de la première tentative et ne la régénérer qu'après un reset complet ou une création confirmée.

---

## 5. Conclusion Finale

Les corrections de sécurité apportées ont comblé avec succès les failles de confidentialité et de format identifiées lors de l'audit initial. L'application est **prête pour les tests de recette et de pré-production**. La mise en production sur Internet public est conditionnée à la présence d'un reverse-proxy assainissant les adresses IP clientes.


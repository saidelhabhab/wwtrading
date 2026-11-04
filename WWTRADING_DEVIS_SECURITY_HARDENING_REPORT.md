# Rapport de Durcissement de Sécurité : Parcours /devis (WWTRADING)

> **Document de référence :** Rapport d'Implémentation et de Durcissement de Sécurité  
> **Date de réalisation :** 27 Octobre 2026  
> **Statut de l'intervention :** TERMINÉE AVEC SUCCÈS — TOUS LES TESTS SONT AU VERT  
> **Intégrité des données :** AUCUN VOLUME, AUCUNE BASE DE DONNÉES ET AUCUN CONTENEUR N'ONT ÉTÉ ALTÉRÉS OU SUPPRIMÉS  

---

## 1. Synthèse des Réalisations

Conformément aux exigences et aux constats identifiés dans le rapport d'audit `WWTRADING_DEVIS_SECURITY_AUDIT.md`, les quatre vulnérabilités et risques de sécurité ont été résolus et vérifiés par des tests automatisés :

| Réf. Audit | Nature du Risque | Composant | Solution Implémentée | Statut |
|---|---|---|---|---|
| **SEC-01** | Absence de Rate Limiting sur création publique de devis | `api-gateway` | Filtre global `PublicQuotationRateLimitFilter` avec algorithme Token-Bucket sous Caffeine par IP client, réponse HTTP 429 avec `Retry-After` et Problem JSON | **Corrigé & Validé** |
| **SEC-02** | Risque d'usurpation de devis invité par email non vérifié | `quotation-service` | Exigence stricte de la claim JWT `email_verified == true` pour toute consultation ou réclamation de devis invité ; isolation IDOR stricte par `customerSubject` | **Corrigé & Validé** |
| **SEC-03** | Format permissif du numéro de téléphone | `quotation-service` | Validation stricte `@Pattern` couvrant les numéros marocains (`05...`, `06...`, `07...`, `+212...`) et internationaux standards | **Corrigé & Validé** |
| **SEC-04** | Quantités d'équipements non bornées | `quotation-service` | Ajout de l'annotation `@Max(9999)` sur les quantités demandées au niveau du devis et des lignes produits | **Corrigé & Validé** |
| **SEC-IDEMP**| Risque de doubles soumissions et replays réseau | `quotation-service` & Frontend | Gestion de l'en-tête `Idempotency-Key` (persistance indexée Flyway V7) couplée à un cache mémoire anti-rebond rapide de 30 secondes | **Corrigé & Validé** |

---

## 2. Détail Technique des Implémentations

### 2.1. Phase 1 — Rate Limiting (`api-gateway`)

- **Fichier implémenté :** `ma.wwtrading.gateway.security.PublicQuotationRateLimitFilter`
- **Configuration :**
  - Seuil par défaut : `10 requêtes / minute` par adresse IP (configurable via `wwtrading.gateway.rate-limit.quotations.capacity` et `refill-seconds`).
  - Périmètre strict : applique la limitation exclusivement aux requêtes `POST /api/quotations` (les routes de consultation, le catalogue et les autres services ne sont pas impactés).
  - Algorithme : **Token Bucket** haute performance, thread-safe, alimenté par le cache in-memory `Caffeine` (avec expiration automatique des IP inactives après 15 minutes).
- **Résolution sécurisée de l'adresse IP :**
  - Extraction du premier jeton de l'en-tête `X-Forwarded-For` (en cas de reverse-proxy de tête comme Cloudflare ou Nginx).
  - Validation stricte de conformité IPv4 (`Pattern`) ou IPv6.
  - Repli automatique sur `request.getRemoteAddress()` si l'en-tête est absent ou forgé avec des caractères illicites.
  - Repli ultime sur `"unknown"`.
- **Réponse HTTP en cas de dépassement :**
  - Code retour : `429 Too Many Requests`.
  - En-tête standard : `Retry-After: <secondes>`.
  - Type de contenu : `application/problem+json;charset=UTF-8`.
  - Corps normalisé RFC 7807 :
    ```json
    {
      "type": "https://wwtrading.ma/errors/rate-limit-exceeded",
      "title": "Too Many Requests",
      "status": 429,
      "detail": "Limite de création de devis atteinte. Veuillez patienter avant de renouveler votre demande.",
      "retryAfterSeconds": 24
    }
    ```
- **Autorisation Gateway :**
  - Ajout de `/api/quotations` dans `SecurityConfig.java` (`.pathMatchers(HttpMethod.POST, "/api/quotations").permitAll()`) afin de permettre la traversée des prospects invités sous la surveillance du filtre de débit.

---

### 2.2. Phase 2 — Protection Anti-IDOR & Revendication Sécurisée des Devis

- **Fichier modifié :** `ma.wwtrading.quotations.application.QuotationApplicationService`
- **Règles d'isolation et d'accès :**
  1. **Rôles internes (`ADMIN`, `MANAGER`, `COMMERCIAL`) :** Accès de gestion conservé sur l'intégralité des devis.
  2. **Devis créés par un client authentifié (`customerSubject != null`) :** L'accès est strictement réservé à l'utilisateur dont le `sub` du JWT correspond à `customerSubject`. Tout autre utilisateur (même avec le même nom ou email) est rejeté avec `403 Forbidden`.
  3. **Devis créés en mode invité (`customerSubject == null`) :**
     - Un utilisateur connecté dont le compte Keycloak n'a pas validé son email (`email_verified != true`) est **systématiquement rejeté (`403 Forbidden`)**.
     - Seul un utilisateur dont l'adresse email est explicitement vérifiée dans le JWT (`email_verified == true`) et correspond à l'email du devis peut consulter le devis. Dès cette première consultation légitime, le devis est automatiquement et définitivement rattaché à son compte (`customerSubject = jwtSubject`).
  4. **Parcours `getMyQuotations` :**
     - Les comptes non vérifiés ne peuvent lister que les devis qu'ils ont explicitement créés avec leur propre identifiant.
     - Les comptes avec email vérifié rattachent automatiquement leurs demandes antérieures déposées en mode invité.

---

### 2.3. Phase 3 — Validation Renforcée du Téléphone et des Quantités

- **Fichier modifié :** `ma.wwtrading.quotations.api.CreateQuotationRequest`
- **Validation du téléphone :**
  - Annotation : `@Pattern(regexp = "^(\\+?[0-9]{1,4}[\\s.-]?)?(\\(?[0-9]{1,4}\\)?[\\s.-]?)?[0-9\\s.-]{6,20}$")`
  - Validité confirmée sur les formats marocains :
    - Mobile : `0612345678`, `0700123456`, `+212612345678`, `+212 6 12 34 56 78`
    - Fixe : `0522123456`, `+212 522-123456`
  - Validité confirmée sur les formats internationaux :
    - France : `+33 1 42 68 00 00`
    - USA/Canada : `+1 (555) 123-4567`
  - Rejet garanti des entrées invalides : `abc-non-tel`, `phone123456`, `1234` (trop court), chaînes contenant des lettres ou des injections.
- **Plafond sur les quantités :**
  - `@Min(1) @Max(9999)` appliqué sur `quantity` au niveau global et sur chaque ligne produit `RequestedLine.quantity`.

---

### 2.4. Phase 4 — Idempotence et Anti-Doublons Serveur

- **Migration de base de données :** `V7__add_idempotency_key.sql`
  ```sql
  ALTER TABLE quotation ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(128);
  CREATE UNIQUE INDEX IF NOT EXISTS idx_quotation_idempotency_key ON quotation (idempotency_key) WHERE idempotency_key IS NOT NULL;
  ```
- **Prise en charge de l'en-tête HTTP :**
  - `QuotationController` extrait l'en-tête optionnel `@RequestHeader("Idempotency-Key")`.
  - `QuotationApplicationService` recherche si un devis existe déjà avec cette clé unique : si oui, il renvoie immédiatement la ressource existante sans duplication en base ni publication d'événement Kafka supplémentaire.
- **Anti-rebond rapide en mémoire (30 secondes) :**
  - Un fingerprint normalisé de la requête (`email + phone + serviceType + lignes produits triées`) est mis en mémoire vive.
  - Tout double clic ultra-rapide ou retransmission de paquet TCP dans la fenêtre de 30 secondes renvoie le devis déjà créé sans créer de doublon.
- **Alignement Frontend :**
  - `QuotationApiService.create` transmet l'en-tête `Idempotency-Key`.
  - `DemandeDevisComponent` génère un UUID d'idempotence à la soumission et prend en charge le code HTTP 429 avec message utilisateur explicite.

---

## 3. Résultats des Tests Automatisés

### 3.1. Tests de la Passerelle (`api-gateway`)
Commande exécutée :
```powershell
mvn -f backend/services/api-gateway/pom.xml test -Dtest=PublicQuotationRateLimitFilterTests,SecurityHeadersFilterTests,CorrelationIdFilterTests
```
**Résultat : 8 / 8 tests réussis (100 %)**
- `PublicQuotationRateLimitFilterTests` (5 tests) :
  - `allowsRequestsBelowCapacity` : ✅ Succès
  - `rejectsExcessiveRequestsWith429` : ✅ Succès (statut 429, Retry-After présent, RFC 7807 Problem JSON validé)
  - `isolatesDifferentClientIps` : ✅ Succès (isolation étanche entre adresses IP distinctes)
  - `bypassesOtherPathsAndMethods` : ✅ Succès (`GET`, `/api/orders`, etc. exemptés)
  - `resolvesClientIpSafely` : ✅ Succès (`X-Forwarded-For` multi-proxy, repli anti-injection)
- `CorrelationIdFilterTests` (2 tests) : ✅ Succès
- `SecurityHeadersFilterTests` (1 test) : ✅ Succès

### 3.2. Tests du Service Devis (`quotation-service`)
Commande exécutée :
```powershell
mvn -f backend/services/quotation-service/pom.xml test
```
**Résultat : 46 / 46 tests réussis (100 %)**
- `QuotationApplicationServiceTests` (18 tests) :
  - `createsCatalogQuotationForGuest` : ✅ Succès
  - `createsCatalogQuotationForCommercialOnBehalfOfCustomer` : ✅ Succès
  - `createsCatalogQuotationForAdmin` : ✅ Succès
  - `createsCustomQuotationWithDescription` : ✅ Succès
  - `returnsExistingQuotationByIdempotencyKey` : ✅ Succès (Idempotency-Key vérifié)
  - `rapidDuplicateSubmissionReturnsExisting` : ✅ Succès (Anti-rebond 30s validé)
  - `unverifiedEmailUserCannotAccessGuestQuotation` : ✅ Succès (Rejet SEC-02 validé)
  - `verifiedEmailUserCanAccessAndLinkGuestQuotation` : ✅ Succès (Rattachement légitime validé)
  - `clientCannotAccessAnotherClientsQuotation` : ✅ Succès (Isolation IDOR stricte validée)
  - `staffCanAccessAnyQuotation` : ✅ Succès
  - Transition et annulation d'états : ✅ Succès
- `QuotationMapperValidationTests` (16 tests) :
  - Formats téléphoniques marocains et internationaux valides : ✅ 8 variantes testées avec succès
  - Rejet des numéros invalides (trop courts, lettres, syntaxe) : ✅ 5 variantes rejetées
  - Rejet des quantités > 9999 unités : ✅ Validé
- `QuotationControllerTests` (6 tests) : ✅ 6/6 réussis (validation HTTP, 201 Created, en-tête Idempotency-Key)
- `QuotationSecurityOpenApiTests` (5 tests) : ✅ 5/5 réussis (permissions Spring Security)
- `QuotationEventSerializationTests` (1 test) : ✅ Succès

### 3.3. Compilation et Bundle Frontend
Commande exécutée :
```powershell
npm --prefix frontend run build
```
**Résultat : Exit code 0 (Succès complet)**
- Compilation Angular 19 sans aucune erreur TypeScript ou SCSS.
- Génération complète des bundles (`dist/wwtrading`).

---

## 4. Vérification des Règles Impératives et d'Intégrité

- **Préservation des données et bases :** Aucune base de données ni volume n'a été réinitialisé ou supprimé.
- **Règles métier :** Les parcours de paiement (`BANK_TRANSFER`, `SPLIT_50_50`), frais de livraison et retraits restent strictement intacts.
- **Spring Security :** Aucune configuration de sécurité n'a été affaiblie ; le contrôle des rôles et des jetons JWT a été renforcé.
- **Endpoints internes :** Les routes de gestion administrative, d'annulation ou de mise à jour des devis demeurent strictement protégées par authentification et rôle.


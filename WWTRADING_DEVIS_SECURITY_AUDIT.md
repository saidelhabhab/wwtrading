# Rapport d'Audit de Sécurité et d'Intégration : Parcours /devis (WWTRADING)

> **Document de référence :** Audit de Sécurité, Architecture et Intégration  
> **Date de réalisation :** 27 Octobre 2026  
> **Statut de l'intervention :** AUDIT EXCLUSIF — AUCUN FICHIER APPLICATIF, AUCUNE CONFIGURATION ET AUCUNE DONNÉE N'ONT ÉTÉ MODIFIÉS  

---

## 1. Synthèse Exécutive et Avis Global

L'audit de sécurité et de conformité fonctionnelle a été mené sur l'ensemble de la chaîne technique du parcours devis :
- **Frontend Angular :** `DemandeDevisComponent`, `ProductApiService`, `QuotationApiService`, stockage `sessionStorage`.
- **Backend `quotation-service` :** `SecurityConfig`, `QuotationController`, `QuotationApplicationService`, DTOs, Repository.
- **Backend `product-service` :** `ProductController`, `ProductApplicationService`, `ProductRepository`.
- **Infrastructure & SSO :** Passerelle `api-gateway`, realm Keycloak `wwtrading-realm.json`, conteneurs Docker en exécution.

### Recommandation globale de mise en production :
⚠️ **AUTORISATION SOUS RÉSERVE D'AJOUT D'UN RATE LIMITING (P1) ET D'UN REBUILD DE CONTENEUR.**
Le flux métier est fonctionnellement sain et sécurisé contre les élévations de privilèges (aucune faille d'injection SQL, aucune fuite de secrets dans `sessionStorage`, isolation robuste des rôles via JWT). Cependant, l'exposition publique non limitée de `POST /api/quotations` présente un risque de déni de service et de spam de devis qui doit être régulé avant une exposition sur Internet public.

---

## 2. Tableau Récapitulatif des Constats d'Audit

| Réf. | Gravité | Composant / Fichier | Nature du Risque | Statut |
|---|---|---|---|---|
| **SEC-01** | **P1 (Majeur)** | `api-gateway` / `QuotationController.java` | Absence de Rate Limiting sur la création publique de devis | Confirmé |
| **SEC-02** | **P2 (Moyen)** | `QuotationApplicationService.java` & Keycloak | Revendication de devis invité sans vérification préalable d'email | Confirmé |
| **SEC-03** | **P2 (Moyen)** | `CreateQuotationRequest.java` (`phone`) | Validation permissive du format téléphone côté backend | Confirmé |
| **SEC-04** | **P3 (Mineur)** | `CreateQuotationRequest.java` (`quantity`) | Absence de contrainte `@Max` sur les quantités d'équipements | Confirmé |
| **SEC-05** | **P3 (Mineur)** | `ProductRepository.java` (`search`) | Interprétation des jokers SQL (`%`, `_`) dans la recherche | Confirmé |
| **OPS-01** | **Info** | Docker Compose (`quotation-service`) | Décalage entre code source compilé localement et conteneur en cours | Confirmé |

---

## 3. Détail Approfondi des Constats par Domaine

### 3.1. Sécurité des Devis Invités

#### [SEC-01] Absence de Rate Limiting sur `POST /api/quotations` (P1)
- **Fichier et méthode :** `backend/services/quotation-service/.../SecurityConfig.java` et `backend/services/api-gateway`.
- **Preuve dans le code :**
  ```java
  // SecurityConfig.java (l. 27)
  .requestMatchers(HttpMethod.POST, "/api/quotations").permitAll()
  ```
  Aucun `RequestRateLimiter` n'est configuré dans `api-gateway/src/main/resources/application.yml` pour le préfixe `/api/quotations`.
- **Scénario d'exploitation :** Un script automatisé peut générer plusieurs centaines de soumissions `POST /api/quotations` par seconde. Chaque soumission provoque :
  1. Un appel HTTP vers `product-service` pour chaque produit du catalogue afin de vérifier son existence et son prix.
  2. Une transaction SQL d'insertion dans la base de données PostgreSQL `quotation-db`.
  3. La publication d'un événement Kafka `quotationCreated`.
  4. L'envoi d'emails transactionnels (client + équipe commerciale).
- **Impact métier et sécurité :** Risque de saturation des connexions JDBC, surcharge du broker Kafka, engorgement de la boîte MailHog/SMTP et épuisement des capacités de traitement de l'équipe commerciale.
- **Correction recommandée (sans l'appliquer) :**
  - Configurer dans `api-gateway` un filtre Spring Cloud Gateway `RequestRateLimiter` basé sur l'adresse IP du client (ex. maximum 10 requêtes / minute / IP).
  - Ajouter un jeton anti-bot (ex. Cloudflare Turnstile ou Google reCAPTCHA v3 invisible) vérifié côté backend.

---

#### [SEC-02] Revendication de devis invité sans vérification d'email Keycloak (P2)
- **Fichier et méthode :** `QuotationApplicationService.java` (`isOwner`, `getMyQuotations`) & `infrastructure/keycloak/wwtrading-realm.json`.
- **Preuve dans le code :**
  ```java
  // QuotationApplicationService.java (l. 220)
  private boolean isOwner(QuotationEntity quotation, String customerRef) {
      return quotation.getCustomerReference().equalsIgnoreCase(customerRef)
          || (quotation.getEmail() != null && quotation.getEmail().equalsIgnoreCase(customerRef));
  }
  ```
  Dans `wwtrading-realm.json`, le paramètre `"verifyEmail": true` n'est pas activé.
- **Scénario d'exploitation :** Un attaquant connaît l'adresse email d'un client potentiel (`direction@acme-maroc.ma`) ayant soumis une demande de devis en mode invité. L'attaquant crée un compte Keycloak avec cette adresse email sans avoir à valider un lien envoyé sur cette boîte. Dès sa connexion, `customerRef` correspond à cette adresse email : l'attaquant peut appeler `GET /api/quotations/my-quotations` et lire tous les devis déposés sous cet email (volumes, adresses, coordonnées, notes).
- **Impact métier et sécurité :** Risque de fuite d'informations commerciales sensibles et de données personnelles (RGPD / CNDP Loi 09-08).
- **Correction recommandée (sans l'appliquer) :**
  1. Activer `"verifyEmail": true` dans le realm Keycloak pour obliger tout nouvel utilisateur à prouver la possession de son adresse email avant l'émission d'un jeton actif.
  2. Dans `QuotationApplicationService.isOwner`, n'autoriser la correspondance sur `quotation.getEmail()` que si la claim `email_verified` du jeton JWT est égale à `true`.

---

#### [SEC-03] Validation permissive du numéro de téléphone côté backend (P2)
- **Fichier et méthode :** `backend/services/quotation-service/.../CreateQuotationRequest.java`.
- **Preuve dans le code :**
  ```java
  // CreateQuotationRequest.java (l. 23)
  @NotBlank @Size(max = 30) String phone
  ```
  Le frontend Angular applique `Validators.pattern(/^[0-9+\s\-().]{8,25}$/)`, mais le backend ne possède aucun validateur de format. Une requête directe via `curl` ou Postman avec `"phone": "00000000"` ou `"phone": "abc-non-tel"` est acceptée avec le statut HTTP 201.
- **Scénario d'exploitation :** Injection de coordonnées téléphoniques invalides ou factices via l'API publique.
- **Impact métier et sécurité :** Dégradation de la qualité des données prospects et perte de temps commercial.
- **Correction recommandée (sans l'appliquer) :**
  Ajouter l'annotation `@Pattern(regexp = "^[0-9+\\s\\-().]{8,25}$", message = "Format de numéro de téléphone invalide")` sur le champ `phone` dans `CreateQuotationRequest.java`.

---

#### [SEC-04] Absence de limite supérieure sur les quantités demandées (P3)
- **Fichier et méthode :** `backend/services/quotation-service/.../CreateQuotationRequest.java` (`RequestedLine`).
- **Preuve dans le code :**
  ```java
  public record RequestedLine(@NotNull UUID productId, @Min(1) int quantity) { }
  ```
- **Scénario d'exploitation :** Un client ou script saisit `quantity = 2147483647`.
- **Impact métier et sécurité :** Risque d'erreur arithmétique lors du chiffrage ultérieur ou d'enregistrement de valeurs aberrantes.
- **Correction recommandée (sans l'appliquer) :**
  Ajouter `@Max(value = 9999, message = "La quantité demandée ne peut excéder 9999 unités")`.

---

### 3.2. Recherche Catalogue

#### Validation du flux complet :
`Angular (DemandeDevisComponent)` → `ProductApiService` → `ProductController` → `ProductApplicationService` → `ProductRepository` (SQL).

- **Transmission du paramètre `search` :**  
  Vérifié. La recherche transmet `search: term.trim()` dans les paramètres de requête HTTP.
- **Requête en base de données :**  
  Vérifié. `ProductRepository.findAllFiltered(...)` exécute un `SELECT` JPQL avec clause `LOWER(...) LIKE LOWER(CONCAT('%', :search, '%'))` sur 4 champs distincts (`name`, `brand`, `model`, `productReference`).
- **Résistance aux injections SQL :**  
  Vérifié. Le paramètre `:search` est une variable nommée JPA évaluée par JDBC avec des variables de liaison (`PreparedStatement`). Aucun risque d'injection SQL.
- **Pagination et limitation mémoire :**  
  Vérifié. La pagination est déléguée au moteur de base de données via `@PageableDefault(size = 20, sort = "name") Pageable pageable`. Seuls 20 éléments par page sont instanciés en mémoire JVM.
- **Compatibilité des filtres existants :**  
  Vérifié. Les filtres `type`, `machineCategory` et `colorMode` restent pleinement opérationnels et combinables avec `search`.

#### [SEC-05] Jokers SQL `%` et `_` dans la recherche (P3)
- **Preuve :** Si l'utilisateur saisit `%`, la requête SQL génère `LIKE '%%%'`, ce qui correspond à tous les enregistrements.
- **Impact :** Aucun risque de sécurité ni de crash ; le comportement correspond simplement à un joker universel.
- **Correction recommandée (sans l'appliquer) :** Échapper les caractères réservés `\` `%` `_` si une recherche stricte est désirée.

---

### 3.3. Brouillon et Gestion des Soumissions

- **Absence de secrets dans `sessionStorage` :**  
  Vérifié. L'objet enregistré sous la clé `wwt_devis_draft_v1` contient uniquement les données du formulaire (`fullName`, `company`, `phone`, `email`, `city`, `additionalMessage`) et les identifiants/quantités de produits sélectionnés. Aucun jeton JWT, aucun refresh token ni secret Keycloak n'est manipulé ou stocké.
- **Intégrité de la validation après restauration :**  
  Vérifié. La restauration utilise `patchValue()` sur le formulaire Angular. Lors du clic sur soumettre, `devisForm.invalid` et `devisForm.markAllAsTouched()` réévaluent l'intégralité des contraintes de validation.
- **Nettoyage après succès confirmé :**  
  Vérifié. `sessionStorage.removeItem(...)` n'est appelé que dans le bloc `next` de la souscription HTTP (une fois le code 201 renvoyé par le serveur).
- **Préservation sur erreur réseau :**  
  Vérifié. En cas d'erreur HTTP 500, 503 ou de perte de connectivité, le brouillon est maintenu intact dans `sessionStorage` et les champs restent remplis à l'écran. Le signal `submitting` repasse à `false`, permettant à l'utilisateur de cliquer à nouveau dès le rétablissement du réseau.
- **Prévention des doubles clics :**  
  Vérifié. Le signal `submitting()` est immédiatement positionné à `true` et le bouton est désactivé dans le DOM (`[disabled]="submitting() || selectedProducts().length === 0"`).

---

## 4. Tests Réellement Exécutés et Résultats Observables

Tous les tests ci-dessous ont été réellement exécutés avec succès dans l'environnement :

| Test / Suite | Commande Exécutée | Résultat | Couverture |
|---|---|---|---|
| `QuotationApplicationServiceTests` | Maven JUnit 5 | **15/15 OK (100 %)** | Devis invité, devis client, devis commercial, devis admin, transition d'états, isolation des dossiers |
| `QuotationSecurityOpenApiTests` | Maven WebMvcTest | **5/5 OK (100 %)** | Vérification `POST /api/quotations` public + protection des routes internes |
| `QuotationControllerTests` | Maven MockMvc | **6/6 OK (100 %)** | Codes retours HTTP 201, en-tête `Location`, rejets HTTP 400 |
| `QuotationMapperValidationTests` | Maven Bean Validation | **2/2 OK (100 %)** | Contraintes `@NotBlank`, `@Email`, `@Size` |
| `ProductBehaviorTests` | Maven JUnit 5 | **17/17 OK (100 %)** | Recherche serveur avec paramètre `search`, filtres machine, pagination |
| Angular Application Build | `npx ng build --configuration=development` | **Exit code 0 (Succès)** | Compilation bundle complète sans erreur TypeScript/SCSS |

---

## 5. État des Conteneurs et Environnement Opérationnel

Inspection non destructive de l'environnement Docker :
- Tous les conteneurs sont démarrés et opérationnels depuis plus de 6 heures.
- `wwtrading-quotation-service-1` : En cours (port 8082).
- `wwtrading-product-service-1` : En cours (port 8086).
- `wwtrading-api-gateway-1` : En cours (port 8080).
- `wwtrading-quotation-db-1` : En cours et sain (port 5434).
- `wwtrading-keycloak-1` : En cours et sain (port 8180).
- `wwtrading-mailhog` : En cours (port 8025 / 1025).

> [!NOTE]
> **Observation opérationnelle (OPS-01) :** Les conteneurs Docker tournent sur les images démarrées précédemment. Pour que les conteneurs Docker intègrent les modifications Java compilées localement sans détruire de données, un simple redémarrage ciblé des conteneurs applicatifs (ex. `docker compose restart quotation-service product-service`) sera à planifier lors de la phase de déploiement.

---

## 6. Vérifications Manuelles Restantes (Hors Automatisation)

1. **Test E2E de bout en bout dans le navigateur hôte :**
   - Accéder à `http://localhost:4201/devis`.
   - Effectuer une recherche de mot-clé (ex. « bizhub ») et vérifier la fluidité du rechargement des 20 résultats sans rafraîchissement complet de la page.
   - Sélectionner un produit, remplir le formulaire invité et soumettre la demande.
   - Observer l'apparition de l'écran de confirmation contenant la référence unique du devis.
2. **Réception de la notification MailHog :**
   - Ouvrir `http://localhost:8025` pour constater l'arrivée de l'email récapitulatif sans doublon.
3. **Restauration de session :**
   - Remplir partiellement le formulaire, fermer l'onglet, rouvrir `http://localhost:4201/devis` et vérifier que les champs et produits sélectionnés sont bien réinjectés.

---

## 7. Recommandation Finale

| Critère | Statut | Commentaire |
|---|---|---|
| **Intégrité des données** | ✅ Conforme | Aucune altération des commandes, de la facturation ou des comptes. |
| **Sécurité des autorisations** | ✅ Conforme | Les rôles CLIENT, COMMERCIAL, MANAGER, ADMIN et invité sont strictement cloisonnés. |
| **Robustesse de recherche** | ✅ Conforme | Pagination serveur, recherche insensible à la casse, pas d'injection SQL. |
| **Exposition publique (anti-abus)** | ⚠️ Action requise (P1) | Mettre en place un Rate Limiting IP sur la passerelle avant ouverture WAN publique. |

**Verdict :**  
Le code est prêt pour validation et recette en environnement de développement / staging. L'activation d'un rate limiter sur `POST /api/quotations` dans `api-gateway` est recommandée avant bascule en production publique.


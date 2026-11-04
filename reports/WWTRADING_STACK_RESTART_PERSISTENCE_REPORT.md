# WWTRADING — Rapport de Redémarrage Sécurisé & Validation de la Persistance des Données

**Date :** 4 novembre 2026  
**Projet :** `C:\Users\hp\Documents\Codex\wwtriding`  
**Configuration Compose :** `compose.yaml` (Projet officiel : `wwtrading`)  
**Méthode de redémarrage :** `docker compose down` (strictement SANS `-v`) puis `docker compose up -d`  
**Sauvegarde de référence :** `backups/docker-export-20261103-134923/` (SHA-256 certifié, 64.62 Mo)

---

## 1. Synthèse Exécutive

Le redémarrage complet de la stack Docker WWTRADING a été exécuté en toute sécurité sans l'option destructive `-v`.

La persistance des données a été validée à **100%** :
- Les **15 volumes Docker persistants** (`wwtest_*`) sont restés intacts et ont été automatiquement rattachés lors du redémarrage.
- L'intégralité des **10 bases de données PostgreSQL** a conservé ses tables et ses données.
- Les **5 produits** de la base `wwtrading_products` (les 4 machines Konica Minolta importées + le produit d'origine `ascbc`) sont immédiatement disponibles.
- L'ensemble des services (`API Gateway`, `Keycloak`, `Eureka`, `Config Server`, `Kafka`, `MailHog` et microservices) est opérationnel et interconnecté.

---

## 2. Étape 1 — Inspection Pré-Redémarrage

Avant toute action d'arrêt :
- **Volumes Externes :** Vérification dans `compose.yaml` que les 15 volumes de données sont explicitement déclarés en `external: true` sous leur nom physique Docker `wwtest_*` (ex. `wwtest_product-data`, `wwtest_user-data`, etc.).
- **Montage du Produit :** Confirmation que le conteneur `wwtrading-product-db-1` est monté sur le volume persistant `wwtest_product-data -> /var/lib/postgresql/data`.
- **Sauvegarde Préservée :** Confirmation de la présence intacte de l'export de sauvegarde `backups/docker-export-20261103-134923/` (28 fichiers).

---

## 3. Étape 2 & 3 — Arrêt Sécurisé et Redémarrage

1. **Arrêt Propre :**
   ```powershell
   docker compose down
   ```
   *Note : L'option `-v` n'a pas été utilisée. Les conteneurs et le réseau bridge ont été arrêtés et retirés proprement, sans toucher aux volumes de données.*
2. **Vérification Intermédiaire :** La commande `docker volume ls --filter "name=wwtest"` a confirmé que les 15 volumes physiques sont demeurés intacts.
3. **Redémarrage :**
   ```powershell
   docker compose up -d
   ```
   *Tous les réseaux, conteneurs et liaisons de volumes externes ont été recréés sans aucune erreur (Code de sortie 0).*

---

## 4. Étape 4 — Vérification de la Persistance dans `wwtrading_products`

### A. Comptages des Tables
Une connexion SQL directe à la base `wwtrading_products` via `wwtrading-product-db-1` a confirmé l'exactitude des volumétries attendues :

| Table | Nombre de Lignes | Statut de Persistance |
| :--- | :---: | :--- |
| **`product`** | **5** | 100% Préservé |
| **`product_image`** | **17** | 100% Préservé |
| **`stock`** | **5** | 100% Préservé |
| **`stock_movement`** | **5** | 100% Préservé |

### B. Détail des 5 Produits Présents

| Référence | Nom du Produit | Prix Unitaire | Marque & Modèle | Statut |
| :--- | :--- | :---: | :--- | :---: |
| **KM-ACCURIOPRESS-C3070** | Konica Minolta AccurioPress C3070 | 70 000.00 DH | Konica Minolta / AccurioPress C3070 | Actif |
| **KM-ACCURIOPRESS-C6000** | Konica Minolta AccurioPress C6000 | 45 000.00 DH | Konica Minolta / AccurioPress C6000 | Actif |
| **KM-BIZHUB-450I** | Konica Minolta bizhub 450i | 12 000.00 DH | Konica Minolta / bizhub 450i | Actif |
| **KM-BIZHUB-650I** | Konica Minolta bizhub 650i | 12 000.00 DH | Konica Minolta / bizhub 650i | Actif |
| **ascbc** | test 1 | 11 000.00 DH | Konica Minolta / bizhub 245 | Actif |

### C. Intégrité Relationnelle (Clés Étrangères)
- Images orphelines : **0**
- Stocks orphelins : **0**
- Mouvements orphelins : **0**

---

## 5. Étape 5 — Test de l'API Gateway (Port 8080)

Appel de validation de bout en bout :
```http
GET http://localhost:8080/api/products
```

- **Statut HTTP :** `200 OK`
- **`totalElements` :** `5`
- **Détails :** Les 5 produits sont tous retournés avec leurs données de prix, références, catégories, stocks associés (quantité disponible: 10) et leurs images respectives.

---

## 6. Persistance des Autres Données Métier

- **Utilisateurs (`wwtrading_users`) :** Les 2 comptes clients (`hamza karout`, `raja fink`) sont conservés.
- **Commandes (`wwtrading_orders`) :** Les 3 commandes avec méthode de paiement `SPLIT_50_50` sont intactes.
- **Keycloak IAM (`keycloak`) :** Realm `wwtrading` et 5 comptes utilisateurs préservés.

---

## 7. État de Santé Global des Conteneurs

| Composant | Conteneur | Port(s) Hôte | État |
| :--- | :--- | :--- | :--- |
| **API Gateway** | `wwtrading-api-gateway-1` | `8080:8080` | `Up` (`status: UP`) |
| **Keycloak IAM** | `wwtrading-keycloak-1` | `8180:8080` | `Up (healthy)` |
| **Discovery Server** | `wwtrading-discovery-server-1` | `8761:8761` | `Up` (`status: UP`) |
| **Config Server** | `wwtrading-config-server-1` | `8888:8888` | `Up (healthy)` |
| **Kafka Broker** | `wwtrading-kafka-1` | `127.0.0.1:29092` | `Up (healthy)` |
| **MailHog** | `wwtrading-mailhog` | `1025`, `8025` | `Up` |
| **10 Bases PostgreSQL** | `wwtrading-*-db-1` | `5433` à `5441`, `5432` | `Up (healthy)` (10/10) |
| **Microservices Métier** | 9 services métier | `8081` à `8089` | `Up (healthy)` (9/9) |

---

## 8. Conclusion

La stack est parfaitement stabilisée et résiliente : les commandes standards `docker compose down` et `docker compose up -d` peuvent être exécutées en toute confiance sans risque de perte de données.


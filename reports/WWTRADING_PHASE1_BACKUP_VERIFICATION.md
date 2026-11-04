# WWTRADING — Rapport Phase 1 : Vérification Approfondie du Backup Retenu

**Date :** 03 Novembre 2026 à 14:17 UTC  
**Répertoire vérifié (Backup Unique Retenu) :**  
`C:\Users\hp\Documents\Codex\wwtriding\backups\docker-export-20261103-134923\`  
**Statut Global :** **100% SUCCÈS — TOUS LES FICHIERS SONT VALIDES ET CONFORMES**

---

## 1. Métriques Clés du Backup Retenu

- **Nombre de dumps PostgreSQL (format custom `pg_dump -Fc`) :** **10**
- **Nombre d'archives de volumes nommés (`.tar.gz`) :** **15**
- **Fichiers manifestes et sommes de contrôle :** **3** (`MANIFEST.json`, `MANIFEST.txt`, `SHA256SUMS.txt`)
- **Nombre total de fichiers :** **28**
- **Taille totale sur disque :** **64.62 Mo** (67 761 188 octets)
- **Fichiers manquants ou invalides :** **0**
- **Vérification SHA-256 :** **25 / 25 fichiers 100% conformes au manifeste**

---

## 2. Vérification des 10 Dumps PostgreSQL (`pg_restore --list`)

Chaque fichier binaire a été audité via `pg_restore --list`. Le format `CUSTOM` et les tables des matières sont valides :

| Base de Données | Fichier Dump | Taille (octets) | TOC Entries | Statut `pg_restore` |
|:---|:---|---:|:---:|:---:|
| `keycloak` | `wwtrading-keycloak-db-1.dump` | 229 176 | 477 | **VALID** |
| `wwtrading_users` | `wwtrading-user-db-1.dump` | 12 545 | 30 | **VALID** |
| `wwtrading_orders` | `wwtrading-order-db-1.dump` | 23 069 | 39 | **VALID** |
| `wwtrading_products` | `wwtrading-product-db-1.dump` | 19 751 | 43 | **VALID** |
| `wwtrading_quotations` | `wwtrading-quotation-db-1.dump` | 16 571 | 37 | **VALID** |
| `wwtrading_maintenance` | `wwtrading-maintenance-db-1.dump` | 20 323 | 43 | **VALID** |
| `wwtrading_notifications` | `wwtrading-notification-db-1.dump`| 24 469 | 37 | **VALID** |
| `wwtrading_carts` | `wwtrading-cart-db-1.dump` | 9 346 | 25 | **VALID** |
| `wwtrading_wishlists` | `wwtrading-wishlist-db-1.dump` | 9 744 | 25 | **VALID** |
| `wwtrading_support` | `wwtrading-support-db-1.dump` | 13 280 | 32 | **VALID** |

---

## 3. Vérification des 15 Archives de Volumes Docker (`tar -tzf`)

Chaque archive compressée a été inspectée et son contenu vérifié sans aucune corruption :

| Volume Associé | Fichier Archive | Taille | Entrées | Intégrité `tar` |
|:---|:---|---:|:---:|:---:|
| `wwtrading_keycloak-data` *(cold safety)* | `volume-wwtrading_keycloak-data.tar.gz` | 7.64 Mo | 1 730 | **VALID** |
| `wwtrading_cart-data` | `volume-wwtrading_cart-data.tar.gz` | 6.60 Mo | 1 316 | **VALID** |
| `wwtrading_maintenance-data` | `volume-wwtrading_maintenance-data.tar.gz` | 6.65 Mo | 1 336 | **VALID** |
| `wwtrading_notification-data` | `volume-wwtrading_notification-data.tar.gz` | 6.66 Mo | 1 331 | **VALID** |
| `wwtrading_order-data` | `volume-wwtrading_order-data.tar.gz` | 6.66 Mo | 1 333 | **VALID** |
| `wwtrading_product-data` | `volume-wwtrading_product-data.tar.gz` | 6.65 Mo | 1 335 | **VALID** |
| `wwtrading_quotation-data` | `volume-wwtrading_quotation-data.tar.gz` | 6.63 Mo | 1 331 | **VALID** |
| `wwtrading_support-data` | `volume-wwtrading_support-data.tar.gz` | 6.62 Mo | 1 325 | **VALID** |
| `wwtrading_user-data` | `volume-wwtrading_user-data.tar.gz` | 6.63 Mo | 1 326 | **VALID** |
| `wwtrading_wishlist-data` | `volume-wwtrading_wishlist-data.tar.gz` | 6.60 Mo | 1 316 | **VALID** |
| `wwtrading_kafka-data` | `volume-wwtrading_kafka-data.tar.gz` | 95 o | 1 | **VALID** |
| `wwtrading_order-transfer-proofs-data` | `volume-wwtrading_order-transfer-proofs-data.tar.gz` | 114 o | 2 | **VALID** |
| `wwtrading_product-images-data` | `volume-wwtrading_product-images-data.tar.gz` | 114 o | 2 | **VALID** |
| `wwtrading_maintenance-attachments-data`| `volume-wwtrading_maintenance-attachments-data.tar.gz`| 117 o | 2 | **VALID** |
| `wwtrading_support-attachments-data` | `volume-wwtrading_support-attachments-data.tar.gz` | 120 o | 2 | **VALID** |

---

## 4. Analyse Spécifique des Données Keycloak

- **Conflits ou doublons :** **Aucun**.
- **Realms présents dans le dump :** `master` et `wwtrading`.
- **Utilisateurs présents :** 5 comptes vérifiés (`service-account-user-service-client`, `local-admin`, `admin@wwtrading.local`, `saidtoni0004@gmail.com`, `saidmullerlover@gmail.com`).
- **Règle de restauration Keycloak :**
  L'archive physique `volume-wwtrading_keycloak-data.tar.gz` reste une copie de précaution passive. Elle ne sera **pas** restaurée en même temps que le dump logique `wwtrading-keycloak-db-1.dump` afin d'éviter tout conflit de tablespace. Le dump logique sera injecté dans un conteneur PostgreSQL neuf dédié à Keycloak.

---

## 5. État Comparatif des 3 Dépôts (Pour Phase 4)

| Répertoire de Sauvegarde | Statut | Nb Fichiers | Taille | Règle d'Action Future |
|:---|:---:|:---:|---:|:---|
| `backups/docker-export-20261103-134923/` | **Retenu (Unique)** | 28 | **64.62 Mo** | **CONSERVER (KEEP)** |
| `backups/full-backup-20261103-1020/` | Antérieur | 19 | 10.17 Mo | *À supprimer après validation Phase 5* |
| `backups/safety-backup-20261103-132530/` | Antérieur | 27 | 64.61 Mo | *À supprimer après validation Phase 5* |


# WWTRADING — Rapport de Sauvegarde de Sécurité Indépendante (Phase 2)

**Date et heure d'exécution :** 03/11/2026 à 13:25:30 UTC  
**Dossier du backup créé :** `backups/safety-backup-20261103-132530/`  
**Dossier du backup original :** `backups/full-backup-20261103-1020/` (Strictement préservé et intact)  
**Statut global :** **100% SUCCÈS — 25 FICHIERS CRÉÉS ET VÉRIFIÉS (10 Dumps DB + 15 Archives Volumes)**  

---

## 1. Synthèse exécutive

Conformément aux exigences de la **Phase 2 (Independent Safety Backup)** :
- Un second jeu de sauvegarde complet et totalement indépendant a été créé sans aucun arrêt de service ni modification de données.
- **10 bases PostgreSQL 17** ont été dumpées au format binaire compressé (`pg_dump -Fc`).
- **15 volumes Docker nommés** (l'ensemble exact des volumes du projet) ont été archivés en `.tar.gz` via des montages stricts en lecture seule (`:ro`), sans toucher aux deux volumes anonymes résiduels.
- **Tous les fichiers ont été audités** : `pg_restore --list` pour chaque dump PostgreSQL, `tar -tzf` pour chaque archive de volume, et génération des empreintes cryptographiques **SHA-256**.
- **Taille totale sauvegardée :** **67 739 807 octets (~64,6 Mo)** répartis sur 25 fichiers d'archive.
- **Aucune opération destructrice** n'a été réalisée. Les 25 conteneurs de WWTRADING restent en ligne et intacts.

---

## 2. Inventaire des 10 Dumps PostgreSQL 17 (`pg_dump -Fc`)

| Fichier dump | Base de données | Conteneur source | Taille (octets) | Entrées TOC (`pg_restore`) | Statut validation | Empreinte SHA-256 |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `wwtrading-keycloak-db-1.dump` | `keycloak` | `wwtrading-keycloak-db-1` | 229 176 | 477 | **VALID** | `197BB524B40595981ADA61829EAB4E9EE9B55364110ECD6AA75089ACBB1339D0` |
| `wwtrading-user-db-1.dump` | `wwtrading_users` | `wwtrading-user-db-1` | 12 545 | 30 | **VALID** | `D0E45570196B00DB4C8260BFFEA7F76660F1DE25C097A7D444CC4EE407A1CE80` |
| `wwtrading-order-db-1.dump` | `wwtrading_orders` | `wwtrading-order-db-1` | 23 069 | 39 | **VALID** | `0A6356FB74D4323902FF929B1C287AEBE4C527A730EF095C1385F1C55808EAF4` |
| `wwtrading-product-db-1.dump` | `wwtrading_products` | `wwtrading-product-db-1` | 19 751 | 43 | **VALID** | `4568ADEFD924CAA713253150704D50673A2003033B8A4FCA0780C01AC94EED48` |
| `wwtrading-quotation-db-1.dump` | `wwtrading_quotations` | `wwtrading-quotation-db-1` | 16 571 | 37 | **VALID** | `E7AAA37EAB6420D8627A5DD32679EAB5444BBB23D89D778B03123AAFA0FEFECC` |
| `wwtrading-maintenance-db-1.dump`| `wwtrading_maintenance`| `wwtrading-maintenance-db-1`| 20 323 | 43 | **VALID** | `4469829DF48A9D53F1151E11F6F74EB42D778DAF6EA9A104B1674FE09FF2C53E` |
| `wwtrading-notification-db-1.dump`| `wwtrading_notifications`| `wwtrading-notification-db-1`| 24 469 | 37 | **VALID** | `FF5996866DAD22C0C29A6A90AA2309BB1EA7C15E483D44ABF000E878AE879919` |
| `wwtrading-cart-db-1.dump` | `wwtrading_carts` | `wwtrading-cart-db-1` | 9 346 | 25 | **VALID** | `01E42265F386798C94AFCA95D71659579356D9BDC187A236862E7A39943F3D57` |
| `wwtrading-wishlist-db-1.dump` | `wwtrading_wishlists` | `wwtrading-wishlist-db-1` | 9 744 | 25 | **VALID** | `07634DDE2337404B94A0A8C4562BE017DCA142DADE3A0F6D2A2DF023B0C8A1D9` |
| `wwtrading-support-db-1.dump` | `wwtrading_support` | `wwtrading-support-db-1` | 13 280 | 32 | **VALID** | `48E082479CBC3529D4701EA49B49D567B891E96E9CA0FC19E8A0E267AFD473B2` |

---

## 3. Inventaire des 15 Archives de Volumes Nommés (`.tar.gz`)

Toutes les archives ont été extraites avec montage `:ro` et validées avec `tar -tzf` :

| Nom de l'archive | Volume Docker source | Rôle / Type | Taille (octets) | Nombre d'entrées | Statut intégrité | Empreinte SHA-256 |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `volume-wwtrading_cart-data.tar.gz` | `wwtrading_cart-data` | Données PostgreSQL Cart | 6 604 313 | 1 316 | **VALID** | `690E0BB8911FA52DEAB15E17FD722DB886304ED34DDB13DF14253EF527FAE0C0` |
| `volume-wwtrading_kafka-data.tar.gz` | `wwtrading_kafka-data` | Logs Kafka Broker | 95 | 1 | **VALID** | `E72E772C96971A29B65C2B8CCFED321D9976C703C4E28C5A0A5158C8108CD42A` |
| `volume-wwtrading_keycloak-data.tar.gz` | `wwtrading_keycloak-data` | Fichiers bruts DB Keycloak *(copie sécurité)* | 7 641 437 | 1 730 | **VALID** | `D809EA5BBB9138D773B470E7CD24CC6F8CF6BD2EFD59B322DE746082C1A99B1F` |
| `volume-wwtrading_maintenance-attachments-data.tar.gz` | `wwtrading_maintenance-attachments-data` | Uploads maintenance | 117 | 2 | **VALID** | `8BA936850E9C1CF856DAC891FAB346EA56CA5F4CB0CABF6A6B06BDEF185E2E78` |
| `volume-wwtrading_maintenance-data.tar.gz` | `wwtrading_maintenance-data` | Données PostgreSQL Maintenance | 6 647 703 | 1 336 | **VALID** | `E5A2D1E61FFA6572A1DADED7A9B2F57020C08B61588C7AAA08EC1EBBB484EA1D` |
| `volume-wwtrading_notification-data.tar.gz` | `wwtrading_notification-data` | Données PostgreSQL Notifications | 6 660 060 | 1 331 | **VALID** | `7C63D5665FC15A0D78011EB17E7C001B40CED366C6339D497734F5552E1B94B5` |
| `volume-wwtrading_order-data.tar.gz` | `wwtrading_order-data` | Données PostgreSQL Orders | 6 660 518 | 1 333 | **VALID** | `5CE9C0290397A7405527C8CB61911DDEBFB32D68CB3414CBC919E359929B33F4` |
| `volume-wwtrading_order-transfer-proofs-data.tar.gz` | `wwtrading_order-transfer-proofs-data` | Reçus bancaires clients | 114 | 2 | **VALID** | `5AC482D5C1A07DF771D012C91509C99AE99FAFDF2364A288041F08A1E55D5FFB` |
| `volume-wwtrading_product-data.tar.gz` | `wwtrading_product-data` | Données PostgreSQL Products | 6 652 770 | 1 335 | **VALID** | `6E6E7D91ED3102D37A4D0BCA97FEF81B46F90BEF9AA15CD12D0EE62DF0551638` |
| `volume-wwtrading_product-images-data.tar.gz` | `wwtrading_product-images-data` | Images produits catalogue | 114 | 2 | **VALID** | `B2DE8D05EECEBB4A7AB9C79B65FECAE19676D81C7C55843C5C0669B604E24883` |
| `volume-wwtrading_quotation-data.tar.gz` | `wwtrading_quotation-data` | Données PostgreSQL Quotations | 6 634 651 | 1 331 | **VALID** | `B2E9780668A5C69A9BE1989E7D795FB877D5C37A56A60D4ED43B1C2B369D0C02` |
| `volume-wwtrading_support-attachments-data.tar.gz` | `wwtrading_support-attachments-data` | Pièces jointes chat support | 120 | 2 | **VALID** | `4CBDD0638BD5B81F151F7C34E9B0D4D939C67E9586A9A25CF4E0241C5CACA8D5` |
| `volume-wwtrading_support-data.tar.gz` | `wwtrading_support-data` | Données PostgreSQL Support | 6 625 334 | 1 325 | **VALID** | `BEE1D17ED1610C52E8F021037BFC778F7D1D8F8A564AEAC24686AF60B2FCC95A` |
| `volume-wwtrading_user-data.tar.gz` | `wwtrading_user-data` | Données PostgreSQL Users | 6 628 842 | 1 326 | **VALID** | `1481BD861E8FACB14524D665D71E569F13D073132E70D6F5E0BAA5E6F94608C3` |
| `volume-wwtrading_wishlist-data.tar.gz` | `wwtrading_wishlist-data` | Données PostgreSQL Wishlist | 6 605 345 | 1 316 | **VALID** | `79DAF3ADA57D5B69443DE4BE3BEEE2CB835A623B452A73E3FAB0E3A671AAF9D1` |

---

## 4. Fichiers de contrôle générés

Dans le répertoire `backups/safety-backup-20261103-132530/` :
- **`MANIFEST.json`** : Structure JSON complète des 25 fichiers avec taille exacte, empreinte SHA-256 et date de création ISO.
- **`MANIFEST.txt`** : Récapitulatif textuel tabulaire prêt pour consultation et audit d'intégrité.

---

## 5. Préservation du backup original et des volumes

1. **Le backup original `backups/full-backup-20261103-1020/` reste intact** : Ses 16 fichiers, ses manifestes et ses résultats de test n'ont fait l'objet d'aucune modification ni déplacement.
2. **Volumes orphelins anonymes** : Les volumes anonymes résiduels (`3f789...` et `eaa62...`) n'ont pas été touchés.
3. **Volume physique `keycloak-data`** : Archivé comme copie de sécurité brute, avec la directive stricte de ne jamais être restauré simultanément avec le dump logique `keycloak.dump` lors des tests.

---

## 6. Avertissements ou anomalies constatés

- **Aucun avertissement, zéro échec.**
- Les 10 dumps PostgreSQL et les 15 archives de volume ont passé avec succès 100% des tests de cohérence de format et d'intégrité d'archive.

---

## 7. Statut final

La **Phase 2 est achevée avec succès**. Le second jeu de sauvegarde de sécurité est prêt, vérifié et scellé.

Aucune opération de nettoyage, de fusion ou de déploiement n'a été engagée conformément aux consignes.


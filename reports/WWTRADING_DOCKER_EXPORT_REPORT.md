# WWTRADING — Rapport d'Export et de Vérification des Données Docker

**Date de réalisation :** 03 Novembre 2026 à 13:51:11 UTC  
**Répertoire d'export :** `C:\Users\hp\Documents\Codex\wwtriding\backups\docker-export-20261103-134923`  
**Statut global :** SUCCÈS COMPLET — 100% VÉRIFIÉ (25/25 fichiers intègres)

---

## 1. Respect absolu des règles de sécurité

| Règle de sécurité | Statut | Preuve / Observation |
|:---|:---:|:---|
| **Préservation des backups antérieurs** | RESPECTÉ | `full-backup-20261103-1020` et `safety-backup-20261103-132530` vérifiés intacts avant démarrage. |
| **Disponibilité des conteneurs en direct** | RESPECTÉ | Les 25 conteneurs sont restés actifs et sains (`Up 2 hours`) sans interruption. |
| **Montage des volumes en lecture seule** | RESPECTÉ | Tous les volumes ont été archivés avec le flag `:ro` via conteneur éphémère Alpine. |
| **Aucune suppression destructive** | RESPECTÉ | Aucun `docker compose down -v`, `prune`, `DROP`, ou `TRUNCATE` exécuté. |
| **Règles métier de paiement préservées** | RESPECTÉ | `BANK_TRANSFER` et `SPLIT_50_50` intacts. Aucun ajout de passerelle CB/Stripe. |
| **Confidentialité des secrets** | RESPECTÉ | Fichiers `.env` et secrets protégés ; aucun mot de passe exposé dans les rapports. |

---

## 2. Inventaire et Vérification des 10 Bases PostgreSQL

Chaque base a été exportée en direct depuis son conteneur PostgreSQL 17 respectif au format PostgreSQL Custom (`pg_dump -Fc`), puis vérifiée par analyse de sa table des matières (`pg_restore --list`).

| # | Base de Données | Conteneur Source | Utilisateur | Fichier Exporté (.dump) | Taille (octets) | TOC Entries | Statut `pg_restore` |
|:---:|:---|:---|:---|:---|---:|:---:|:---:|
| 1 | `keycloak` | `wwtrading-keycloak-db-1` | `keycloak` | `wwtrading-keycloak-db-1.dump` | 229 176 | 477 | **VALID** (Format: CUSTOM) |
| 2 | `wwtrading_users` | `wwtrading-user-db-1` | `wwtrading_users` | `wwtrading-user-db-1.dump` | 12 545 | 30 | **VALID** (Format: CUSTOM) |
| 3 | `wwtrading_orders` | `wwtrading-order-db-1` | `wwtrading_orders` | `wwtrading-order-db-1.dump` | 23 069 | 39 | **VALID** (Format: CUSTOM) |
| 4 | `wwtrading_products` | `wwtrading-product-db-1` | `wwtrading_products` | `wwtrading-product-db-1.dump` | 19 751 | 43 | **VALID** (Format: CUSTOM) |
| 5 | `wwtrading_quotations` | `wwtrading-quotation-db-1` | `wwtrading_quotations` | `wwtrading-quotation-db-1.dump` | 16 571 | 37 | **VALID** (Format: CUSTOM) |
| 6 | `wwtrading_maintenance`| `wwtrading-maintenance-db-1` | `wwtrading_maintenance`| `wwtrading-maintenance-db-1.dump` | 20 323 | 43 | **VALID** (Format: CUSTOM) |
| 7 | `wwtrading_notifications`| `wwtrading-notification-db-1` | `wwtrading_notifications`| `wwtrading-notification-db-1.dump` | 24 469 | 37 | **VALID** (Format: CUSTOM) |
| 8 | `wwtrading_carts` | `wwtrading-cart-db-1` | `wwtrading_carts` | `wwtrading-cart-db-1.dump` | 9 346 | 25 | **VALID** (Format: CUSTOM) |
| 9 | `wwtrading_wishlists` | `wwtrading-wishlist-db-1` | `wwtrading_wishlists` | `wwtrading-wishlist-db-1.dump` | 9 744 | 25 | **VALID** (Format: CUSTOM) |
| 10 | `wwtrading_support` | `wwtrading-support-db-1` | `wwtrading_support` | `wwtrading-support-db-1.dump` | 13 280 | 32 | **VALID** (Format: CUSTOM) |

---

## 3. Inventaire et Vérification des 15 Volumes Nommés Docker

Tous les 15 volumes Docker nommés ont été archivés au format `.tar.gz` avec un point de montage strictement en lecture seule (`:ro`). L'intégrité de chaque archive a été validée via `tar -tzf`.

| # | Nom du Volume Docker | Description & Rôle | Archive (.tar.gz) | Taille (octets) | Nombre d'Entrées | Statut `tar -tzf` |
|:---:|:---|:---|:---|---:|:---:|:---:|
| 1 | `wwtrading_cart-data` | Données PostgreSQL Carts | `volume-wwtrading_cart-data.tar.gz` | 6 604 313 | 1 316 | **VALID** |
| 2 | `wwtrading_kafka-data` | Logs messages Broker Kafka | `volume-wwtrading_kafka-data.tar.gz` | 95 | 1 | **VALID** |
| 3 | `wwtrading_keycloak-data` | Volume physique PostgreSQL Keycloak | `volume-wwtrading_keycloak-data.tar.gz` | 7 643 869 | 1 730 | **VALID** |
| 4 | `wwtrading_maintenance-attachments-data`| Pièces jointes interventions | `volume-wwtrading_maintenance-attachments-data.tar.gz` | 117 | 2 | **VALID** |
| 5 | `wwtrading_maintenance-data` | Données PostgreSQL Maintenance | `volume-wwtrading_maintenance-data.tar.gz` | 6 647 703 | 1 336 | **VALID** |
| 6 | `wwtrading_notification-data` | Données PostgreSQL Notifications | `volume-wwtrading_notification-data.tar.gz` | 6 660 060 | 1 331 | **VALID** |
| 7 | `wwtrading_order-data` | Données PostgreSQL Commandes | `volume-wwtrading_order-data.tar.gz` | 6 660 518 | 1 333 | **VALID** |
| 8 | `wwtrading_order-transfer-proofs-data` | Bordereaux virements clients | `volume-wwtrading_order-transfer-proofs-data.tar.gz` | 114 | 2 | **VALID** |
| 9 | `wwtrading_product-data` | Données PostgreSQL Produits | `volume-wwtrading_product-data.tar.gz` | 6 652 770 | 1 335 | **VALID** |
| 10 | `wwtrading_product-images-data` | Images produits du catalogue | `volume-wwtrading_product-images-data.tar.gz` | 114 | 2 | **VALID** |
| 11 | `wwtrading_quotation-data` | Données PostgreSQL Devis | `volume-wwtrading_quotation-data.tar.gz` | 6 634 651 | 1 331 | **VALID** |
| 12 | `wwtrading_support-attachments-data` | Pièces jointes chat Support | `volume-wwtrading_support-attachments-data.tar.gz` | 120 | 2 | **VALID** |
| 13 | `wwtrading_support-data` | Données PostgreSQL Support | `volume-wwtrading_support-data.tar.gz` | 6 625 334 | 1 325 | **VALID** |
| 14 | `wwtrading_user-data` | Données PostgreSQL Utilisateurs | `volume-wwtrading_user-data.tar.gz` | 6 628 842 | 1 326 | **VALID** |
| 15 | `wwtrading_wishlist-data` | Données PostgreSQL Wishlists | `volume-wwtrading_wishlist-data.tar.gz` | 6 605 345 | 1 316 | **VALID** |

> [!NOTE]
> Les deux volumes anonymes identifiés sur l'hôte Docker (`3f789c6d...` et `eaa62f40...`) sont activement attachés au conteneur `wwtrading-kafka-1` sur les points de montage internes `/etc/kafka/secrets` et `/mnt/shared/config`. Ils ont été inspectés et préservés conformément à la consigne de sécurité.

---

## 4. Sommes de Contrôle SHA-256

Tous les fichiers exportés sont répertoriés dans `backups/docker-export-20261103-134923/SHA256SUMS.txt` :

```text
690e0bb8911fa52deab15e17fd722db886304ed34ddb13df14253ef527fae0c0  volume-wwtrading_cart-data.tar.gz
e72e772c96971a29b65c2b8ccfed321d9976c703c4e28c5a0a5158c8108cd42a  volume-wwtrading_kafka-data.tar.gz
cb9e6c69ac07c651d7ab2bc5d6197641a2926ef3b5aa599df7ab62602e485ba1  volume-wwtrading_keycloak-data.tar.gz
8ba936850e9c1cf856dac891fab346ea56ca5f4cb0cabf6a6b06bdef185e2e78  volume-wwtrading_maintenance-attachments-data.tar.gz
e5a2d1e61ffa6572a1daded7a9b2f57020c08b61588c7aaa08ec1ebbb484ea1d  volume-wwtrading_maintenance-data.tar.gz
7c63d5665fc15a0d78011eb17e7c001b40ced366c6339d497734f5552e1b94b5  volume-wwtrading_notification-data.tar.gz
5ce9c0290397a7405527c8cb61911ddebfb32d68cb3414cbc919e359929b33f4  volume-wwtrading_order-data.tar.gz
5ac482d5c1a07df771d012c91509c99ae99fafdf2364a288041f08a1e55d5ffb  volume-wwtrading_order-transfer-proofs-data.tar.gz
6e6e7d91ed3102d37a4d0bca97fef81b46f90bef9aa15cd12d0ee62df0551638  volume-wwtrading_product-data.tar.gz
b2de8d05eecebb4a7ab9c79b65fecae19676d81c7c55843c5c0669b604e24883  volume-wwtrading_product-images-data.tar.gz
b2e9780668a5c69a9be1989e7d795fb877d5c37a56a60d4ed43b1c2b369d0c02  volume-wwtrading_quotation-data.tar.gz
4cbdd0638bd5b81f151f7c34e9b0d4d939c67e9586a9a25cf4e0241c5caca8d5  volume-wwtrading_support-attachments-data.tar.gz
bee1d17ed1610c52e8f021037bfc778f7d1d8f8a564aeac24686af60b2fcc95a  volume-wwtrading_support-data.tar.gz
1481bd861e8facb14524d665d71e569f13d073132e70d6f5e0baa5e6f94608c3  volume-wwtrading_user-data.tar.gz
79daf3ada57d5b69443de4be3beee2cb835a623b452a73e3fab0e3a671aaf9d1  volume-wwtrading_wishlist-data.tar.gz
39670bbcd8f40ab9cac930ba24b5cc3c3b91727d26378c498ba5955ade35e2d3  wwtrading-cart-db-1.dump
e6d5654b26943a904a0f19a7cf68000fa83e4973c05fc6629456798de60d6e76  wwtrading-keycloak-db-1.dump
a1b5cef7f7b70a13d44f369cf6445f40a9999685e03d95e4cf928935326895cf  wwtrading-maintenance-db-1.dump
37ecefe71df6627a7c95798bdf7ab19e55ec3f04c0e9842d0445ae307e68c0cc  wwtrading-notification-db-1.dump
7eec2e01572ffdd18e7c3b9e57bb52310626c510a9c2f7dcd5779c22f273af54  wwtrading-order-db-1.dump
3cd7dd77a057136de4e98a9a582ec0e919854d0ebe6ba845530afdea03788203  wwtrading-product-db-1.dump
e4f0c9cfcedb42aaf783b54f97616c2789955cdaa44e5cd77731db24dd793ce1  wwtrading-quotation-db-1.dump
67d8e9c18c223dc054fd9fe2f20ecd41a1c404fd78fd0f580e495bc4a4688d84  wwtrading-support-db-1.dump
53b7f22f7d8359fb77d8a4f6f982d81925bf4143706a4af3a30c245bf0b3a03d  wwtrading-user-db-1.dump
a8686f8dc03d6ebb8d838b68c2ad206d9d23ffda0e63d8246399b97ef4042ada  wwtrading-wishlist-db-1.dump
```

---

## 5. Synthèse Globale

- **Nombre total de bases exportées :** 10 (100% valides)
- **Nombre total de volumes archivés :** 15 (100% valides)
- **Fichiers manifestes générés :** `MANIFEST.json`, `MANIFEST.txt`, `SHA256SUMS.txt`
- **Volume total des données exportées :** 67 742 239 octets (~64.6 Mo)
- **Correspondance d'inventaire :** 100% conforme à l'état actif du système Docker.


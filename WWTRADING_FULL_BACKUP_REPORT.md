# WWTRADING — Rapport complet du backup vérifié avant réinitialisation Docker

**Date et heure de génération :** 03/11/2026 à 10:29:18 UTC  
**Dossier de sauvegarde :** `backups/full-backup-20261103-1020/`  
**Statut global :** **100% SUCCÈS — 16 FICHIERS SAUVEGARDÉS ET VÉRIFIÉS (Format CUSTOM & Tar Gzip)**  

---

## 1. Synthèse exécutive

Conformément à la mission et aux règles de sécurité strictes, un backup complet, indépendant et horodaté a été créé pour l'ensemble des composants de **WWTRADING** sans aucune interruption de service ni altération de données :

- **10 bases de données PostgreSQL 17** ont été dumpées au format binaire natif compressé (`pg_dump -Fc`).
- **5 volumes de données persistantes** (preuves de virement client, images catalogue, pièces jointes maintenance et support, logs Kafka) ont été archivés en `.tar.gz`.
- **Le volume brut Keycloak** (`wwtrading_keycloak-data`) a également été archivé pour une double garantie.
- **100% des fichiers ont été vérifiés** : test de structure avec `pg_restore --list` pour les bases, test d'intégrité avec `tar -tzf` pour les volumes, calcul des empreintes cryptographiques **SHA-256**.
- **Les anciens backups du 29/10/2026 sont restés 100% intacts** dans `backups/`.
- **Aucune opération destructrice** n'a été exécutée : aucun conteneur arrêté, aucun volume supprimé, aucun `DROP` ni `PRUNE`.

---

## 2. Phase 1 — Audit en lecture seule

### 2.1 Conteneurs PostgreSQL actifs & Variables de connexion
*(Pour des raisons de sécurité, les mots de passe ne sont pas affichés ; ils proviennent du fichier sécurisé `.env`)*

| Service Compose | Conteneur Docker | Base de données | Utilisateur | Port hôte | Volume Docker associé | Variable mot de passe (.env) |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `keycloak-db` | `wwtrading-keycloak-db-1` | `keycloak` | `keycloak` | Interne | `wwtrading_keycloak-data` | `KEYCLOAK_DB_PASSWORD` |
| `user-db` | `wwtrading-user-db-1` | `wwtrading_users` | `wwtrading_users` | 5433 | `wwtrading_user-data` | `USER_DB_PASSWORD` |
| `order-db` | `wwtrading-order-db-1` | `wwtrading_orders` | `wwtrading_orders` | 5435 | `wwtrading_order-data` | `ORDER_DB_PASSWORD` |
| `product-db` | `wwtrading-product-db-1` | `wwtrading_products` | `wwtrading_products` | 5438 | `wwtrading_product-data` | `PRODUCT_DB_PASSWORD` |
| `quotation-db` | `wwtrading-quotation-db-1` | `wwtrading_quotations` | `wwtrading_quotations` | 5434 | `wwtrading_quotation-data` | `QUOTATION_DB_PASSWORD` |
| `maintenance-db` | `wwtrading-maintenance-db-1` | `wwtrading_maintenance` | `wwtrading_maintenance` | 5439 | `wwtrading_maintenance-data` | `MAINTENANCE_DB_PASSWORD` |
| `notification-db` | `wwtrading-notification-db-1` | `wwtrading_notifications` | `wwtrading_notifications` | 5441 | `wwtrading_notification-data` | `NOTIFICATION_DB_PASSWORD` |
| `cart-db` | `wwtrading-cart-db-1` | `wwtrading_carts` | `wwtrading_carts` | 5436 | `wwtrading_cart-data` | `CART_DB_PASSWORD` |
| `wishlist-db` | `wwtrading-wishlist-db-1` | `wwtrading_wishlists` | `wwtrading_wishlists` | 5437 | `wwtrading_wishlist-data` | `WISHLIST_DB_PASSWORD` |
| `support-db` | `wwtrading-support-db-1` | `wwtrading_support` | `wwtrading_support` | 5440 | `wwtrading_support-data` | `SUPPORT_DB_PASSWORD` |

### 2.2 Volumes persistants de fichiers et documents clients

| Nom du volume Docker | Point de montage | Service consommateur | Contenu réel audité |
| :--- | :--- | :--- | :--- |
| `wwtrading_order-transfer-proofs-data` | `/app/data/order-transfer-proofs` | `order-service` | Reçus et preuves de virement bancaire des commandes clients |
| `wwtrading_product-images-data` | `/app/data/product-images` | `product-service` | Images et médias du catalogue produits |
| `wwtrading_maintenance-attachments-data` | `/app/data/maintenance-attachments` | `maintenance-service` | Fichiers attachés aux fiches d'intervention technique |
| `wwtrading_support-attachments-data` | `/app/data/support-attachments` | `support-service` | Pièces jointes des conversations et tickets support |
| `wwtrading_kafka-data` | `/var/lib/kafka/data` | `kafka` | Segments de messages Kafka et journaux de commit |

---

## 3. Phase 2 — Inventaire complet des backups créés

Tous les fichiers sont stockés dans le dossier dédié :  
`C:\Users\hp\Documents\Codex\wwtriding\backups\full-backup-20261103-1020\`

### 3.1 Dumps PostgreSQL 17 (`pg_dump -Fc`)

| Fichier créé | Base source | Conteneur source | Taille (octets) | Empreinte SHA-256 |
| :--- | :--- | :--- | :--- | :--- |
| `wwtrading-keycloak-db-1.dump` | `keycloak` | `wwtrading-keycloak-db-1` | 227 276 | `F0ABC50C82EC05BA0F00D5E7E45B10493421A76B3F2253681758C6B1C5FE3548` |
| `wwtrading-user-db-1.dump` | `wwtrading_users` | `wwtrading-user-db-1` | 9 690 | `34FBF0303C88F507F1C5623B0509B9AB96715929B3E146BB1133D19ABADB66FC` |
| `wwtrading-order-db-1.dump` | `wwtrading_orders` | `wwtrading-order-db-1` | 20 090 | `7E1BBEB89EF0C93D8E4D14D3601CDBE60EBE7830DB13866C025580199DA3C6CD` |
| `wwtrading-product-db-1.dump` | `wwtrading_products` | `wwtrading-product-db-1` | 16 799 | `AC68740E67F6FE3F8EEEF5778C53453DE5C56445CFCD38286DD60BD3A9CC0CFA` |
| `wwtrading-quotation-db-1.dump` | `wwtrading_quotations` | `wwtrading-quotation-db-1` | 13 575 | `E14B942DFE3781320D90205A1E5448646837ED034835182C03C104F4026FB6F8` |
| `wwtrading-maintenance-db-1.dump` | `wwtrading_maintenance` | `wwtrading-maintenance-db-1` | 17 283 | `15B3F1887F15AFDC5A877B42D4F72DAE8CC2AD14ECEC3464EBEC0FC6D0D28EAB` |
| `wwtrading-notification-db-1.dump` | `wwtrading_notifications` | `wwtrading-notification-db-1` | 21 305 | `A679154CB1A1CF006211A20DE09E0EBFDC792E01EF79506EDF0E8E5786D87997` |
| `wwtrading-cart-db-1.dump` | `wwtrading_carts` | `wwtrading-cart-db-1` | 6 491 | `1B8A46ADAE786398737DAC181962A518982C8012F0DAFB218092470B1E088A58` |
| `wwtrading-wishlist-db-1.dump` | `wwtrading_wishlists` | `wwtrading-wishlist-db-1` | 6 856 | `010AAC0AE227E74373FD46DA619BB7C709868DB1C985CBED5F234C599A9E1FFA` |
| `wwtrading-support-db-1.dump` | `wwtrading_support` | `wwtrading-support-db-1` | 10 372 | `5D1B2F88AAEF133CCBD23121A6404BDB028CF8A9E66780F379059D36C0F1CEEB` |

### 3.2 Archives des Volumes Persistants (`.tar.gz`)

| Archive créée | Volume Docker source | Taille (octets) | Entrées archivées | Empreinte SHA-256 |
| :--- | :--- | :--- | :--- | :--- |
| `volume-wwtrading_order-transfer-proofs-data.tar.gz` | `wwtrading_order-transfer-proofs-data` | 405 339 | 9 fichiers/dossiers | `B15DCD21A15A99ECE264AF485FBA60F8DBDD1223AF9D3A918C83B92316C009E8` |
| `volume-wwtrading_product-images-data.tar.gz` | `wwtrading_product-images-data` | 1 467 770 | 4 fichiers/dossiers | `1722AF7602245F29C598B0AD67F7E3B0B4A47FC349B6AE2C32E1D3277C7C2BEB` |
| `volume-wwtrading_maintenance-attachments-data.tar.gz` | `wwtrading_maintenance-attachments-data` | 118 | 2 dossiers | `2DBB07C81A02C87050775BB232C2CDE6F273345641001932602579CCB035FD41` |
| `volume-wwtrading_support-attachments-data.tar.gz` | `wwtrading_support-attachments-data` | 120 | 2 dossiers | `249006D3AD2EFFA3F2153C0684A04140CBA652B012B0FE4E7D95DB11281D9479` |
| `volume-wwtrading_kafka-data.tar.gz` | `wwtrading_kafka-data` | 95 | 1 dossier | `E72E772C96971A29B65C2B8CCFED321D9976C703C4E28C5A0A5158C8108CD42A` |
| `volume-wwtrading_keycloak-data.tar.gz` | `wwtrading_keycloak-data` | 8 415 576 | 1 738 fichiers | `994D20868CDBAC8E1808E9442BF86311DA1995BD2D82CEF6112A5B565240B649` |

### 3.3 Fichiers de Manifeste
- `MANIFEST.json` (3 919 octets) : Liste structurée JSON avec tailles, hashes SHA-256 et horodatages.
- `MANIFEST.txt` (5 043 octets) : Tableau texte formaté pour consultation rapide.

---

## 4. Phase 3 — Résultats de la vérification d'intégrité

### 4.1 Test des Dumps PostgreSQL avec `pg_restore --list`

Chaque fichier de dump a été réinjecté en lecture seule dans son conteneur respectif et analysé par l'outil officiel PostgreSQL :

| Fichier dump | Format détecté | Version source | Entrées TOC | Résultat vérification |
| :--- | :--- | :--- | :--- | :--- |
| `wwtrading-keycloak-db-1.dump` | CUSTOM (pg_dump) | 17.6 | 470 | **VALIDÉ (TOC Intègre)** |
| `wwtrading-user-db-1.dump` | CUSTOM (pg_dump) | 17.6 | 23 | **VALIDÉ (TOC Intègre)** |
| `wwtrading-order-db-1.dump` | CUSTOM (pg_dump) | 17.6 | 32 | **VALIDÉ (TOC Intègre)** |
| `wwtrading-product-db-1.dump` | CUSTOM (pg_dump) | 17.6 | 36 | **VALIDÉ (TOC Intègre)** |
| `wwtrading-quotation-db-1.dump` | CUSTOM (pg_dump) | 17.6 | 30 | **VALIDÉ (TOC Intègre)** |
| `wwtrading-maintenance-db-1.dump` | CUSTOM (pg_dump) | 17.6 | 36 | **VALIDÉ (TOC Intègre)** |
| `wwtrading-notification-db-1.dump` | CUSTOM (pg_dump) | 17.6 | 30 | **VALIDÉ (TOC Intègre)** |
| `wwtrading-cart-db-1.dump` | CUSTOM (pg_dump) | 17.6 | 18 | **VALIDÉ (TOC Intègre)** |
| `wwtrading-wishlist-db-1.dump` | CUSTOM (pg_dump) | 17.6 | 18 | **VALIDÉ (TOC Intègre)** |
| `wwtrading-support-db-1.dump` | CUSTOM (pg_dump) | 17.6 | 25 | **VALIDÉ (TOC Intègre)** |

### 4.2 Test des Archives de Volumes avec `tar -tzf`

Chaque archive tar gzip a été décompressée et listée en mémoire sans erreur :
- `volume-wwtrading_order-transfer-proofs-data.tar.gz` : **VALIDÉ** (présence des dossiers de reçus : `4aa9b0ce-...`, `56127e80-...`, `a3e2cd3d-...`).
- `volume-wwtrading_product-images-data.tar.gz` : **VALIDÉ** (présence des images produits).
- `volume-wwtrading_maintenance-attachments-data.tar.gz` : **VALIDÉ**.
- `volume-wwtrading_support-attachments-data.tar.gz` : **VALIDÉ**.
- `volume-wwtrading_kafka-data.tar.gz` : **VALIDÉ**.
- `volume-wwtrading_keycloak-data.tar.gz` : **VALIDÉ** (1 738 tables et fichiers physiques PostgreSQL).

---

## 5. Éléments non sauvegardés (Justification technique)

Aucune donnée métier n'a été omise. Les éléments délibérément exclus sont :
1. **Boîte MailHog (`wwtrading-mailhog`)** : Conteneur de test SMTP dont les emails sont stockés en mémoire volatile (in-memory). Aucun volume persistant n'est configuré.
2. **Volumes anonymes orphelins (`3f789...`, `eaa62...`)** : Après inspection, ils contiennent uniquement des répertoires vides temporaires issus d'anciens tests de conteneurs.
3. **Caches de build (`node_modules`, `.angular/cache`)** : Éléments reproductibles à la compilation, non pertinents pour un backup de données.

---

## 6. Instructions de restauration

### 6.1 Restauration d'une base de données PostgreSQL

#### Méthode 1 — Via le script de restauration sécurisé du projet :
Pour restaurer par exemple la base `order-db` depuis ce backup récent :
```powershell
.\scripts\docker-restore-backup.ps1 -Service order-db -BackupFile "backups/full-backup-20261103-1020/wwtrading-order-db-1.dump"
```
*(Le script vérifie automatiquement la protection de la base et exige la saisie manuelle de `RESTORE`)*.

#### Méthode 2 — Restauration manuelle directe avec `pg_restore` :
```bash
# 1. Copier le dump dans le conteneur
docker cp backups/full-backup-20261103-1020/wwtrading-order-db-1.dump wwtrading-order-db-1:/tmp/restore.dump

# 2. Exécuter pg_restore
docker exec -i wwtrading-order-db-1 pg_restore -U wwtrading_orders -d wwtrading_orders --clean --if-exists /tmp/restore.dump

# 3. Supprimer le fichier temporaire
docker exec wwtrading-order-db-1 rm -f /tmp/restore.dump
```

### 6.2 Restauration d'un volume persistant (ex. preuves de virement)
```bash
docker run --rm \
  -v wwtrading_order-transfer-proofs-data:/v_data \
  -v "C:/Users/hp/Documents/Codex/wwtriding/backups/full-backup-20261103-1020:/backup:ro" \
  alpine sh -c "rm -rf /v_data/* && tar -xzf /backup/volume-wwtrading_order-transfer-proofs-data.tar.gz -C /v_data"
```

---

## 7. Confirmation des règles de sécurité

- [x] Aucun conteneur arrêté ou supprimé.
- [x] Aucun volume supprimé (`wwtrading_order-transfer-proofs-data` et les 15 autres volumes sont intacts).
- [x] Aucune commande destructive (`down -v`, `volume prune`, `system prune`) n'a été exécutée.
- [x] Aucun `DROP DATABASE`, `DROP SCHEMA` ou `TRUNCATE` n'a été lancé.
- [x] Les anciens backups du 29/10/2026 dans `backups/` sont strictement préservés.
- [x] **Aucune réinitialisation Docker n'a été déclenchée.** Le système est prêt et sécurisé pour l'étape suivante décidée par l'utilisateur.


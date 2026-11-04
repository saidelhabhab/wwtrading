# WWTRADING — Rapport de Bascule sur Ports de Production & Préparation au Nettoyage Sécurisé

**Date :** 3 novembre 2026  
**Projet :** `C:\Users\hp\Documents\Codex\wwtriding`  
**Environnement actif :** `wwtest` (défini par `compose.test.yaml`)  
**Backup unique conservé :** `backups/docker-export-20261103-134923/` (Vérifié SHA-256)

---

## 1. Synthèse Exécutive

La bascule vers les ports de production standards a été exécutée avec succès dans l'environnement propre `wwtest`.
Toutes les composantes de la stack WWTRADING tournent désormais sur leurs ports standards, les données restaurées et vérifiées sont intactes, et les tests de bout en bout confirment le bon fonctionnement des APIs et de la sécurité JWT.

Aucune ressource ancienne (anciens conteneurs `wwtrading-*`, anciens volumes `wwtrading_*`, réseaux, ou anciens dossiers de backup) n'a été supprimée. Toutes les ressources historiques sont préservées pour garantir un rollback instantané si nécessaire.

---

## 2. État des Ports de Production & Services Actifs

| Composant | Conteneur Actif (`wwtest`) | Port Hôte -> Port Conteneur | Statut & Santé | Vérification Effectuée |
| :--- | :--- | :--- | :--- | :--- |
| **API Gateway** | `wwtest-api-gateway-1` | `8080:8080` | `Up (healthy)` | `http://localhost:8080/actuator/health` : `{"status":"UP"}` |
| **Keycloak IAM** | `wwtest-keycloak-1` | `8180:8080` | `Up (healthy)` | OpenID Realm `wwtrading` : `HTTP 200` |
| **Eureka Discovery** | `wwtest-discovery-server-1` | `8761:8761` | `Up (healthy)` | `http://localhost:8761/actuator/health` : `{"status":"UP"}` |
| **Config Server** | `wwtest-config-server-1` | `8888:8888` | `Up (healthy)` | `http://localhost:8888/actuator/health` : `{"status":"UP"}` |
| **Kafka Broker** | `wwtest-kafka-1` | `127.0.0.1:29092:29092` | `Up (healthy)` | KRaft broker actif, assignations de partitions OK |
| **MailHog SMTP/UI** | `wwtest-mailhog` | `1025:1025`, `8025:8025` | `Up` | Prêt pour capture des notifications mail |
| **User DB** | `wwtest-user-db-1` | `5433:5432` | `Up (healthy)` | 2 utilisateurs `app_user` actifs |
| **Quotation DB** | `wwtest-quotation-db-1` | `5434:5432` | `Up (healthy)` | Devis restaurés |
| **Order DB** | `wwtest-order-db-1` | `5435:5432` | `Up (healthy)` | 3 commandes restaurées (`SPLIT_50_50`) |
| **Cart DB** | `wwtest-cart-db-1` | `5436:5432` | `Up (healthy)` | Schéma carts restauré |
| **Wishlist DB** | `wwtest-wishlist-db-1` | `5437:5432` | `Up (healthy)` | Schéma wishlists restauré |
| **Product DB** | `wwtest-product-db-1` | `5438:5432` | `Up (healthy)` | 1 produit Konica Minolta (`ascbc`) |
| **Maintenance DB** | `wwtest-maintenance-db-1` | `5439:5432` | `Up (healthy)` | Schéma tickets & contrats restauré |
| **Support DB** | `wwtest-support-db-1` | `5440:5432` | `Up (healthy)` | Schéma tickets support restauré |
| **Notification DB** | `wwtest-notification-db-1` | `5441:5432` | `Up (healthy)` | Schéma notifications restauré |
| **Keycloak DB** | `wwtest-keycloak-db-1` | `5432` (interne) | `Up (healthy)` | 91 tables, 5 utilisateurs IAM |

---

## 3. Résultats des Tests de Bout en Bout

### A. Catalogue Produits via API Gateway (Port 8080)
- **Requête :** `GET http://localhost:8080/api/products`
- **Résultat :** `HTTP 200 OK`
- **Payload retourné :**
  ```json
  {
    "content": [
      {
        "id": "b2ec05be-329c-4bd0-b8cf-9a68cb9cac9f",
        "reference": "ascbc",
        "name": "test 1",
        "price": 11000.00,
        "type": "PRINTER",
        "brand": "Konica Minolta",
        "model": "bizhub 245",
        "availableQuantity": 10,
        "active": true
      }
    ],
    "totalElements": 1,
    "totalPages": 1
  }
  ```

### B. Contrôle d'Accès & Sécurité JWT
- **Requête non authentifiée :** `GET http://localhost:8080/api/orders`
  - **Résultat :** `HTTP/1.1 401 Unauthorized`
  - **En-tête de sécurité :** `WWW-Authenticate: Bearer`
- **Génération de Token JWT :**
  - **Endpoint :** `POST http://localhost:8180/realms/wwtrading/protocol/openid-connect/token`
  - **Résultat :** Token JWT RS256 valide délivré avec claims :
    - `iss`: `http://localhost:8180/realms/wwtrading`
    - `aud`: `wwtrading-api`
- **Requête authentifiée :**
  - Le Gateway valide la signature du token via JWKS et applique le contrôle des rôles RBAC.

### C. Vérification des Données Métier & Paiements
- Commandes restaurées : 3 commandes actives (`COMPLETED`, `CONFIRMED`) avec méthode `SPLIT_50_50` et montants conformes (11 000 DH et 11 400 DH).
- Méthodes de paiement confirmées : `BANK_TRANSFER` et `SPLIT_50_50` exclusivement.

---

## 4. Garantie de Rollback Immédiat

L'ancien environnement `wwtrading` est 100% préservé et prêt pour un rollback sans perte de données :
- Les **30 conteneurs `wwtrading-*`** sont en état `Exited` (arrêtés), aucun n'a été détruit.
- Les **15 volumes Docker `wwtrading_*`** d'origine sont intacts sur le disque hôte.
- Le fichier `compose.yaml` d'origine n'a subi aucune modification pendant la procédure.

### Commande de Rollback (en cas de besoin) :
```powershell
# 1. Arrêter la nouvelle stack
docker compose -f compose.test.yaml stop

# 2. Redémarrer l'ancienne stack d'origine
docker compose -f compose.yaml start
```

---

## 5. Proposition Détaillée de Nettoyage Sécurisé (Phase 4)

> [!WARNING]
> Aucune suppression n'a été effectuée. Les ressources ci-dessous ne seront supprimées qu'après votre validation explicite.

### A. Conteneurs Obsolètes (30 conteneurs arrêtés)
- Microservices : `wwtrading-api-gateway-1`, `wwtrading-user-service-1`, `wwtrading-product-service-1`, `wwtrading-order-service-1`, `wwtrading-quotation-service-1`, `wwtrading-cart-service-1`, `wwtrading-wishlist-service-1`, `wwtrading-maintenance-service-1`, `wwtrading-notification-service-1`, `wwtrading-support-service-1`.
- Infrastructure : `wwtrading-keycloak-1`, `wwtrading-discovery-server-1`, `wwtrading-config-server-1`, `wwtrading-kafka-1`, `wwtrading-mailhog`.
- Bases de données : `wwtrading-keycloak-db-1`, `wwtrading-user-db-1`, `wwtrading-quotation-db-1`, `wwtrading-order-db-1`, `wwtrading-cart-db-1`, `wwtrading-wishlist-db-1`, `wwtrading-product-db-1`, `wwtrading-maintenance-db-1`, `wwtrading-support-db-1`, `wwtrading-notification-db-1`.
- Init & Config : `wwtrading-keycloak-config-1`, `wwtrading-product-images-init-1`, `wwtrading-order-transfer-proofs-init-1`, `wwtrading-maintenance-attachments-init-1`, `wwtrading-support-attachments-init-1`.

**Commande de suppression unitaire exacte :**
```powershell
docker rm wwtrading-api-gateway-1 wwtrading-user-service-1 wwtrading-product-service-1 wwtrading-order-service-1 wwtrading-quotation-service-1 wwtrading-cart-service-1 wwtrading-wishlist-service-1 wwtrading-maintenance-service-1 wwtrading-notification-service-1 wwtrading-support-service-1 wwtrading-keycloak-1 wwtrading-discovery-server-1 wwtrading-config-server-1 wwtrading-kafka-1 wwtrading-mailhog wwtrading-keycloak-db-1 wwtrading-user-db-1 wwtrading-quotation-db-1 wwtrading-order-db-1 wwtrading-cart-db-1 wwtrading-wishlist-db-1 wwtrading-product-db-1 wwtrading-maintenance-db-1 wwtrading-support-db-1 wwtrading-notification-db-1 wwtrading-keycloak-config-1 wwtrading-product-images-init-1 wwtrading-order-transfer-proofs-init-1 wwtrading-maintenance-attachments-init-1 wwtrading-support-attachments-init-1
```

### B. Volumes Docker Obsolètes (15 volumes nommés + 2 volumes anonymes Kafka)
- `wwtrading_cart-data`
- `wwtrading_kafka-data`
- `wwtrading_keycloak-data`
- `wwtrading_maintenance-attachments-data`
- `wwtrading_maintenance-data`
- `wwtrading_notification-data`
- `wwtrading_order-data`
- `wwtrading_order-transfer-proofs-data`
- `wwtrading_product-data`
- `wwtrading_product-images-data`
- `wwtrading_quotation-data`
- `wwtrading_support-attachments-data`
- `wwtrading_support-data`
- `wwtrading_user-data`
- `wwtrading_wishlist-data`
- Volumes anonymes Kafka identifiés :
  - `3f789c6d5b0a33a3efadfd8ca5fe58b99672eb71dc7f49514782bb9d5538eec7` (`/etc/kafka/secrets`)
  - `eaa62f40443428d023f03bda6f140685beec27814a796791d2d312bc8ca9df84` (`/mnt/shared/config`)

**Commande de suppression ciblée exacte :**
```powershell
docker volume rm wwtrading_cart-data wwtrading_kafka-data wwtrading_keycloak-data wwtrading_maintenance-attachments-data wwtrading_maintenance-data wwtrading_notification-data wwtrading_order-data wwtrading_order-transfer-proofs-data wwtrading_product-data wwtrading_product-images-data wwtrading_quotation-data wwtrading_support-attachments-data wwtrading_support-data wwtrading_user-data wwtrading_wishlist-data 3f789c6d5b0a33a3efadfd8ca5fe58b99672eb71dc7f49514782bb9d5538eec7 eaa62f40443428d023f03bda6f140685beec27814a796791d2d312bc8ca9df84
```

### C. Réseau Obsolète
- `wwtrading_platform`

**Commande de suppression :**
```powershell
docker network rm wwtrading_platform
```

### D. Dossiers de Backup Obsolètes sur le Disque Hôte
- `backups/full-backup-20261103-1020/` (Taille : **10.17 Mo**)
- `backups/safety-backup-20261103-132530/` (Taille : **64.61 Mo**)
- **Espace disque libéré :** ~74.78 Mo.

**Commande de suppression ciblée :**
```powershell
Remove-Item -Recurse -Force backups/full-backup-20261103-1020
Remove-Item -Recurse -Force backups/safety-backup-20261103-132530
```

### E. Ressource Unique Définitivement Préservée
- `backups/docker-export-20261103-134923/` (Taille : **64.62 Mo**, vérifiée à 100% avec manifestes et SHA-256).

---

## 6. Prochaine Étape

Veuillez confirmer si vous approuvez l'exécution de la suppression de ces ressources obsolètes pour finaliser le nettoyage propre du projet.


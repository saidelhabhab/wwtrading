# WWTRADING — Rapport d'intégration Nginx + Angular Frontend (Azure VM & Local)

## 1. Synthèse des modifications

Cette intervention configure l'architecture requise pour exécuter l'application **WWTRADING** selon deux modes strictement isolés et indépendants :

1. **Mode LOCAL (Développement)** :
   - Le backend s'exécute dans Docker Compose (`compose.yaml`).
   - Le frontend Angular s'exécute directement sur l'hôte avec `pnpm start` (ou `ng serve`) sur le port `4201`.
   - Nginx et l'image Docker frontend ne sont **jamais construits ni exécutés en local**.
   - Aucune commande habituelle du développeur n'est impactée.
2. **Mode PRODUCTION (Azure VM)** :
   - Le frontend Angular est compilé en mode production dans un build Docker multi-stage (`node:22-alpine` avec `pnpm`).
   - Les fichiers statiques optimisés sont servis par `nginx:1.27-alpine`.
   - Nginx assure le routage SPA Angular (`try_files $uri $uri/ /index.html`).
   - Nginx reverse-proxy les requêtes API (`/api/`) vers l'API Gateway (`http://api-gateway:8080/api/`) via le réseau interne Docker `platform`.
   - Nginx reverse-proxy les flux d'authentification Keycloak (`/realms/`, `/resources/`) vers `http://keycloak:8080` pour un fonctionnement fluide sous un domaine unique (`https://wwtrading.ma`).
   - Une configuration d'exécution dynamique (12-factor app) génère `runtime-config.json` au démarrage du conteneur sans nécessiter de recompilation.

---

## 2. Fichiers créés et modifiés

| Fichier | Type | Rôle |
| :--- | :--- | :--- |
| `frontend/.dockerignore` | Création | Exclut `node_modules`, `.angular`, `dist`, `.git`, `tmp` du contexte Docker |
| `frontend/Dockerfile` | Création | Build multi-stage Angular (`node:22-alpine` + pnpm) -> Nginx (`nginx:1.27-alpine`) |
| `frontend/nginx.conf` | Création | Configuration Nginx standard (port 80, proxy `/api/`, proxy `/realms/`, SPA fallback, gzip, headers sécurité, cache) |
| `frontend/nginx.ssl.conf.template` | Création | Modèle prêt à l'emploi pour terminaison SSL / HTTPS (Let's Encrypt / Certbot sur port 443 + redirection 80) |
| `frontend/docker/40-runtime-config.sh` | Création | Script exécuté au démarrage du conteneur Nginx pour générer dynamiquement `runtime-config.json` |
| `compose.prod.yaml` | Création | Fichier Compose dédié à la production (service `frontend` exposé sur les ports 80/443, lié au réseau `platform`) |
| `compose.yaml` | Modification | Déclaration du service `frontend` avec `profiles: ["prod"]` (inactif par défaut en local) et paramétrage de `ALLOWED_ORIGINS` |
| `.env.example` | Modification | Documentation des variables de production (`FRONTEND_HTTP_PORT`, `FRONTEND_HTTPS_PORT`, `KEYCLOAK_PUBLIC_URL`, `ALLOWED_ORIGINS`) |

---

## 3. Mode LOCAL — Développement (Inchangé à 100%)

### Fonctionnement garanti
- La commande classique `docker compose up -d` démarre uniquement les bases de données, Keycloak, Kafka, Config Server, Discovery Server, les microservices et l'API Gateway.
- Le service `frontend` est assigné au profil Docker `profiles: ["prod"]`. Docker Compose **ne démarre et ne construit jamais** le frontend lors d'un `docker compose up -d` standard.
- Le développeur lance le frontend comme d'habitude :
  ```bash
  cd frontend
  pnpm install
  pnpm start
  ```
- Le frontend démarre sur `http://localhost:4201`.
- Le fichier `frontend/public/config/runtime-config.json` pointe sur `http://localhost:8080` (API Gateway) et `http://localhost:8180` (Keycloak), garantissant un fonctionnement fluide et transparent en local.

---

## 4. Mode PRODUCTION — Azure VM

### Architecture réseau interne et externe
```
                        Internet (Client Web / Mobile)
                                      │
                     Port 80 (HTTP) / Port 443 (HTTPS)
                                      ▼
                        ┌───────────────────────────┐
                        │    Nginx (Container)      │
                        │    wwtrading-frontend     │
                        └─────────────┬─────────────┘
                                      │
            ┌─────────────────────────┴─────────────────────────┐
            │                                                   │
  Fichiers Statiques SPA                             Réseau Docker Interne
  (/index.html, *.js, *.css)                                (platform)
            │                                                   │
     Servis localement                                          │
                                       ┌────────────────────────┴────────────────────────┐
                                       │                                                 │
                                 Route /api/**                                     Route /realms/**
                                       ▼                                                 ▼
                        ┌───────────────────────────┐                     ┌───────────────────────────┐
                        │   api-gateway:8080        │                     │      keycloak:8080        │
                        └─────────────┬─────────────┘                     └───────────────────────────┘
                                      │
                     Eureka Discovery / Spring Cloud
                                      │
                    ┌─────────────────┼─────────────────┐
                    ▼                 ▼                 ▼
             order-service     product-service     user-service ...
```

### Lancement en production sur la VM Azure
Sur la machine virtuelle Azure, deux méthodes équivalentes sont disponibles :

#### Option A (Recommandée — fichier overlay) :
```bash
docker compose -f compose.yaml -f compose.prod.yaml up -d --build
```

#### Option B (Via le profil prod) :
```bash
docker compose --profile prod up -d --build
```

---

## 5. Détail technique du Dockerfile et de Nginx

### Dockerfile multi-stage (`frontend/Dockerfile`)
1. **Étape 1 (Build)** :
   - Image : `node:22-alpine`.
   - Gestionnaire : `pnpm@11.19.0` via Corepack officiel.
   - Cache optimisé : copie préalable de `package.json`, `pnpm-lock.yaml`, `pnpm-workspace.yaml` suivie de `pnpm install --frozen-lockfile`.
   - Compilation : `pnpm run build` produisant le bundle optimisé dans `dist/wwtrading/browser`.
2. **Étape 2 (Runtime)** :
   - Image : `nginx:1.27-alpine` (légère, sécurisée, faible empreinte mémoire).
   - Fichiers copiés dans `/usr/share/nginx/html`.
   - Script `/docker-entrypoint.d/40-runtime-config.sh` exécuté au boot pour instancier la configuration.
   - Healthcheck intégré : `wget --spider http://127.0.0.1:80/healthz`.

### Caractéristiques de `frontend/nginx.conf`
- **Routage SPA** : `try_files $uri $uri/ /index.html` redirigeant toutes les routes Angular vers l'application sans erreur 404.
- **Reverse Proxy API** :
  ```nginx
  location /api/ {
      proxy_pass http://api-gateway:8080/api/;
      proxy_http_version 1.1;
      proxy_set_header Host $host;
      proxy_set_header X-Real-IP $remote_addr;
      proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
      proxy_set_header X-Forwarded-Proto $scheme;
      proxy_set_header X-Forwarded-Host $host;
      proxy_set_header X-Forwarded-Port $server_port;
      proxy_set_header Upgrade $http_upgrade;
      proxy_set_header Connection $connection_upgrade;
  }
  ```
- **Reverse Proxy Keycloak** :
  - `/realms/` et `/resources/` sont relayés vers `http://keycloak:8080`, éliminant les restrictions cross-origin et simplifiant la configuration SSO.
- **Politique de cache optimisée** :
  - `index.html` et `runtime-config.json` : `Cache-Control: "no-store, no-cache, must-revalidate, max-age=0"`.
  - Fichiers hachés (`*.js`, `*.css`, images, polices) : `Cache-Control: "public, max-age=31536000, immutable"`.
- **Compression Gzip** : Activée pour le HTML, JS, CSS, JSON, XML, SVG avec niveau de compression 6.
- **En-têtes de sécurité HTTP** : `X-Frame-Options SAMEORIGIN`, `X-Content-Type-Options nosniff`, `X-XSS-Protection 1; mode=block`, `Referrer-Policy strict-origin-when-cross-origin`.

---

## 6. Configuration 12-Factor : `runtime-config.json` dynamique

Angular lit sa configuration au démarrage via `/config/runtime-config.json`.
Grâce au script d'initialisation `/docker-entrypoint.d/40-runtime-config.sh`, l'image Docker n'a **jamais besoin d'être recompilée** lors d'un changement de domaine ou d'adresse d'API.

Variables injectables dans l'environnement Docker :
- `API_BASE_URL` : En production, laisser vide `""`. Les appels Angular `${this.config.apiBaseUrl}/api/...` deviennent des chemins relatifs `/api/...` routés directement par Nginx.
- `SITE_URL` : URL canonique du site (ex. `https://wwtrading.ma`).
- `TRACKING_BASE_URL` : URL de suivi public des commandes (ex. `https://wwtrading.ma`).
- `KEYCLOAK_URL` : URL publique de Keycloak (ex. `https://wwtrading.ma`).
- `KEYCLOAK_REALM` : Realm Keycloak (défaut : `wwtrading`).
- `KEYCLOAK_CLIENT_ID` : Client ID public (défaut : `wwtrading-web`).

---

## 7. Guide de déploiement Azure VM (DNS & SSL)

### 1. Configuration Réseau Azure (NSG - Network Security Group)
Dans le portail Azure, sur le groupe de sécurité réseau associé à la VM :
- Autoriser la règle de sécurité de trafic entrant **Port 80 (HTTP)**.
- Autoriser la règle de sécurité de trafic entrant **Port 443 (HTTPS)**.
- Conserver le port **22 (SSH)** restreint à vos adresses IP d'administration.

### 2. Configuration DNS
Chez votre registrar DNS (ex. Gandi, OVH, Cloudflare, Namecheap) :
- Enregistrement `A` : `@` -> `IP_PUBLIQUE_AZURE_VM`
- Enregistrement `A` (ou `CNAME`) : `www` -> `IP_PUBLIQUE_AZURE_VM`

### 3. Obtention du certificat SSL (Let's Encrypt / Certbot)
Une fois le domaine pointant sur l'IP de la machine virtuelle :
```bash
# Sur la VM Azure
sudo apt update && sudo apt install -y certbot

# Arrêter temporairement le port 80 ou utiliser le mode webroot :
sudo certbot certonly --standalone -d wwtrading.ma -d www.wwtrading.ma
```
Les certificats seront générés dans `/etc/letsencrypt/live/wwtrading.ma/`.

### 4. Activation HTTPS dans Nginx
Copier le template préparé `frontend/nginx.ssl.conf.template` vers `frontend/nginx.conf` ou monter le certificat dans le conteneur :
- Dans `compose.prod.yaml`, ajouter le volume de montage des certificats :
  ```yaml
  volumes:
    - /etc/letsencrypt:/etc/letsencrypt:ro
  ```
- Remplacer `${DOMAIN_NAME}` par `wwtrading.ma` dans la configuration.
- Relancer avec `docker compose -f compose.yaml -f compose.prod.yaml up -d`.

---

## 8. Garantie d'intégrité et sécurité des données

- **Aucun volume Docker n'a été altéré ou supprimé** : `order-data`, `order-transfer-proofs-data`, `user-data`, `product-data`, `keycloak-data` et tous les autres volumes restent intacts.
- **Aucune commande destructive** (`down -v`, `volume prune`, `system prune`) n'a été exécutée.
- La sécurité de restauration `ENABLE_BACKUP_RESTORE=false` est conservée par défaut dans `.env`.
- Le port frontend en développement local reste `4201`.


# WWTRADING — Rapport de Nettoyage des Fichiers Temporaires Approuvés

**Date d'exécution :** 03 Novembre 2026 à 14:09:36 UTC  
**Statut :** SUCCÈS COMPLET — NETTOYAGE EFFECTUÉ ET INTÉGRITÉ 100% CONFIRMÉE  
**Espace disque récupéré :** **204.39 Mo** (214 317 335 octets sur 64 fichiers)

---

## 1. Vérification Préalable Obligatoire

Avant toute suppression, l'ensemble des conditions de sécurité a été audité et validé :

1. **Présence des 3 dépôts de sauvegarde :**
   - `backups/full-backup-20261103-1020/` (16 fichiers) — **Présent et intègre**
   - `backups/safety-backup-20261103-132530/` (25 fichiers) — **Présent et intègre**
   - `backups/docker-export-20261103-134923/` (25 fichiers) — **Présent et intègre**
2. **Absence de références dans le code et l'infrastructure :**
   - Recherche textuelle (`git grep`) confirmant l'absence de toute référence aux fichiers ciblés dans `compose.yaml`, `compose.prod.yaml`, `scripts/`, `.github/`, `backend/`, et `frontend/`.
3. **Protection des sous-répertoires `tmp/` :**
   - Les dossiers `tmp/jarx/`, `tmp/kc-base/`, et `tmp/pdfs/` ont été strictement préservés conformément à la consigne.

---

## 2. Inventaire Exhaustif des Fichiers Supprimés (64 fichiers)

### A. Racine du projet (4 fichiers — 363.9 Ko)

| Fichier | Taille | Description |
|:---|---:|:---|
| `tmp-mvn-err.log` | 888 o | Journal d'erreur d'un ancien build Maven local |
| `tmp-mvn-out.log` | 27 627 o | Journal de sortie d'un ancien build Maven local |
| `qa-kc-390.png` | 120 228 o | Capture d'écran ponctuelle de test responsive Keycloak |
| `qa-kc-desktop.png` | 215 628 o | Capture d'écran ponctuelle de test bureau Keycloak |

### B. Builds autonomes et gros binaires obsolètes dans `tmp/` (2 fichiers — 211.9 Mo)

| Fichier | Taille | Description |
|:---|---:|:---|
| `tmp/notif-app.jar` | 107 607 758 o | Ancien JAR autonome de notification-service compilé en septembre |
| `tmp/product-service.jar` | 104 294 910 o | Ancien JAR autonome de product-service compilé en octobre |

### C. Fichiers de session JWT temporaires expirés (2 fichiers — 2.3 Ko)

| Fichier | Taille | Note de sécurité |
|:---|---:|:---|
| `tmp/admin.jwt` | 1 043 o | Jeton de session de test expiré (supprimé sans affichage du contenu) |
| `tmp/client2.jwt` | 1 289 o | Jeton de session de test expiré (supprimé sans affichage du contenu) |

### D. Captures d'écran temporaires dans `tmp/` (11 fichiers — 1.54 Mo)

- `tmp/kc-account.png` (17.6 Ko)
- `tmp/kc-login-390.png` (24.3 Ko)
- `tmp/kc-login-error.png` (192.7 Ko)
- `tmp/kc-login-final.png` (188.1 Ko)
- `tmp/kc-login.png` (187.4 Ko)
- `tmp/kc-register-filled.png` (180.1 Ko)
- `tmp/kc-register.png` (185.3 Ko)
- `tmp/kc-reset-sent.png` (190.9 Ko)
- `tmp/kc-reset.png` (181.5 Ko)
- `tmp/orders.png` (191.6 Ko)
- `tmp/shot-login-1440.png` (181.0 Ko)

### E. Scripts de test scratch, logs et réponses HTTP dans `tmp/` (45 fichiers — ~350 Ko)

- **Logs et états :** `tmp/notif-logs.txt` (200 Ko), `tmp/mh.json` (14.5 Ko).
- **Réponses curl et dumps HTML :** `login.html`, `login2.html`, `login3.html`, `login4.html`, `p.html`, `r.html`, `r1.html`, `reg2.html`, `reg3.html`, `reg4.html`, `reg6.html`, `reg7.html`, `cj.txt`, `cj2.txt`, `cj3.txt`, `cj4.txt`, `reg.url`.
- **Scripts de scratch temporaires :** `adminpw.js`, `admintok.js`, `gettok.js`, `check-login.sh`, `check-pages.sh`, `check-reg*.sh` (8 fichiers), `dbg.sh`, `dbg2.sh`, `e2e-*.mjs` (5 fichiers), `native-register.mjs`, `qa.mjs`, `test-account-api.sh`, `verify-*.mjs` (4 fichiers).

---

## 3. Respect des Exclusions Strictes

- **Aucun code source supprimé** (`backend/` et `frontend/` intacts à 100%).
- **Aucun fichier Compose ou Dockerfile modifié ou supprimé.**
- **Fichiers `.env` et `.env.example` strictement préservés.**
- **Toutes les migrations Flyway préservées.**
- **Tous les volumes Docker nommés et anonymes (Kafka) préservés.**
- **Tous les conteneurs et images Docker conservés sans modification.**
- **Toutes les données de base de données, bordereaux de virement et images produits intacts.**

---

## 4. Vérification Post-Nettoyage (Re-vérification SHA-256)

Après l'exécution du nettoyage, l'intégrité de l'ensemble des dépôts de sauvegarde a été immédiatement recalculée et comparée à leurs manifestes respectifs :

| Dépôt de Sauvegarde | Nombre de Fichiers | Vérification SHA-256 | Résultat |
|:---|:---:|:---:|:---:|
| `backups/full-backup-20261103-1020` | 16 | 16 / 16 fichiers recalculés | **100% CONFORME** |
| `backups/safety-backup-20261103-132530` | 25 | 25 / 25 fichiers recalculés | **100% CONFORME** |
| `backups/docker-export-20261103-134923` | 25 | 25 / 25 fichiers recalculés | **100% CONFORME** |

**Contrôle Git :** `git status` confirme qu'aucun fichier suivi par git n'a été altéré ou supprimé.

---

## 5. Synthèse

- **Total fichiers supprimés :** 64
- **Espace disque libéré :** 204.39 Mo (214 317 335 octets)
- **Environnement Docker :** Inchangé, 25 conteneurs actifs et sains.
- **Opération terminée :** Conforme aux consignes, aucun nettoyage Docker ni opération de base de données n'a été déclenché.


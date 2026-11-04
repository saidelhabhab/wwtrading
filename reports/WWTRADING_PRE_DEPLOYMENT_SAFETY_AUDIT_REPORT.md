# WWTRADING — Pre-Deployment CI/CD Safety Audit Report

**Date:** 2026-11-04  
**Repository:** `https://github.com/saidelhabhab/wwtrading`  
**Commits Audited:** `ae1c239` (remote) and `a423cf4` (local with fingerprint & health probe)  
**Target Azure VM:** `74.248.151.43`  
**SSH User:** `saidtoni`  
**Deployment Status:** **HELD / PENDING USER APPROVAL (Zero deployments triggered)**

---

## 1. Executive Summary

A comprehensive pre-deployment safety audit was performed across `.github/workflows/ci.yml`, `scripts/deploy-azure-vm.sh`, `compose.yaml`, `compose.prod.yaml`, `frontend/server.mjs`, and the target Azure VM host.

### Key Audit Findings:
- **Zero Existing Data on Azure VM:** The Azure Linux VM (`74.248.151.43`) was inspected via live SSH: it contains **0 Docker containers, 0 Docker volumes, and no `/home/saidtoni/wwtrading` directory**. The 25 healthy containers and 15 `wwtest_*` database volumes exist **exclusively on the local development workstation**.
- **Destructive Command Check:** Verified that `docker compose down -v`, `docker volume prune`, `docker system prune`, and database drop/truncate commands are **100% absent** from all workflows, scripts, and Compose files.
- **Strict Gating:** Deployment cannot run unless all 5 required CI checks pass (`secret-scan`, `backend`, `frontend`, `filesystem-scan`, `docker-validation`). Pull requests **never** trigger deployment (`if: github.event_name == 'push'`).
- **Cryptographic SSH Verification:** Host key verification is strictly pinned using the exact server host key fingerprint (`SHA256:f6EiD3A1BO29C+IQoXI0LnFkx4Lzz5ZnovytaQs1J5g`).
- **Same-Origin Port 80 Strategy:** Node.js frontend server handles SPA static routing and reverse proxies `/api/` and `/realms/` over the internal Docker network `platform`. Public internet access to ports 8080 and 8180 is not required.

---

## 2. Detailed Audit of the 14 Safety Requirements

| # | Safety Requirement | Audit Result | Evidence / Implementation Details |
| :--- | :--- | :--- | :--- |
| **1** | Deployment runs only after all required CI jobs succeed | **PASS** | `needs: [secret-scan, backend, frontend, filesystem-scan, docker-validation]` in `.github/workflows/ci.yml`. If any job fails, `deploy` is skipped. |
| **2** | Pull requests never trigger deployment | **PASS** | `if: github.ref == 'refs/heads/main' && github.event_name == 'push'` strictly rejects PR events. |
| **3** | SSH uses GitHub Secrets and strict host key verification | **PASS** | `host`, `username`, and `key` read from GitHub Secrets. `fingerprint: "SHA256:f6EiD3A1BO29C+IQoXI0LnFkx4Lzz5ZnovytaQs1J5g"` enforces cryptographic identity. |
| **4** | Deployment targets correct VM and directory | **PASS** | Target VM: `74.248.151.43`, Target Directory: `/home/saidtoni/wwtrading`. |
| **5** | Docker Compose uses intended production configuration | **PASS** | Executes `docker compose --profile prod up -d --build --remove-orphans` using `compose.yaml`. |
| **6** | No `down -v`, `volume prune`, or `system prune` | **PASS** | Audited all scripts: zero occurrences of volume deletion or prune commands. |
| **7** | Existing database volumes never deleted or recreated | **PASS** | `scripts/deploy-azure-vm.sh` executes `docker volume inspect <vol>` before creation; existing volumes are preserved intact. |
| **8** | Existing external volume names and mappings preserved | **PASS** | All 15 persistent volumes (`wwtest_keycloak-data`, `wwtest_user-data`, `wwtest_product-data`, etc.) match `compose.yaml` exactly. |
| **9** | Deployment does not overwrite production `.env` | **PASS** | Guarded by `if [ ! -f .env ]`; on all subsequent deployments, existing `.env` is preserved without modification. |
| **10** | Database migrations reviewed; no data reset | **PASS** | All microservices configured with `ddl-auto: validate` and Flyway enabled; schema changes are additive and never drop tables. |
| **11** | Frontend accessible through port 80 via Node same-origin proxy | **PASS** | Node.js production server listens on port 3000 mapped to host port 80; proxies `/api/` and `/realms/` internally. |
| **12** | API Gateway (8080) and Keycloak (8180) not exposed publicly | **PASS** | External browser connects exclusively to port 80; ports 8080 and 8180 are restricted by Azure NSG and used internally. |
| **13** | Deployment waits for health checks & verifies product API | **PASS** | Probes `/healthz` (attempt loop 1-12) and tests `GET http://127.0.0.1:80/api/products` (attempt loop 1-20) before finishing. |
| **14** | Failed deployments do not destroy persistent data | **PASS** | Script uses `set -euo pipefail`; failures halt execution immediately without affecting persistent Docker volumes on disk. |

---

## 3. Azure VM Host Audit & Data Status

A live read-only inspection was executed on `74.248.151.43` as `saidtoni`:

```bash
=== DOCKER CONTAINERS ON VM ===
CONTAINER ID   IMAGE     COMMAND   CREATED   STATUS    PORTS     NAMES
(0 containers)

=== DOCKER VOLUMES ON VM ===
DRIVER    VOLUME NAME
(0 volumes)

=== DIRECTORY /home/saidtoni ===
No /home/saidtoni/wwtrading found
```

### Critical Distinction:
- **Local Machine:** Contains 25 running containers, 15 persistent `wwtest_*` volumes, and live product catalog data.
- **Azure VM (`74.248.151.43`):** Is a **clean, freshly provisioned host** with Docker Engine 29.9.3, Compose v5.7.0, and 4 GB swap space. It contains **no containers, no volumes, and no database data yet**.
- **No data will be overwritten on the VM**, and local database backups will NOT be copied to the VM automatically.

---

## 4. Exact Deployment Commands That Will Execute

When the deployment job runs on the VM, it will execute:

```bash
# Step 1: Clone or sync repository in ~/wwtrading
mkdir -p ~/wwtrading
cd ~/wwtrading
if [ ! -d .git ]; then
  git clone https://github.com/saidelhabhab/wwtrading.git .
else
  git fetch origin
  git checkout main
  git reset --hard origin/main
fi

# Step 2: Make deployment script executable and run it
chmod +x scripts/deploy-azure-vm.sh
./scripts/deploy-azure-vm.sh
```

Inside `scripts/deploy-azure-vm.sh`:
```bash
# 1. Idempotently create 15 persistent volumes if not already existing:
for vol in "${VOLUMES[@]}"; do
  if ! docker volume inspect "${vol}" >/dev/null 2>&1; then
    docker volume create "${vol}"
  fi
done

# 2. On first run only: create production .env from .env.example with secure random passwords:
if [ ! -f .env ]; then
  cp .env.example .env
  # updates FRONTEND_PUBLIC_URL=http://74.248.151.43, etc.
fi

# 3. Non-destructively start all containers with the production profile:
docker compose --profile prod up -d --build --remove-orphans

# 4. Wait for Node.js frontend healthcheck:
curl -sf http://127.0.0.1:80/healthz

# 5. Wait for API Gateway & Product Service route:
curl -s -o /dev/null -w "%{http_code}" http://127.0.0.1:80/api/products
```

---

## 5. Docker Compose Project & Volume Mappings

- **Project Name:** `wwtrading` (defined in `compose.yaml`).
- **Internal Network:** `platform` (Docker bridge network).
- **Persistent Volume Mappings:**
  1. `keycloak-data` -> `wwtest_keycloak-data`
  2. `user-data` -> `wwtest_user-data`
  3. `maintenance-data` -> `wwtest_maintenance-data`
  4. `support-data` -> `wwtest_support-data`
  5. `notification-data` -> `wwtest_notification-data`
  6. `quotation-data` -> `wwtest_quotation-data`
  7. `order-data` -> `wwtest_order-data`
  8. `cart-data` -> `wwtest_cart-data`
  9. `wishlist-data` -> `wwtest_wishlist-data`
  10. `product-data` -> `wwtest_product-data`
  11. `support-attachments-data` -> `wwtest_support-attachments-data`
  12. `product-images-data` -> `wwtest_product-images-data`
  13. `maintenance-attachments-data` -> `wwtest_maintenance-attachments-data`
  14. `order-transfer-proofs-data` -> `wwtest_order-transfer-proofs-data`
  15. `kafka-data` -> `wwtest_kafka-data`

---

## 6. Required GitHub Secrets Verification

The deployment job requires these secrets under **Settings > Secrets and variables > Actions**:

| Secret Name | Expected Value | Status |
| :--- | :--- | :--- |
| `AZURE_VM_HOST` | `74.248.151.43` | User must configure in GitHub |
| `AZURE_VM_USER` | `saidtoni` | User must configure in GitHub |
| `AZURE_VM_SSH_KEY` | Content of `C:\Users\hp\Downloads\vmforwwtrading_key.pem` | User must configure in GitHub |

---

## 7. Confirmation of Deployment Status

- **Zero deployments have been triggered.**
- The Azure VM remains in a clean, idle state.
- Awaiting user approval to push the final commit and trigger the pipeline.


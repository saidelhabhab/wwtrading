#!/usr/bin/env bash
set -euo pipefail

# ==============================================================================
# WWTRADING — Idempotent Azure VM Deployment Script
# Target IP: 74.248.151.43
# ==============================================================================

DEPLOY_DIR="${HOME}/wwtrading"
VM_PUBLIC_IP="74.248.151.43"

echo "=== [WWTRADING-DEPLOY] Starting deployment at $(date) ==="

# 1. Ensure deployment directory exists
mkdir -p "${DEPLOY_DIR}"
cd "${DEPLOY_DIR}"

# 2. Ensure persistent Docker volumes exist (Mode B Safety)
VOLUMES=(
  "wwtest_keycloak-data"
  "wwtest_user-data"
  "wwtest_maintenance-data"
  "wwtest_support-data"
  "wwtest_notification-data"
  "wwtest_quotation-data"
  "wwtest_order-data"
  "wwtest_cart-data"
  "wwtest_wishlist-data"
  "wwtest_product-data"
  "wwtest_support-attachments-data"
  "wwtest_product-images-data"
  "wwtest_maintenance-attachments-data"
  "wwtest_order-transfer-proofs-data"
  "wwtest_kafka-data"
)

echo "=== [WWTRADING-DEPLOY] Checking persistent Docker volumes ==="
for vol in "${VOLUMES[@]}"; do
  if ! docker volume inspect "${vol}" >/dev/null 2>&1; then
    echo "Creating persistent volume: ${vol}"
    docker volume create "${vol}"
  else
    echo "Persistent volume exists: ${vol}"
  fi
done

# 3. Setup production environment file (.env) if missing
if [ ! -f .env ]; then
  echo "=== [WWTRADING-DEPLOY] Initializing production .env from .env.example ==="
  cp .env.example .env
  # Update environment variables for production VM
  sed -i "s|FRONTEND_PUBLIC_URL=.*|FRONTEND_PUBLIC_URL=http://${VM_PUBLIC_IP}|g" .env
  sed -i "s|TRACKING_BASE_URL=.*|TRACKING_BASE_URL=http://${VM_PUBLIC_IP}|g" .env
  sed -i "s|KEYCLOAK_PUBLIC_URL=.*|KEYCLOAK_PUBLIC_URL=http://${VM_PUBLIC_IP}|g" .env
  sed -i "s|ALLOWED_ORIGINS=.*|ALLOWED_ORIGINS=http://${VM_PUBLIC_IP},http://localhost:4201|g" .env
  sed -i "s|FRONTEND_HTTP_PORT=.*|FRONTEND_HTTP_PORT=80|g" .env
  sed -i "s|FRONTEND_API_BASE_URL=.*|FRONTEND_API_BASE_URL=|g" .env
  # Generate random strong passwords if CHANGE_ME is present
  sed -i "s|CHANGE_ME_local_keycloak_admin|$(openssl rand -hex 16)|g" .env
  sed -i "s|CHANGE_ME_local_keycloak_db|$(openssl rand -hex 16)|g" .env
  sed -i "s|CHANGE_ME_local_user_db|$(openssl rand -hex 16)|g" .env
  sed -i "s|CHANGE_ME_local_maintenance_db|$(openssl rand -hex 16)|g" .env
  sed -i "s|CHANGE_ME_local_support_db|$(openssl rand -hex 16)|g" .env
  sed -i "s|CHANGE_ME_local_notification_db|$(openssl rand -hex 16)|g" .env
  sed -i "s|CHANGE_ME_local_quotation_db|$(openssl rand -hex 16)|g" .env
  sed -i "s|CHANGE_ME_local_order_db|$(openssl rand -hex 16)|g" .env
  sed -i "s|CHANGE_ME_local_cart_db|$(openssl rand -hex 16)|g" .env
  sed -i "s|CHANGE_ME_local_wishlist_db|$(openssl rand -hex 16)|g" .env
  sed -i "s|CHANGE_ME_local_product_db|$(openssl rand -hex 16)|g" .env
  chmod 600 .env
  echo "Production .env created and secured with 600 permissions."
else
  echo "=== [WWTRADING-DEPLOY] Existing .env found, preserving configuration ==="
fi

# 4. Build and start containers non-destructively
echo "=== [WWTRADING-DEPLOY] Deploying containers with docker compose --profile prod ==="
docker compose --profile prod up -d --build --remove-orphans

# 5. Health checks
echo "=== [WWTRADING-DEPLOY] Performing health verification on port 80 ==="
for i in {1..12}; do
  if curl -sf http://127.0.0.1:80/healthz >/dev/null 2>&1; then
    echo "Frontend Node.js health check PASSED on port 80."
    break
  fi
  echo "Waiting for frontend to become healthy (attempt $i/12)..."
  sleep 5
done

echo "=== [WWTRADING-DEPLOY] Deployment completed successfully! ==="
docker compose ps

#!/bin/sh
set -e

CONFIG_FILE="/usr/share/nginx/html/config/runtime-config.json"
mkdir -p /usr/share/nginx/html/config

# Environment variables with production-ready defaults
API_BASE_URL="${API_BASE_URL:-}"
SITE_URL="${SITE_URL:-https://wwtrading.ma}"
TRACKING_BASE_URL="${TRACKING_BASE_URL:-}"
GOOGLE_SITE_VERIFICATION="${GOOGLE_SITE_VERIFICATION:-}"
GOOGLE_ANALYTICS_MEASUREMENT_ID="${GOOGLE_ANALYTICS_MEASUREMENT_ID:-}"
KEYCLOAK_URL="${KEYCLOAK_URL:-https://wwtrading.ma}"
KEYCLOAK_REALM="${KEYCLOAK_REALM:-wwtrading}"
KEYCLOAK_CLIENT_ID="${KEYCLOAK_CLIENT_ID:-wwtrading-web}"

cat <<EOF > "$CONFIG_FILE"
{
  "apiBaseUrl": "$API_BASE_URL",
  "siteUrl": "$SITE_URL",
  "trackingBaseUrl": "$TRACKING_BASE_URL",
  "googleSiteVerification": "$GOOGLE_SITE_VERIFICATION",
  "googleAnalyticsMeasurementId": "$GOOGLE_ANALYTICS_MEASUREMENT_ID",
  "keycloak": {
    "url": "$KEYCLOAK_URL",
    "realm": "$KEYCLOAK_REALM",
    "clientId": "$KEYCLOAK_CLIENT_ID"
  }
}
EOF

echo "[WWTRADING] runtime-config.json generated successfully:"
cat "$CONFIG_FILE"


#!/bin/bash
# Applies WWTRADING realm configuration that cannot be expressed in the
# standard realm import JSON (user profile schema) and enforces settings on
# an already-imported realm. Safe to run on every container start (idempotent).
set -euo pipefail

KC_SERVER="${KC_URL:-http://keycloak:8080}"
KC_ADMIN_USER="${KC_BOOTSTRAP_ADMIN_USERNAME:?Set KC_BOOTSTRAP_ADMIN_USERNAME}"
KC_ADMIN_PASSWORD="${KC_BOOTSTRAP_ADMIN_PASSWORD:?Set KC_BOOTSTRAP_ADMIN_PASSWORD}"
KC_REALM="wwtrading"
KCADM="/opt/keycloak/bin/kcadm.sh"

# Authenticate as the bootstrap admin.
"$KCADM" config credentials \
  --server "$KC_SERVER" --realm master \
  --user "$KC_ADMIN_USER" --password "$KC_ADMIN_PASSWORD" >/dev/null

# 1. Themes + login UX (idempotent on both imported and persistent realms).
"$KCADM" update "realms/${KC_REALM}" \
  -s loginTheme=wwtrading \
  -s accountTheme=wwtrading \
  -s emailTheme=wwtrading \
  -s registrationEmailAsUsername=true \
  -s rememberMe=true \
  -s internationalizationEnabled=true \
  -s 'supportedLocales=["fr","en"]' \
  -s defaultLocale=fr



# 2. User profile: phone is mandatory and validated (Moroccan-friendly format).
"$KCADM" update "users/profile" -r "$KC_REALM" -f - <<'JSON'
{
  "attributes": [
    {
      "name": "username",
      "displayName": "${username}",
      "validations": {
        "length": { "min": 3, "max": 255 },
        "username-prohibited-characters": {},
        "up-username-not-idn-homograph": {}
      },
      "permissions": { "view": ["admin", "user"], "edit": ["admin", "user"] },
      "multivalued": false
    },
    {
      "name": "email",
      "displayName": "${email}",
      "validations": { "email": {}, "length": { "max": 255 } },
      "required": { "roles": ["user"] },
      "permissions": { "view": ["admin", "user"], "edit": ["admin", "user"] },
      "multivalued": false
    },
    {
      "name": "firstName",
      "displayName": "${firstName}",
      "validations": {
        "length": { "max": 255 },
        "person-name-prohibited-characters": {}
      },
      "required": { "roles": ["user"] },
      "permissions": { "view": ["admin", "user"], "edit": ["admin", "user"] },
      "multivalued": false
    },
    {
      "name": "lastName",
      "displayName": "${lastName}",
      "validations": {
        "length": { "max": 255 },
        "person-name-prohibited-characters": {}
      },
      "required": { "roles": ["user"] },
      "permissions": { "view": ["admin", "user"], "edit": ["admin", "user"] },
      "multivalued": false
    },
    {
      "name": "phone",
      "displayName": "${phone}",
      "validations": {
        "length": { "min": 8, "max": 20 },
        "pattern": { "pattern": "^\\+?[0-9][0-9\\s\\-]{7,16}$" }
      },
      "annotations": { "inputType": "tel" },
      "required": { "roles": ["user"] },
      "permissions": { "view": ["admin", "user"], "edit": ["admin", "user"] },
      "multivalued": false
    }
  ],
  "groups": [
    {
      "name": "user-metadata",
      "displayHeader": "User metadata",
      "displayDescription": "Attributes, which refer to user metadata"
    }
  ]
}
JSON

# 3. Built-in client scopes removed by the realm import (required by the Account Console).
create_scope () {
  local name="$1"
  if ! "$KCADM" get client-scopes -r "$KC_REALM" 2>/dev/null | grep -q "\"name\" : \"${name}\""; then
    "$KCADM" create "client-scopes" -r "$KC_REALM" -f - <<JSON
$(sed -n "/^#@scope:${name}/,/^#@end/p" "$0" | sed '1d;$d')
JSON
  fi
}

create_scope "profile"
create_scope "email"
create_scope "roles"
create_scope "web-origins"
create_scope "account-dedicated"

"$KCADM" update "realms/${KC_REALM}" -s 'defaultDefaultClientScopes=["profile","email","roles","web-origins","account-dedicated","wwtrading-identity","wwtrading-api-audience","offline_access"]'

# Ensure email verified mapper exists on wwtrading-identity client scope
IDENTITY_SCOPE_ID=$("$KCADM" get client-scopes -r "$KC_REALM" 2>/dev/null | grep -B 2 '"name" : "wwtrading-identity"' | grep '"id"' | head -1 | awk -F'"' '{print $4}' || true)
if [[ -n "$IDENTITY_SCOPE_ID" ]]; then
  if ! "$KCADM" get "client-scopes/${IDENTITY_SCOPE_ID}/protocol-mappers/models" -r "$KC_REALM" 2>/dev/null | grep -q '"name" : "email verified"'; then
    "$KCADM" create "client-scopes/${IDENTITY_SCOPE_ID}/protocol-mappers/models" -r "$KC_REALM" -f - <<JSON || true
{
  "name": "email verified",
  "protocol": "openid-connect",
  "protocolMapper": "oidc-usermodel-property-mapper",
  "consentRequired": false,
  "config": {
    "user.attribute": "emailVerified",
    "claim.name": "email_verified",
    "jsonType.label": "boolean",
    "id.token.claim": "true",
    "access.token.claim": "true",
    "userinfo.token.claim": "true",
    "introspection.token.claim": "true",
    "lightweight.claim": "false"
  }
}
JSON
  fi
fi


# 4. Google IdP label and credentials on persistent realms. Credentials remain
# environment-only and are never printed or stored in this script.
"$KCADM" update "identity-provider/instances/google" -r "$KC_REALM" \
  -s displayName="Continue with Google" 2>/dev/null \
  || echo "WARN: google identity provider not present; skipping Google configuration"

if [[ -n "${GOOGLE_CLIENT_ID:-}" && -n "${GOOGLE_CLIENT_SECRET:-}" ]]; then
  "$KCADM" update "identity-provider/instances/google" -r "$KC_REALM" \
    -s "config.clientId=${GOOGLE_CLIENT_ID}" \
    -s "config.clientSecret=${GOOGLE_CLIENT_SECRET}"
else
  echo "WARN: GOOGLE_CLIENT_ID/GOOGLE_CLIENT_SECRET are not set; Google sign-in cannot be verified"
fi


echo "WWTRADING Keycloak configuration applied successfully."

exit 0

# Embedded JSON definitions are read by create_scope through sed. Keep them
# inside a no-op heredoc so Bash does not parse the JSON as shell syntax.
: <<'SCOPE_DEFINITIONS'
#@scope:profile
{
  "name": "profile",
  "description": "OpenID Connect built-in scope: profile",
  "protocol": "openid-connect",
  "attributes": { "include.in.token.scope": "true", "display.on.consent.screen": "true", "consent.screen.text": "${profileScopeConsentText}" },
  "protocolMappers": [
    { "name": "username", "protocol": "openid-connect", "protocolMapper": "oidc-usermodel-property-mapper", "consentRequired": false, "config": { "user.attribute": "username", "claim.name": "preferred_username", "jsonType.label": "String", "id.token.claim": "true", "access.token.claim": "true", "userinfo.token.claim": "true", "introspection.token.claim": "true", "lightweight.claim": "false" } },
    { "name": "given name", "protocol": "openid-connect", "protocolMapper": "oidc-usermodel-property-mapper", "consentRequired": false, "config": { "user.attribute": "firstName", "claim.name": "given_name", "jsonType.label": "String", "id.token.claim": "true", "access.token.claim": "true", "userinfo.token.claim": "true", "introspection.token.claim": "true", "lightweight.claim": "false" } },
    { "name": "family name", "protocol": "openid-connect", "protocolMapper": "oidc-usermodel-property-mapper", "consentRequired": false, "config": { "user.attribute": "lastName", "claim.name": "family_name", "jsonType.label": "String", "id.token.claim": "true", "access.token.claim": "true", "userinfo.token.claim": "true", "introspection.token.claim": "true", "lightweight.claim": "false" } },
    { "name": "full name", "protocol": "openid-connect", "protocolMapper": "oidc-full-name-mapper", "consentRequired": false, "config": { "id.token.claim": "true", "access.token.claim": "true", "userinfo.token.claim": "true", "introspection.token.claim": "true", "lightweight.claim": "false" } },
    { "name": "locale", "protocol": "openid-connect", "protocolMapper": "oidc-usermodel-attribute-mapper", "consentRequired": false, "config": { "user.attribute": "locale", "claim.name": "locale", "jsonType.label": "String", "id.token.claim": "true", "access.token.claim": "true", "userinfo.token.claim": "true", "introspection.token.claim": "true", "lightweight.claim": "false" } },
    { "name": "zoneinfo", "protocol": "openid-connect", "protocolMapper": "oidc-usermodel-attribute-mapper", "consentRequired": false, "config": { "user.attribute": "zoneinfo", "claim.name": "zoneinfo", "jsonType.label": "String", "id.token.claim": "true", "access.token.claim": "true", "userinfo.token.claim": "true", "introspection.token.claim": "true", "lightweight.claim": "false" } },
    { "name": "updated at", "protocol": "openid-connect", "protocolMapper": "oidc-usermodel-attribute-mapper", "consentRequired": false, "config": { "user.attribute": "timestamp", "claim.name": "updated_at", "jsonType.label": "long", "id.token.claim": "true", "access.token.claim": "true", "userinfo.token.claim": "true", "introspection.token.claim": "true", "lightweight.claim": "false" } }
  ]
}
#@end

#@scope:email
{
  "name": "email",
  "description": "OpenID Connect built-in scope: email",
  "protocol": "openid-connect",
  "attributes": { "include.in.token.scope": "true", "display.on.consent.screen": "true", "consent.screen.text": "${emailScopeConsentText}" },
  "protocolMappers": [
    { "name": "email", "protocol": "openid-connect", "protocolMapper": "oidc-usermodel-property-mapper", "consentRequired": false, "config": { "user.attribute": "email", "claim.name": "email", "jsonType.label": "String", "id.token.claim": "true", "access.token.claim": "true", "userinfo.token.claim": "true", "introspection.token.claim": "true", "lightweight.claim": "false" } },
    { "name": "email verified", "protocol": "openid-connect", "protocolMapper": "oidc-usermodel-property-mapper", "consentRequired": false, "config": { "user.attribute": "emailVerified", "claim.name": "email_verified", "jsonType.label": "boolean", "id.token.claim": "true", "access.token.claim": "true", "userinfo.token.claim": "true", "introspection.token.claim": "true", "lightweight.claim": "false" } }
  ]
}
#@end

#@scope:roles
{
  "name": "roles",
  "description": "OpenID Connect built-in scope: roles",
  "protocol": "openid-connect",
  "attributes": { "include.in.token.scope": "false", "display.on.consent.screen": "true", "consent.screen.text": "${rolesScopeConsentText}" },
  "protocolMappers": [
    { "name": "realm roles", "protocol": "openid-connect", "protocolMapper": "oidc-usermodel-realm-role-mapper", "consentRequired": false, "config": { "claim.name": "realm_access.roles", "jsonType.label": "String", "multivalued": "true", "userinfo.token.claim": "false", "id.token.claim": "true", "access.token.claim": "true", "introspection.token.claim": "true", "lightweight.claim": "false" } },
    { "name": "client roles", "protocol": "openid-connect", "protocolMapper": "oidc-usermodel-client-role-mapper", "consentRequired": false, "config": { "claim.name": "resource_access.${client_id}.roles", "jsonType.label": "String", "multivalued": "true", "userinfo.token.claim": "false", "id.token.claim": "true", "access.token.claim": "true", "introspection.token.claim": "true", "lightweight.claim": "false" } },
    { "name": "audience resolve", "protocol": "openid-connect", "protocolMapper": "oidc-audience-resolve-mapper", "consentRequired": false, "config": { "id.token.claim": "false", "access.token.claim": "true", "introspection.token.claim": "true", "lightweight.claim": "false" } }
  ]
}
#@end

#@scope:web-origins
{
  "name": "web-origins",
  "description": "OpenID Connect built-in scope: web-origins",
  "protocol": "openid-connect",
  "attributes": { "include.in.token.scope": "false", "display.on.consent.screen": "false" },
  "protocolMappers": [
    { "name": "allowed web origins", "protocol": "openid-connect", "protocolMapper": "oidc-allowed-origins-mapper", "consentRequired": false, "config": { "id.token.claim": "true", "access.token.claim": "true", "introspection.token.claim": "true", "lightweight.claim": "false" } }
  ]
}
#@end

#@scope:account-dedicated
{
  "name": "account-dedicated",
  "description": "account dedication scope",
  "protocol": "openid-connect",
  "attributes": { "include.in.token.scope": "false", "display.on.consent.screen": "false" },
  "protocolMappers": [
    { "name": "account audience", "protocol": "openid-connect", "protocolMapper": "oidc-audience-mapper", "consentRequired": false, "config": { "included.client.audience": "account", "id.token.claim": "false", "access.token.claim": "true", "introspection.token.claim": "true", "lightweight.claim": "false" } }
  ]
}
#@end
SCOPE_DEFINITIONS

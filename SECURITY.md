# Security

## Authentication and authorization

Keycloak is the OpenID Connect provider. The API Gateway validates JWT issuer
and signatures, applies a deny-by-default policy and permits only documented
public and health endpoints without authentication. Realm roles are ADMIN,
MANAGER, COMMERCIAL, TECHNICIAN and CLIENT.

The Gateway and User Service both validate signature, issuer, expiry and the
`wwtrading-api` audience before accepting a token.

Realm roles are extracted through `common-security` and restricted to the five
declared business roles. The User Service is deny-by-default and never stores
credentials.

## Secrets

- `.env` is ignored and must never be committed.
- `.env.example` contains fake local placeholders only.
- Production secrets belong in a secret manager, Docker secrets or Kubernetes
  Secrets, and in GitHub Actions Secrets for CI/CD.
- Logs must never contain tokens, Authorization headers, passwords or API keys.

## HTTP controls

- CORS uses an externalized allowlist.
- Stateless REST endpoints disable CSRF; browser session endpoints must not.
- HTTPS is terminated by the deployment ingress or reverse proxy.
- Request size limits, audience validation and correlation IDs are enforced.

## Reporting

Do not include credentials or customer data in vulnerability reports. Rotate
any exposed secret immediately and invalidate affected sessions.

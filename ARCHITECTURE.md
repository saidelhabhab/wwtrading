# Architecture

WWTRADING is a microservices platform. PHASE 3A introduces the authenticated
User Service bounded context.

## Boundaries

- Every future business service owns its database.
- No service may query another service's database.
- Synchronous communication uses versioned REST APIs.
- Shared modules contain contracts or security utilities, never business state.

## Planned data allocation

- PostgreSQL: users, orders, quotations, maintenance and technicians.
- MySQL: products, inventory and catalog data.
- Redis: cache, rate limits, temporary state and idempotency keys.

## Deployment model

Containers are stateless except for explicitly declared data services. Images
use multi-stage builds and a non-root runtime user. Kubernetes manifests are a
later deployment phase; all configuration is externalized now to keep the
services Kubernetes-ready.

## PHASE 3A data ownership

- `user-service` exclusively owns the `wwtrading_users` PostgreSQL database.
- Keycloak owns its separate PostgreSQL database and remains the identity
  authority; User Service never stores passwords.
See `docs/architecture/phase-2.md` for the PHASE 3A decisions.

# Phase 1 - Platform foundation

## Goal

Establish a runnable, secure foundation without generating unused business
services. This phase contains only components required to route, configure and
authenticate future services, plus the public Angular shell.

## Runtime flow

1. Keycloak authenticates users and issues JWT access tokens.
2. The API Gateway validates JWTs and enforces authenticated access by default.
3. Services register with Eureka when introduced.
4. Centralized non-secret configuration is served by Config Server.

## Security defaults

- Deny-by-default gateway policy outside explicit public and health endpoints.
- CORS origins are externalized.
- No client secret, password or API key is committed.
- Keycloak realm roles: ADMIN, MANAGER, COMMERCIAL, TECHNICIAN and CLIENT.
- Production TLS, secret storage and hostname configuration remain deployment
  concerns and must be supplied per environment.

## Data ownership

No business database is created in this phase. Future services will own their
database exclusively. Cross-service access will use REST or Kafka events only.

## Deferred to later phases

- Business services and shared event contracts
- PostgreSQL, MySQL and Redis consumers
- Kafka in KRaft mode and Kafka UI
- Zipkin, Grafana and production telemetry pipelines
- Kubernetes manifests
- AI provider integration and its secret configuration

## Known source-data decision

The supplied PDF and prompt disagree about the public company spelling and
telephone numbers. The public shell therefore uses the non-conflicting email
and Kenitra location until the business owner confirms the canonical values.

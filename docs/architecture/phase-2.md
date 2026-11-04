# PHASE 3A — USER SERVICE

## Scope

PHASE 3A introduces only the components required for authenticated user
profiles:

- `common-security` for audience validation and Keycloak realm role mapping;
- `user-service` with its own PostgreSQL database;
- Flyway-owned schema and MapStruct DTO/entity mappings;
- Keycloak JWT validation and allowlisted realm roles.

Product, inventory, cart, ordering and operational back-office domains remain
outside this phase.

## Request flow

1. User Service validates JWT signature, issuer, lifetime and API audience.
2. Only declared WWTRADING realm roles become Spring Security authorities.
3. `PUT /api/v1/users/me` synchronizes the Keycloak subject and verified email
   without ever receiving or storing a password.

## Persistence

`user-service` is the sole owner of `wwtrading_users`. Flyway owns the schema;
Hibernate is configured with `ddl-auto=validate`. DTO/entity transformations
use MapStruct with unmapped targets treated as compilation failures.

## Security decisions

- No Keycloak or database secret is committed.
- The API audience is explicitly added by a Keycloak client-scope mapper.
- User Service uses deny-by-default authorization.
- Only `ADMIN`, `MANAGER`, `COMMERCIAL`, `TECHNICIAN` and `CLIENT` claims become
  Spring Security authorities.

## Known limitations and next phase

- Local realm import does not overwrite a realm already stored in the Keycloak
  volume; existing development realms require a controlled migration.
- Frontend authentication, messaging, rate limiting and observability remain
  outside PHASE 3A.

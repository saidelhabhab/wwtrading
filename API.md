# API

## Gateway

Base URL in local development: `http://localhost:8080`.

- `GET /actuator/health` is public for health checks.
- `/api/public/**` is reserved for explicitly public endpoints.
- Every other route requires a valid WWTRADING realm JWT.

## User API v1

All User API operations require a bearer access token issued by the
`wwtrading` realm with audience `wwtrading-api`.

- `PUT /api/v1/users/me` idempotently creates or synchronizes the authenticated
  profile from trusted JWT claims.
- `GET /api/v1/users/me` returns the already synchronized profile.
- `PATCH /api/v1/users/me` updates `firstName`, `lastName` and/or `locale` using
  a validated JSON body. Missing fields are left unchanged.

The API never exposes JPA entities. Errors use RFC 9457 problem details, with a
field-level `errors` array for validation failures. The OpenAPI description is
available locally from `http://localhost:8081/v3/api-docs` and Swagger UI from
`http://localhost:8081/swagger-ui.html`.

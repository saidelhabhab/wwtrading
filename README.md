# WWTRADING Platform

Production-oriented B2B platform for printer sales, rental, maintenance,
consumables, quotations and technical support in Morocco.

## Current scope: PHASE 3A — USER SERVICE

- Angular 19 public shell with standalone components and lazy routes
- Spring Cloud Config Server
- Eureka Discovery Server
- Spring Cloud API Gateway secured as an OAuth2 resource server
- Keycloak realm with the WWTRADING roles
- Docker Compose development environment
- GitHub Actions build pipeline
- Shared JWT audience validation and Keycloak realm-role mapping
- User Service with PostgreSQL, Flyway and MapStruct
- JWT issuer, signature and audience validation in the gateway and User Service

Product, inventory, cart and ordering services remain intentionally deferred.

## Requirements

- Java 21
- Maven 3.9+ or Docker
- Node.js 20 or 22
- pnpm 10+
- Docker Desktop with Compose

## First run

1. Copy `.env.example` to `.env` and replace every example value.
2. Install and start the frontend:

   ```powershell
   cd frontend
   pnpm install
   pnpm start
   ```

3. Start the backend foundation:

   ```powershell
   docker compose up --build
   ```

Local endpoints:

- Frontend: http://localhost:4200
- API Gateway: http://localhost:8080
- Keycloak: http://localhost:8180
- Config Server: http://localhost:8888
- Eureka: http://localhost:8761
- User Service OpenAPI: http://localhost:8081/swagger-ui.html

## Build without a local Maven installation

```powershell
docker run --rm -v "${PWD}:/workspace" -w /workspace/backend maven:3.9.16-eclipse-temurin-21 mvn verify
```

See `docs/architecture/phase-2.md` for PHASE 3A architecture decisions.

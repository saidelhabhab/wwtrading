# Development

## Branches

Use `develop` as the integration branch and `feature/*`, `fix/*`, `hotfix/*`
or `release/*` branches. Changes reach `main` only through a reviewed pull
request with required checks.

## Local workflow

1. Copy `.env.example` to `.env` and replace example values.
2. Run `pnpm install` and `pnpm start` from `frontend`.
3. Run `docker compose up --build` from the repository root.
4. Before a pull request, run `pnpm build` and `mvn verify`.

Use conventional commits such as `feat(gateway): add product route` or
`security(auth): validate token audience`. Never disable failing tests to make
a build pass.

## Code rules

- Java 21, constructor injection, DTO boundaries and validation.
- MapStruct for DTO/entity mappings; unmapped targets fail compilation.
- Angular standalone components, strict typing and lazy routes.
- No entity exposure, hardcoded credentials or placeholder implementations
  presented as complete functionality.

# AGENTS.md

This file provides guidance to AI agents (e.g. Claude Code, opencode) when working with code in this repository.

## Project Overview

Conluz is an energy community management application built with Spring Boot 3. It manages community members, supply points, consumption data, production metrics from energy plants, and electricity prices. The application is API-driven with JWT authentication, uses PostgreSQL for relational data and InfluxDB for time-series data.

## Development Commands

### Build and Run
```bash
./gradlew build                 # Build the project
./gradlew bootRun               # Run the application (accessible at https://localhost:8443)
./gradlew clean build --info    # Clean build with detailed output
```

### Testing
```bash
./gradlew test                  # Run all tests (uses JUnit 5)
./gradlew test --tests ClassName  # Run a specific test class
```

Tests use Testcontainers for PostgreSQL and InfluxDB integration tests.

### Docker Deployment
`deploy/` holds a **sanitized reference example** (`docker-compose.example.yml`), not the
production deployment (see the "Deployment & infrastructure boundary" note below).

```bash
# From project root:
docker build -t conluz:1.0 -f Dockerfile .
cd deploy
cp .env.example .env                                        # then edit .env with your own values
docker compose -f docker-compose.example.yml up -d          # Start the core stack
docker compose -f docker-compose.example.yml up -d postgres # Start only PostgreSQL
docker compose -f docker-compose.example.yml up -d influxdb # Start only InfluxDB
docker stop conluz                                          # Stop the app
```

## Deployment & infrastructure boundary

This repository is **public and world-readable**. Environment-specific values — real
hostnames, filesystem paths, real service/community names, CUPS codes, backup schedules, and
any credential (JWT keys, DB/InfluxDB/MQTT passwords, `PGPASSWORD`, tokens) — **must never
enter this repo**. They live in the **private `conluz-infra` repository**, which is the single
source of truth for production topology and operational tooling (backups, restores, snapshots,
monitoring, reverse proxy, host configuration).

- `deploy/` here is a **sanitized reference example, not a mirror of any production setup**:
  `docker-compose.example.yml` + `.env.example` use `${VAR}`/placeholder values only, plus the
  two generic DB init scripts.
- Real secret values live only in gitignored `.env` files on the host; committed files use
  `${VAR}` interpolation and `*.env.example` templates. `.env`, `*.env`, `*.key`, `*.pem` are
  gitignored (but `*.env.example` is allowed).
- A **gitleaks `pre-commit` hook** is the backstop — install it once per clone
  (`pre-commit install`); see `docs/gitleaks.md`. If a real secret is ever found committed
  here, treat it as **compromised**: rotate it (a human decision), do not just delete the file
  (deletion does not remove it from history).

## Architecture

### Package Structure

The codebase follows **Hexagonal Architecture** (Ports and Adapters):

- **`domain/`**: Core business logic, pure Java classes
  - `admin/`: User, supply point, and plant management
  - `consumption/`: Consumption data from Datadis and other sources
  - `production/`: Production data from Huawei inverters and other sources
  - `price/`: Electricity price data management
  - `shared/`: Domain-level shared utilities

- **`infrastructure/`**: Adapters for external systems
  - Controllers (REST endpoints)
  - Repositories (JPA/InfluxDB implementations)
  - External integrations (Datadis, Huawei, Shelly)
  - `shared/`: Infrastructure-level shared components (security, DB config, jobs, i18n, etc.)

### Key Components

- **Authentication**: JWT-based with HMAC-SHA256, tokens contain user ID, role, expiration
- **Controllers**: REST endpoints in `infrastructure/*/` packages, documented with OpenAPI/Swagger
- **Services**: Business logic in `domain/*/` packages (e.g., `*Service.java`)
- **Repositories**: Interfaces in `domain/`, implementations in `infrastructure/`
- **Database Migrations**: Liquibase changesets in `src/main/resources/db/liquibase/`
- **Scheduled Jobs**: Quartz-based scheduled tasks enabled via `@EnableScheduling`

### Data Storage

1. **PostgreSQL**: Users, supplies, configuration (managed via Liquibase migrations)
2. **InfluxDB**: Time-series data for consumption, production, and prices with retention policies (1 month, 1 year, forever)

#### InfluxDB Schema

The time-series database stores consumption, production and price data in the measurements described in
[`docs/db/timeseries/influxdb/influxdb_schema.md`](docs/db/timeseries/influxdb/influxdb_schema.md), with
`docs/db/timeseries/influxdb/influxdb_schema.txt` holding the complete schema with sample data.

## Configuration

### Required Environment Variables

- `CONLUZ_JWT_SECRET_KEY`: JWT secret key (≥256 bits, HMAC-SHA compatible). Generate using `org.lucoenergia.conluz.infrastructure.shared.security.JwtSecretKeyGenerator`
- `SPRING_DATASOURCE_URL`: PostgreSQL connection (default: `jdbc:postgresql://localhost:5432/conluz_db`)

### Database Setup

For new installations and existing databases (PostgreSQL and InfluxDB setup scripts), see
[`docs/db/setup.md`](docs/db/setup.md).

## API Documentation

With the app running:
- OpenAPI spec: https://localhost:8443/api-docs
- Swagger UI: https://localhost:8443/api-docs/swagger-ui/index.html

## Git Workflow

- Main branch: `main`
- Feature branches: `feature/conluz-XXX` (where XXX is the issue number)
- Commit format: `[conluz-XXX] Your commit message`
- Merge strategy: Squash and merge to main
- Direct pushes to `main` are not allowed

## Code Standards

- Follow SOLID and Clean Code principles
- Code and comments must be in English
- Code should be self-explanatory with comments when additional explanation is needed
- **URL path parameter naming**: All REST API URL path segments that identify a resource MUST use the
  `{resourceId}` convention (e.g. `{supplyId}`, `{userId}`, `{plantId}`, `{communityId}`,
  `{sharingAgreementId}`). The bare `{id}` pattern is never used. The same name MUST be used
  consistently for the `@PathVariable` annotation value and the Java method parameter name. This
  ensures OpenAPI specs are self-documenting and eliminates ambiguity when multiple IDs appear in
  the same URL.
- All new code must have automated tests
- Architecture tests are enforced via ArchUnit (see `src/test/java/org/lucoenergia/conluz/architecture/`)
- When injecting beans, always use the interface. This also applies to integration tests
- When creating tests over services that has an interface, always use the name of the interface + "Test" for naming them
- **Never use `findAll().stream().findFirst()` in production code** to retrieve a single entity. This loads all rows into memory. Use a Spring Data derived query method that produces a `LIMIT 1` query instead — e.g., `findFirstBy()` or `findFirstByOrderByIdAsc()` in the JPA repository interface. This anti-pattern is only acceptable in test code where it avoids adding repository methods purely for test purposes.
- **JPA repositories and entities are internal infrastructure details and must never leak across layers.** Spring Data JPA repository interfaces (extending `JpaRepository`) and JPA entity classes (annotated with `@Entity`) may only be referenced within `infrastructure/` package tree. Repository implementation classes in `infrastructure/` are the sole layer where JPA/ORM types reside. Services and controllers must never receive or return JPA entities — entity mappers must convert between JPA entities and domain objects before crossing layer boundaries. Architecture tests (see `src/test/java/org/lucoenergia/conluz/architecture/JpaUsageArchTest.java`) enforce this via ArchUnit.
- **All `RepositoryDatabase` classes must be annotated with `@Transactional`.** Both read and write operations should declare intent explicitly: use `@Transactional(readOnly = true)` for read-only queries and `@Transactional` for write operations. This ensures consistent transaction boundaries across all database access. Architecture tests (see `src/test/java/org/lucoenergia/conluz/architecture/RepositoryTransactionalArchTest.java`) enforce this via ArchUnit.
- **`RepositoryDatabase` and `@Service` classes must not mix read-only and write transactional methods.** A class's transactional mode is declared once, at the class level: `@Transactional(readOnly = true)` if every method is read-only, or plain `@Transactional` if it contains writes. A method-level `@Transactional` is only allowed when it configures something other than `readOnly` (e.g. `propagation`, `isolation`, `timeout`, `rollbackFor`) — an override whose only effect is toggling `readOnly` away from the class default (including a bare `@Transactional` used to flip back to writable) is not allowed. A class that genuinely needs both modes must be split into separate classes by responsibility, following the existing `Get*`/`Create*`/`Update*`/`Delete*`/`Enable*`/`Disable*` naming convention (e.g. `GetSupplyRepositoryDatabase` vs `CreateSupplyRepositoryDatabase`), instead of mixing modes in one class. Architecture tests (see `RepositoryTransactionalArchTest.java` and `ServiceTransactionalArchTest.java`) enforce this via ArchUnit.
- **All authorization logic must live in the controller layer.** Access decisions are expressed via `@PreAuthorize` on controllers (delegating to the `@communityAccessGuard` bean); services and repositories must contain no access-control logic and must never call `CommunityAccessGuard` or throw `AccessDeniedException`. The only non-controller class allowed to reference `AccessDeniedException` is `ConluzAccessDeniedHandler` (the component that maps it to a 403 response). Architecture tests (see `src/test/java/org/lucoenergia/conluz/architecture/AuthorizationLocationArchTest.java`) enforce this via ArchUnit.
- **Controllers must be thin: no domain logic and no calls to the repository layer.** A controller may only (1) enforce authorization via `@PreAuthorize`, (2) bind and validate the request (path variables, `@Valid @RequestBody`), (3) delegate to a single domain service call, and (4) map the service result to the HTTP response. All business logic — conditional dispatch/branching, precondition checks (e.g. "is Datadis enabled for this community?"), iteration, orchestration, and any repository access — must live in a service (`domain/**` interface with its `infrastructure/**` `*Impl`). Controllers must never inject or call a repository (`*Repository`, `RepositoryDatabase`, `*RepositoryInflux`) directly; they depend only on service interfaces. If a controller needs data or a guard condition, add a service method for it rather than reaching into a repository or embedding an `if` that encodes a business rule. The repository-access half of this rule is enforced via ArchUnit (see `src/test/java/org/lucoenergia/conluz/architecture/ControllerRepositoryAccessArchTest.java`).
- **Response schema annotations: every `*Response` field must declare required-ness, and nullable fields use `types`, never `nullable`.** Jackson's default null-inclusion in this app is `ALWAYS` (verified empirically — no `spring.jackson.default-property-inclusion`, no `@JsonInclude`, no custom `ObjectMapper` bean), so every response field's JSON key is always present; declare every field in the class-level `@Schema(requiredProperties = {...})`, mirroring the convention `*Body` request DTOs already use. If a field's *value* can be `null`, additionally annotate it `@Schema(types = {"<T>", "null"})` (e.g. `types = {"string", "null"}`) — restate the base type explicitly, never `types = {"null"}` alone. **Never use `@Schema(nullable = true)`**: springdoc/swagger-core generates OpenAPI 3.1, under which `nullable` is silently dropped (verified — it never appears anywhere in the generated document, even on fields that carry it) with no compiler or runtime warning. A response DTO nesting a nullable `$ref` object additionally needs the `conluz-web` Orval `input.override.transformer` (rewrites `{$ref, type:[...,"null"]}` into `{anyOf:[{$ref},{type:"null"}]}`) to type correctly client-side — Orval otherwise silently drops nullability when a `$ref` sibling is present. Also never write a `@Schema(example = "...")` string that starts with a bare number/boolean/null token followed by whitespace or end-of-string (e.g. `"2024 winter distribution"`) — springdoc's Jackson-based example resolution renders it as that scalar, not the intended string (e.g. the number `2024`). Both conventions are enforced via ArchUnit (see `ResponseSchemaNullabilityArchTest.java` and `SchemaExampleArchTest.java`).

## Security & Authorization Policy

This policy is MANDATORY. Every REST controller endpoint MUST enforce it via a `@PreAuthorize` clause (delegating to the `@communityAccessGuard` bean when community/object scope is required). **All authorization lives in the controller layer** — services and repositories must contain no access-control logic. See the full policy (roles, capabilities, enforcement rules, 401/403/404 error mapping, and 409 state conflicts) in [`docs/security/authorization-policy.md`](docs/security/authorization-policy.md).

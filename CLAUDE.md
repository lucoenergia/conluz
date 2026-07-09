# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

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

The time-series database contains the following measurements:

- **`datadis_consumption_kwh`**: Consumption data from Datadis
  - Fields: `consumption_kwh`, `generation_energy_kwh`, `obtain_method`, `self_consumption_energy_kwh`, `surplus_energy_kwh`
  - Tags: `cups` (supply point identifier)

- **`huawei_production_hourly`**: Hourly production data from Huawei inverters
  - Fields: `inverter_power`, `ongrid_power`, `power_profit`, `radiation_intensity`, `theory_power`
  - Tags: `station_code` (plant identifier)

- **`huawei_production_realtime`**: Real-time production snapshots from Huawei inverters
  - Fields: `day_income`, `day_power`, `month_power`, `real_health_state`, `total_income`, `total_power`
  - Tags: `station_code` (plant identifier)

- **`omie_prices_kwh`**: Electricity market prices from OMIE
  - Fields: `price1` (price in €/kWh)
  - Tags: none

- **`shelly_consumption_kw`**: Consumption data from Shelly devices
  - Fields: `consumption_kw`
  - Tags: `channel`, `prefix` (device identifier)

- **`shelly_mqtt_power_messages`**: Raw MQTT power messages from Shelly devices
  - Fields: `value` (power in watts)
  - Tags: `host`, `topic` (MQTT topic path)

For the complete schema with sample data, see `docs/db/timeseries/influxdb/influxdb_schema.txt`.

## Configuration

### Required Environment Variables

- `CONLUZ_JWT_SECRET_KEY`: JWT secret key (≥256 bits, HMAC-SHA compatible). Generate using `org.lucoenergia.conluz.infrastructure.shared.security.JwtSecretKeyGenerator`
- `SPRING_DATASOURCE_URL`: PostgreSQL connection (default: `jdbc:postgresql://localhost:5432/conluz_db`)

### Database Setup

For new installations, use the sanitized reference example `deploy/docker-compose.example.yml`
(copy `deploy/.env.example` to `deploy/.env` and fill it in first). For existing databases:

**PostgreSQL:**
```sql
CREATE DATABASE conluz_db;
CREATE DATABASE conluz_db_test;
CREATE USER luz WITH PASSWORD 'blank';
GRANT ALL PRIVILEGES ON DATABASE conluz_db TO luz;
GRANT ALL PRIVILEGES ON DATABASE conluz_db_test TO luz;
```

**InfluxDB:**
```sql
CREATE DATABASE conluz_db
CREATE USER luz WITH PASSWORD 'blank'
GRANT ALL ON conluz_db TO luz
CREATE RETENTION POLICY one_month ON conluz_db DURATION 30d REPLICATION 1
CREATE RETENTION POLICY one_year ON conluz_db DURATION 365d REPLICATION 1
CREATE RETENTION POLICY forever ON conluz_db DURATION INF REPLICATION 1 DEFAULT
```

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
- All new code must have automated tests
- Architecture tests are enforced via ArchUnit (see `src/test/java/org/lucoenergia/conluz/architecture/`)
- When injecting beans, always use the interface. This also applies to integration tests
- When creating tests over services that has an interface, always use the name of the interface + "Test" for naming them
- **Never use `findAll().stream().findFirst()` in production code** to retrieve a single entity. This loads all rows into memory. Use a Spring Data derived query method that produces a `LIMIT 1` query instead — e.g., `findFirstBy()` or `findFirstByOrderByIdAsc()` in the JPA repository interface. This anti-pattern is only acceptable in test code where it avoids adding repository methods purely for test purposes.
- **JPA repositories and entities are internal infrastructure details and must never leak across layers.** Spring Data JPA repository interfaces (extending `JpaRepository`) and JPA entity classes (annotated with `@Entity`) may only be referenced within `infrastructure/` package tree. Repository implementation classes in `infrastructure/` are the sole layer where JPA/ORM types reside. Services and controllers must never receive or return JPA entities — entity mappers must convert between JPA entities and domain objects before crossing layer boundaries. Architecture tests (see `src/test/java/org/lucoenergia/conluz/architecture/JpaUsageArchTest.java`) enforce this via ArchUnit.
- **All `RepositoryDatabase` classes must be annotated with `@Transactional`.** Both read and write operations should declare intent explicitly: use `@Transactional(readOnly = true)` for read-only queries and `@Transactional` for write operations. This ensures consistent transaction boundaries across all database access. Architecture tests (see `src/test/java/org/lucoenergia/conluz/architecture/RepositoryTransactionalArchTest.java`) enforce this via ArchUnit.
- **All authorization logic must live in the controller layer.** Access decisions are expressed via `@PreAuthorize` on controllers (delegating to the `@communityAccessGuard` bean); services and repositories must contain no access-control logic and must never call `CommunityAccessGuard` or throw `AccessDeniedException`. The only non-controller class allowed to reference `AccessDeniedException` is `ConluzAccessDeniedHandler` (the component that maps it to a 403 response). Architecture tests (see `src/test/java/org/lucoenergia/conluz/architecture/AuthorizationLocationArchTest.java`) enforce this via ArchUnit.
- **Controllers must be thin: no domain logic and no calls to the repository layer.** A controller may only (1) enforce authorization via `@PreAuthorize`, (2) bind and validate the request (path variables, `@Valid @RequestBody`), (3) delegate to a single domain service call, and (4) map the service result to the HTTP response. All business logic — conditional dispatch/branching, precondition checks (e.g. "is Datadis enabled for this community?"), iteration, orchestration, and any repository access — must live in a service (`domain/**` interface with its `infrastructure/**` `*Impl`). Controllers must never inject or call a repository (`*Repository`, `RepositoryDatabase`, `*RepositoryInflux`) directly; they depend only on service interfaces. If a controller needs data or a guard condition, add a service method for it rather than reaching into a repository or embedding an `if` that encodes a business rule. The repository-access half of this rule is enforced via ArchUnit (see `src/test/java/org/lucoenergia/conluz/architecture/ControllerRepositoryAccessArchTest.java`).

## Security & Authorization Policy

This policy is MANDATORY. Every REST controller endpoint MUST enforce it via a `@PreAuthorize` clause (delegating to the `@communityAccessGuard` bean when community/object scope is required). **All authorization lives in the controller layer** — services and repositories must contain no access-control logic (no `CommunityAccessGuard` calls, no `AccessDeniedException`); this is enforced by `AuthorizationLocationArchTest`. The `@Operation` description MUST state the required role(s) so Swagger matches the guard. No endpoint may rely on being "internal" — every endpoint is authorized.

### Roles
- **Platform admin** — `User.isPlatformAdmin() == true` → authority `ROLE_PLATFORM_ADMIN`.
- **Community admin** — enabled membership with `CommunityRole.COMMUNITY_ADMIN`.
- **Member / regular user** — enabled membership without admin role.

### Capabilities
- Platform admins can:
  - List, view, create, edit and remove users globally.
  - List, view, create, edit and remove communities.
  - Add and remove admins to/from communities.
- Community admins (scoped to the community they administer) can:
  - Import, create, edit, view, list and remove members.
  - Import, create, edit, view, list and remove supplies of those members.
  - Create, edit, view, list and remove plants.
  - Create, edit, view, list and remove sharing agreements.
  - Manage supply/plant config (Huawei, Datadis, Shelly).
  - Get consumption and production data of any supply or plant they administer.
- Regular users (non-admins) can:
  - See data about supplies they own.
  - See production data of their community/communities.
- Any authenticated user can get prices.
- Any user can modify their own data, but CANNOT enable/disable or delete themselves.

### Enforcement rules for developers and AI agents
- Platform-wide actions: `@PreAuthorize("hasRole('PLATFORM_ADMIN')")`.
- Community-scoped actions: `@PreAuthorize("@communityAccessGuard.<method>(...)")` using the matching guard method (`canManageCommunity`, `canManageMemberships`, `canManagePlant`, `canCreatePlant`, `canManageSharingAgreement`, `canEditSupply`, `canCreateUserIn`, `canReadUser`, `canEditUser`, `canListUsers`).
- Object reads scoped to ownership/community: enforce `canReadSupply` / `canReadCommunity` (or the matching object-scoped guard method) directly in the controller `@PreAuthorize` (never in the service). The guard method itself throws the matching `*NotFoundException` (→ 404) when the caller cannot see the object — controllers MUST NOT add their own `if (!guard.canX(id)) throw ...` / `ResponseEntity.notFound()` boilerplate. See "Error responses for denied access" below.
- List endpoints: compute the visible scope in the controller via the guard (`visibleCommunityIds()` for membership scope, `adminCommunityIds()` for admin-only scope) and pass it as a plain parameter to the service/repository query — the service must not call the guard itself.
- `isAuthenticated()` alone is acceptable ONLY for endpoints any authenticated user may call without object scope (e.g. `GET /prices`). Otherwise use a `@communityAccessGuard` method.
- Self-service: a user editing their own record is allowed; enabling/disabling/deleting one's own account MUST be rejected for everyone, including admins (e.g. `@PreAuthorize("@communityAccessGuard.canEditUser(#userId) and !@communityAccessGuard.isCurrentUser(#userId)")`).
- New endpoints without an authorization clause are NOT permitted. Add a controller test for every endpoint asserting **401** (no token); **404** when an authenticated caller cannot see the targeted object (object-scoped denial); and **403** when the caller can see the object but lacks permission for the action (role/scope denials and self-service).

### Error responses for denied access (401 / 403 / 404)

To avoid leaking the existence of resources, denials are mapped by **visibility**, not just by role:
- **401 Unauthorized** — the request is unauthenticated.
- **404 Not Found** — the authenticated caller **cannot see** the targeted object (it does not exist, or it is outside everything they may read). Returning 403 here would reveal that the object exists.
- **403 Forbidden** — the authenticated caller **can see** the object but is **not permitted to perform this action** (e.g. a non-admin community member hitting a community-admin-only endpoint). They already know it exists, so nothing leaks. Also used for platform-wide role checks (`hasRole('PLATFORM_ADMIN')`) and self-service guards (enabling/disabling/deleting one's own account).

How this is enforced (centralized in the guard — no controller boilerplate):
- Object-scoped `@communityAccessGuard` methods perform a **visibility gate then an authorization check**: they **throw the matching `*NotFoundException`** (`CommunityNotFoundException`, `SupplyNotFoundException`, `PlantNotFoundException`, `UserNotFoundException`, `SharingAgreementNotFoundException`) when the caller cannot see the object, and otherwise **return** whether the action is allowed (`false` → 403). They return `false` (never throw) only when the caller is unauthenticated, so anonymous requests become 401.
- Controllers reference the guard directly in `@PreAuthorize` (e.g. `@PreAuthorize("@communityAccessGuard.canReadSupply(#id)")`); they add **no** not-found/forbidden boilerplate. A `*NotFoundException` thrown during `@PreAuthorize` evaluation is mapped to 404 by the global `@RestControllerAdvice` handlers; a `false` result is mapped to 403 by `ConluzAccessDeniedHandler` (or 401 for anonymous callers).
- This is why an object-scoped denial must NEVER be left to fall through to a 403 when the caller cannot see the object — the guard decides 404 vs 403. Throwing a domain `*NotFoundException` from the guard does not violate the "only `ConluzAccessDeniedHandler` references `AccessDeniedException`" rule (it is a different exception type).

### State conflicts (409)

`409 Conflict` is **not** an authorization outcome — it signals that a request which is authenticated, authorized and well-formed cannot be applied because it **conflicts with the current state of the resource** (a precondition/invariant violation, not a missing permission or a malformed body). Use it — never 400, 403 or 422 — whenever the operation is legal for this caller but the target's state forbids it right now.

Established uses in this codebase:
- **Integration disabled** — hitting a manual sync / config-dependent endpoint while the integration is turned off: `DatadisDisabledException`, `ShellyDisabledException`, `HuaweiDisabledException` (e.g. auto-sync is enabled, so the Huawei manual sync endpoints respond `409`).
- **Invariant would be broken** — an action that would violate a domain invariant, e.g. revoking the last platform admin (`LastPlatformAdminException`).

Enforcement rules for developers and AI agents:
- Model each conflict as a dedicated domain exception thrown by the **service** (not the controller), and map it to `HttpStatus.CONFLICT` in the module's `@RestControllerAdvice` `*ExceptionHandler` via `errorBuilder.build(message, HttpStatus.CONFLICT)`, with an i18n message key. Do not build the `ResponseEntity` in the controller.
- Document the `409` response on the endpoint's `@Operation`/`@ApiResponse` so Swagger matches the behavior.
- Add a controller test asserting **409** for the conflicting-state case (alongside the 401/403/404 tests required above).

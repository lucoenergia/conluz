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
```bash
# From project root:
docker build -t conluz:1.0 -f Dockerfile .
cd deploy
docker compose up -d            # Start all services
docker compose up -d postgres   # Start only PostgreSQL
docker compose up -d influxdb   # Start only InfluxDB
docker stop conluz              # Stop the app
```

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

For new installations, use `deploy/docker-compose.yaml`. For existing databases:

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

- Follow Clean Code principles
- Code and comments must be in English
- Code should be self-explanatory with comments when additional explanation is needed
- All new code must have automated tests
- Architecture tests are enforced via ArchUnit (see `src/test/java/org/lucoenergia/conluz/architecture/`)
- when injecting beans, always use the interface. This also applies to integration tests
- when creating tests over services that has an interface, always use the name of the interface + "Test" for naming them

## General Notes
- Automatically use context7 for code generation and library documentation.
# Deploy — sanitized reference example

This directory is a **reference example**. It shows the
minimal shape of a Conluz deployment (PostgreSQL + InfluxDB + the Conluz application) so you can
bring up a single instance from scratch and adapt it to your own environment.

## Contents

| File | Purpose |
|---|---|
| `docker-compose.example.yml` | Minimal core stack: `postgres`, `influxdb`, `conluz`. |
| `.env.example` | Template for the variables the compose file expects (placeholders only). |
| `conluz-postgres-init.sh` | First-boot script: creates the app databases and role in Postgres. |
| `conluz-influxdb-init.sh` | First-boot script: creates the InfluxDB database, user and retention policies. |

## Environment variables

Provide these via a local `.env` (see `.env.example` for the full annotated list):

- **App:** `CONLUZ_JWT_SECRET_KEY`, `CONLUZ_IMAGE`
- **PostgreSQL:** `POSTGRES_USER`, `POSTGRES_PASSWORD`, `PATH_TO_POSTGRES_DATA`, `SPRING_DATASOURCE_URL`
- **InfluxDB:** `INFLUXDB_ADMIN_USER`, `INFLUXDB_ADMIN_PASSWORD`, `INFLUXDB_CONLUZ_USER`,
  `INFLUXDB_CONLUZ_USER_PASSWORD`, `PATH_TO_INFLUXDB_DATA`, `SPRING_INFLUXDB_URL`,
  `SPRING_INFLUXDB_DATABASE`, `SPRING_INFLUXDB_USERNAME`, `SPRING_INFLUXDB_PASSWORD`

## Bring up one instance

```bash
cd deploy
cp .env.example .env                                    # then edit .env with your own values
docker compose -f docker-compose.example.yml up -d      # start postgres + influxdb + conluz
```

The app is then reachable at https://localhost:8443 (see the project `README.md` and `CLAUDE.md`
for build and usage details).

> Validate the compose file without starting anything with:
> `docker compose -f docker-compose.example.yml config`.

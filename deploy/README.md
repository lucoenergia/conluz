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

- **App:** `CONLUZ_JWT_SECRET_KEY`, `CONLUZ_IMAGE`, `CONLUZ_TRUSTED_PROXIES`
- **PostgreSQL:** `POSTGRES_USER`, `POSTGRES_PASSWORD`, `PATH_TO_POSTGRES_DATA`, `SPRING_DATASOURCE_URL`
- **InfluxDB:** `INFLUXDB_ADMIN_USER`, `INFLUXDB_ADMIN_PASSWORD`, `INFLUXDB_CONLUZ_USER`,
  `INFLUXDB_CONLUZ_USER_PASSWORD`, `PATH_TO_INFLUXDB_DATA`, `SPRING_INFLUXDB_URL`,
  `SPRING_INFLUXDB_DATABASE`, `SPRING_INFLUXDB_USERNAME`, `SPRING_INFLUXDB_PASSWORD`

### `CONLUZ_TRUSTED_PROXIES`

The reverse proxies whose `X-Forwarded-For` header the app trusts to learn the client's address, which login
and password-change throttling and the authentication logs rely on. The value is a **Java regex matched
against the address of the direct peer**: the proxy's address as the app sees it. Empty or unset, the
default, trusts no proxy, so every request is attributed to the address it arrives from.

Docker assigns container addresses dynamically, so a regex for a single container address can stop
matching once the proxy container is recreated. Every request is then attributed to the proxy's own
address, and the per-address limit throttles all users together. Instead, put the proxy and the app on a
Docker network whose subnet is fixed in the compose file, and match that subnet with a range regex, for
example `172\.20\.0\.\d{1,3}` (in `.env`, wrap it in single quotes so the backslashes are kept). The
example `.env.example` leaves the value empty.

## Bring up one instance

```bash
cd deploy
cp .env.example .env                                    # then edit .env with your own values
docker compose -f docker-compose.example.yml up -d      # start postgres + influxdb + conluz
```

The app is then reachable at https://localhost:8443 (see the project `README.md` and `AGENTS.md`
for build and usage details).

> Validate the compose file without starting anything with:
> `docker compose -f docker-compose.example.yml config`.

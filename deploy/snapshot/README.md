# Anonymized production snapshot

`create-snapshot.sh` produces a versioned, **anonymized** snapshot bundle of production data
(PostgreSQL + InfluxDB 1.8). Member-identity PII is pseudonymized **on the production host**,
before any data leaves it. The bundle is the foundation later phases consume (local restore,
UAT refresh). Scheduling, restore and retention are out of scope here.

A finished bundle (`snapshot-<UTC-timestamp>.tar.gz`) contains:

| Entry           | Description                                                              |
| --------------- | ------------------------------------------------------------------------ |
| `postgres.dump` | Anonymized custom-format PostgreSQL dump (no member-identity PII)         |
| `influx/`       | Portable InfluxDB 1.8 backup (CUPS tags preserved, **not** rewritten)    |
| `manifest.json` | Provenance: app version/commit, UTC timestamp, source host, schema state |

## How it works

The script runs **on a workstation on the Tailnet** and drives the production host over SSH:

1. `pg_dump -Fc` the prod database (read-only). The raw PII dump never leaves the prod host.
2. Start a **separate ephemeral Postgres container** on the prod host (distinct name/port).
3. `pg_restore` the raw dump into the ephemeral, then run `anonymize.sql` against it.
4. `pg_dump -Fc` the **cleaned** ephemeral database — this anonymized dump is what ships.
5. `influxd backup -portable` the prod InfluxDB (read-only) and copy it out.
6. Write `manifest.json`.
7. Assemble the bundle in a temp dir and finalize it into `OUTPUT_DIR` only on full success.

```
workstation ──ssh──> prod host
                       ├─ postgres        (READ-ONLY: pg_dump)
                       ├─ influxdb         (READ-ONLY: influxd backup)
                       └─ ephemeral pg     (all restore + anonymize WRITES happen here)
```

## Prerequisites

- **SSH over Tailscale** to the prod laptop, key-based (the script is non-interactive).
  Verify: `ssh "$PROD_SSH_HOST" docker ps`.
- `docker` available on the prod host for the user you SSH as.
- On the workstation: `bash`, `ssh`, `scp`, `tar`.
- A populated `.env` (copy from `.env.example`).

## Invocation

```bash
cp .env.example .env   # then edit .env
./create-snapshot.sh
```

## Configuration

All environment-specific values live in `.env` (see `.env.example`).
**Do** read every value from `.env` → **do not** hardcode container names, db names, ports or
hosts in the script.

### Step-0 verification commands (confirm `.env` against the running prod stack)

```bash
ssh "$PROD_SSH_HOST" docker compose -f "$PROD_COMPOSE_DIR/docker-compose.yaml" ps   # container names
ssh "$PROD_SSH_HOST" docker exec postgres postgres --version                        # -> PG_VERSION
ssh "$PROD_SSH_HOST" docker exec influxdb influx -execute 'SHOW DATABASES'           # -> INFLUX_DB
```

## Safety invariants

- **Do** treat production as strictly read-only (`pg_dump`, `influxd backup`) → **do not** run
  `pg_restore`, `psql` writes, `DROP`, or any `influx` write against the prod containers.
- **Do** perform every restore/anonymization in the ephemeral container → **do not** target
  the prod container. `assert_ephemeral_target()` enforces this before each write and aborts
  otherwise.
- **Do** keep data one-directional, prod → workstation → **do not** push anything back to prod.
- **Do** pseudonymize PII on the prod host before transfer → **do not** transfer the raw dump;
  only the cleaned `postgres.dump` leaves the prod host.
- **Do** keep the **CUPS** intact (`supplies.code` and the InfluxDB tags) → **do not** rewrite
  it; it is the join key between the two stores.
- **Do** finalize the bundle only on full success → **do not** leave partial bundles; the
  ephemeral container and all temp files are removed on **any** exit (`trap cleanup EXIT`).

## What gets anonymized

`anonymize.sql` (run against the ephemeral DB only), deterministically derived from each row's
primary key so output is unique and stable across re-runs:

- `users`: `personal_id`, `full_name`, `address`, `phone_number`, `email`.
- `supplies`: `address` (but **not** `code` — the CUPS is retained).

Consumption magnitudes and timestamps are untouched; nothing is nulled out. The script is
idempotent: running it twice produces equivalent anonymized output.

## Exit codes

| Code | Meaning                                                          |
| ---- | ---------------------------------------------------------------- |
| 0    | Success — bundle written to `OUTPUT_DIR`                         |
| 2    | Usage / missing configuration (no `.env`, or a required var unset) |
| 10   | PostgreSQL dump (prod, read-only) failed                        |
| 11   | Ephemeral restore / anonymization / re-dump failed              |
| 20   | InfluxDB backup failed                                          |
| 30   | Bundle assembly / transfer failed                               |

A failure of either store aborts the run and emits **no** bundle.

## Trade-offs

- The ephemeral restore + anonymization runs **on the prod laptop**, costing CPU/RAM/disk for
  the duration of the run. In exchange, PII never leaves the host and prod stays read-only with
  **no downtime**. → **Do** run it during a quiet window if the laptop is resource-constrained
  → **do not** point the ephemeral container at the prod volume/port.

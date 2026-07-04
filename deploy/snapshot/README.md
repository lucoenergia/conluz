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

The script runs **on a workstation on the Tailnet** and drives the production host over SSH.
All data crosses container boundaries via `docker exec` pipes — never `docker cp` or host temp
files — so it works with the production host's confined snap Docker (see *Prerequisites*):

1. Start a **separate ephemeral Postgres container** on the prod host (distinct name, no
   published port — accessed only via `docker exec`).
2. Stream `pg_dump -Fc` of the prod database (read-only) **straight into `pg_restore`** on the
   ephemeral container, then run `anonymize.sql` against it. The raw PII dump is never written
   to disk — it only flows through the pipe between the two prod containers.
3. `pg_dump -Fc` the **cleaned** ephemeral database, streamed over SSH stdout — this anonymized
   dump is what ships.
4. `influxd backup -portable` the prod InfluxDB (read-only) and stream it out of the container
   with `docker exec … tar -cf -` piped to the workstation.
5. Write `manifest.json`.
6. Assemble the bundle in a temp dir and finalize it into `OUTPUT_DIR` only on full success.

```
workstation ──ssh──> prod host
                       ├─ postgres        (READ-ONLY: pg_dump)
                       ├─ influxdb         (READ-ONLY: influxd backup)
                       └─ ephemeral pg     (all restore + anonymize WRITES happen here)
```

## Prerequisites

- **SSH over Tailscale** to the prod laptop, key-based (the script is non-interactive).
  Verify: `ssh "$PROD_SSH_HOST" docker ps`.
- `docker` available on the prod host for the user you SSH as. The prod Docker is the confined
  **snap** package, whose daemon cannot access the host's `/tmp` (so `docker cp <hostpath> …`
  fails with `lstat …: no such file or directory`). The script therefore avoids `docker cp`
  and host temp files entirely, streaming over `docker exec` pipes instead.
- On the workstation: `bash`, `ssh`, `tar`.
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
- **Do** pseudonymize PII on the prod host before transfer → **do not** transfer the raw dump.
  The raw PII dump is never even written to the prod disk — it is streamed straight from the
  prod Postgres container into the ephemeral one; only the cleaned `postgres.dump` leaves the
  prod host.
- **Do** keep the **CUPS** intact (`supplies.code` and the InfluxDB tags) → **do not** rewrite
  it; it is the join key between the two stores.
- **Do** finalize the bundle only on full success → **do not** leave partial bundles; the
  ephemeral container and all temp files are removed on **any** exit (`trap cleanup EXIT`).

## What gets anonymized

`anonymize.sql` (run against the ephemeral DB only), deterministically derived from each row's
primary key so output is unique and stable across re-runs:

- `users`: `personal_id`, `full_name`, `address`, `phone_number`, `email` (identity PII).
- `supplies`: `address` (but **not** `code` — the CUPS is retained).

It also **scrubs secrets** so no usable credential leaves the host:

- `users.password`: every account is reset to a known bcrypt hash of the literal string
  `password`, so restored UAT/local environments stay loggable with a documented throwaway
  credential instead of shipping production hashes.
- `datadis_config` / `huawei_config` (`username`, `password`): these third-party portal
  credentials are stored **without encryption at rest**, so they are overwritten with inert
  placeholders. Restored environments must be reconfigured with their own credentials.

It also **disables every third-party integration** so a restored local/UAT stack can never
call out to the real services by mistake:

- `datadis_config`, `huawei_config`, `shelly_config` (`enabled`): forced to `false`. The
  update is guarded on column existence, so a snapshot from a production install that predates
  the `enabled` column is left untouched — Liquibase recreates the column with a `false`
  default when the restored branch migrates on startup, so the integration is off either way.
  Restored environments must explicitly re-enable each integration after configuring their own
  credentials.

Consumption magnitudes and timestamps are untouched. The script is idempotent: running it
twice produces equivalent anonymized output.

## Exit codes

| Code | Meaning                                                          |
| ---- | ---------------------------------------------------------------- |
| 0    | Success — bundle written to `OUTPUT_DIR`                         |
| 2    | Usage / missing configuration (no `.env`, or a required var unset) |
| 11   | Ephemeral restore / anonymization / re-dump failed (incl. the prod read-only `pg_dump` that feeds the restore stream) |
| 20   | InfluxDB backup failed                                          |
| 30   | Bundle assembly / transfer failed                               |

A failure of either store aborts the run and emits **no** bundle.

## Trade-offs

- The ephemeral restore + anonymization runs **on the prod laptop**, costing CPU/RAM/disk for
  the duration of the run. In exchange, PII never leaves the host and prod stays read-only with
  **no downtime**. → **Do** run it during a quiet window if the laptop is resource-constrained
  → **do not** reuse a prod container name for the ephemeral container (the script aborts up
  front if `EPHEMERAL_PG_CONTAINER` collides with a prod container, and `cleanup()` never
  force-removes a prod container).

# Local data-clone workflow

Restore a snapshot **bundle** (anonymized production data) into a **dedicated, isolated**
Postgres + InfluxDB stack, then run the **currently checked-out branch** with `./gradlew bootRun`
so **Liquibase applies the branch's pending changesets on startup**.

Purpose: validate a branch's code against prod-like (anonymized) data **before** it reaches
production. The bundle carries production's (older) schema state, so starting the branch on top
reproduces the production migration.

> This restore is **destructive** — it drops and recreates databases. It runs on a **separate
> isolated stack** (`conluz-local-*`, ports 5433/8087, named volumes) → it does **not** touch your
> normal `postgres`/`influxdb` services. It is also fenced behind a local-only guardrail gate and
> only ever acts on the **local Docker engine**.

## Isolation (run it alongside your dev stack)

`make up` starts a **second** Postgres + InfluxDB defined in `docker-compose.local.yml` under its
own compose project (`conluz-local`): distinct container names (`conluz-local-postgres` /
`conluz-local-influxdb`), distinct host ports (default **5433** / **8087**, set via
`LOCAL_POSTGRES_PORT` / `LOCAL_INFLUX_PORT`), and **named Docker volumes**. Do keep your usual
`postgres`/`influxdb` running on 5432/8086 → this workflow never reads or writes them. `make reset`
runs `down -v`, wiping **only** the isolated `conluz-local_*` volumes → **not** your dev data. A
guardrail refuses to run if you point the target at the shared `postgres`/`influxdb` names.

## Prerequisites

- Do install Docker (with Compose v2) and a JDK 17 toolchain → do **not** expect this to work
  against a remote Docker context.
- Do place a bundle `snapshot-<UTC>.tar.gz` in `BUNDLE_DIR` (default `deploy/local/bundles/`)
  → do **not** unpack it yourself; the script does.
- `jq` is optional (a `grep` fallback parses the manifest) → do install it for stricter validation.
- Do `cp .env.example .env` and set a local `CONLUZ_JWT_SECRET_KEY` → do **not** commit `.env` or
  copy production credentials into it.

## The loop (two commands)

```bash
cd deploy/local
cp .env.example .env            # once; edit CONLUZ_JWT_SECRET_KEY

make up                         # start the ISOLATED Postgres + InfluxDB stack (5433/8087)
make restore                    # dry-run: prints the resolved target + bundle identity
make restore EXECUTE=1          # perform the destructive restore
make run                        # ./gradlew bootRun -> Liquibase migrates on startup
```

Or the single entry point:

```bash
make loop EXECUTE=1             # restore (execute) then run
```

| Target | Does X → not Y |
| --- | --- |
| `make up` | Starts the **isolated** `conluz-local-*` stack → **not** your dev `postgres`/`influxdb`. |
| `make down` | Stops & removes the isolated stack containers → **not** your dev containers. |
| `make reset` | `down -v` wiping only the **isolated** named volumes → **not** your dev data. |
| `make restore` | Defaults to a **dry-run** (no changes) → **not** destructive unless `EXECUTE=1`. |
| `make restore BUNDLE=path` | Restores a specific bundle → otherwise picks the **newest** in `BUNDLE_DIR`. |
| `make run` | Runs the **checked-out branch** via `bootRun` → **not** the packaged prod image. |
| `make loop EXECUTE=1` | Restore then run → refuses to run without the explicit `EXECUTE=1`. |

## Guardrails (the restore aborts, exit 10, before touching any database)

The orchestrator resolves the target and fails **closed** if any check cannot be evaluated. Do
keep all of these true → do **not** try to bypass them to point at a shared/remote instance.

1. **Local-only target** — the Postgres/InfluxDB hosts in `.env` must be loopback
   (`127.0.0.1` / `::1` / `localhost`) and resolvable → **not** any other or unresolvable host.
2. **Production denylist** — the target must not match `CONLUZ_PROD_HOST_DENYLIST`
   (default `lucobot1 conluz-prod`) → **not** a production host/name.
3. **Local Docker engine** — `docker context` must be local (`default`/`desktop-linux`, `unix://`)
   → **not** an `ssh://` or remote `tcp://` context.
4. **Explicit marker** — `CONLUZ_ENV` must be exactly `local` → **not** empty/unset.
5. **Local credentials only** — only `deploy/local/.env` is sourced → **not** `deploy/snapshot/.env`
   or any production credential.
6. **Opt-in destruction** — default is `--dry-run` → **not** destructive without `--execute`/`EXECUTE=1`.
7. **Loud pre-flight** — the resolved target and bundle identity are printed before any action.

Exit codes: `10` guardrail abort · `20` bundle/manifest (or stack not up) · `30` Postgres restore ·
`40` InfluxDB restore.

## Version-match requirement

The bundle's `manifest.json` must match the local stack: **Postgres major == 16** and **InfluxDB
backup format == `portable`**. Do regenerate the bundle if versions drift → do **not** restore a
mismatched bundle (the script refuses, exit 20).

If the branch under test **modifies an already-applied changeset**, Liquibase will fail on startup
with a checksum mismatch — that is a real finding the workflow is meant to surface, not a bug here.

## InfluxDB 1.8 restore constraint

`influxd restore -portable` **cannot overwrite an existing database in place**, so the shared
`restore_influxdb.sh` **drops** the target database, restores it, and recreates the retention
policies and app user. Do expect the local InfluxDB database to be wiped and rebuilt on every
restore → do **not** rely on any pre-existing local InfluxDB data surviving.

## How shared scripts are reused (no parallel restore logic)

- Both `deploy/restore_postgres.sh` and `deploy/restore_influxdb.sh` were made
  **backward-compatible-configurable** (overridable container/DB names; tolerant stop/start in the
  Influx one). With **no overrides set they behave identically** for the disaster-recovery path.
- The local workflow points them at the isolated stack via env overrides
  (`POSTGRES_CONTAINER`/`POSTGRES_DB`, `INFLUX_CONTAINER`) and passes **sentinel** container names
  to `restore_influxdb.sh` (`CONLUZ_CONTAINER`/`TELEGRAF_CONTAINER`) so it never touches a real app
  container. The orchestrator feeds Postgres the uncompressed `postgres.dump` as a bare filename
  (run from `deploy/`) and verifies the result rather than trusting `pg_restore`'s exit code.

## Idempotency

Running `make restore EXECUTE=1` twice yields the same state: Postgres is dropped/recreated and
re-restored, InfluxDB is dropped/recreated by the shared script. Do re-run freely → do **not**
expect partial/incremental restores.

## Packaging caveat (important)

This workflow runs the branch with `./gradlew bootRun`, **not** the production Docker image. So
"works locally" validates the branch's **migrations and runtime against prod-like data** — it does
**not** certify the production artifact. Image/packaging validation is UAT's job (Phase 3). Do use
this to catch migration problems early → do **not** treat a green local run as production sign-off.

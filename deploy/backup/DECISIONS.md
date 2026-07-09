# Backup / DR — decision record

Locked decisions for the Conluz Backup/DR epic. Phase 1 (this directory) implements the
consistent local dump engine; later phases build on it. Anchor future changes to these.

## Engine and formats

- **Offsite/dedup engine = [restic][restic] (Phase 2).** Phase 1 deliberately produces plain
  local dumps; restic is layered on top later for encryption, deduplication, retention and
  offsite. Phase 1 adds none of those.
- **PostgreSQL = whole-cluster `pg_dumpall`, plain SQL.** Dump globals + every database (roles,
  `conluz_db`, `conluz_db_test`, …) in one artifact. **Not** `-Fc` / custom format: the cluster
  is well under 50 MB, and a plain-text whole-cluster dump is the simplest faithful,
  self-describing artifact. (The per-database custom-format `-Fc` dumps produced by
  `../backup_postgres.sh` and `../snapshot/` serve different, narrower purposes.)
- **InfluxDB = `influxd backup -portable`, whole instance, fresh timestamped dir.** Full and
  self-contained — **no incremental chains**. The `-database` flag is omitted so the backup
  captures **all databases + the metastore** (retention policies, users), giving DR parity with
  the whole-cluster Postgres dump. InfluxDB is **1.8**, so `influxd backup -portable` is the
  correct command. It connects to the backup RPC service (`127.0.0.1:8088`), which is
  independent of HTTP auth (`8086`) — so it runs without credentials and **without a `-host`
  flag** (passing `-host`, even its default value, is rejected by the 1.8 RPC service as
  "invalid metadata blob"). This is verified empirically on each run, not assumed.

## Targets, objectives, physical security

- **RPO / RTO = 24h** (pre-production). A daily consistent dump is sufficient for now.
- **Offsite = self-hosted, pull-based node + snapshots.** A separate node pulls backups and keeps
  its own snapshots (details land with Phase 2). No third-party cloud dependency.
- **External backup drive = plain ext4** (no per-drive encryption). The host's **root disk is
  LUKS-encrypted**, which covers the theft/loss threat for the machine; the removable drive stays
  physically with it.
- **Interactive root LUKS ⇒ no unattended recovery.** Because unlocking root requires an
  interactive passphrase at boot, recovery is a hands-on operation by design; automated,
  unattended restore is explicitly out of scope.

## Cross-cutting invariants

- **Mountpoint guard.** No script writes anywhere under `$BACKUP_MOUNT` until
  `guard_mountpoint "$BACKUP_MOUNT"` (in [`lib/guard.sh`](./lib/guard.sh)) confirms the backup
  drive is actually mounted there. Every later phase sources this same helper.
- **No `docker cp` against the prod host.** The production Docker daemon is a confined snap that
  cannot read host paths, so `docker cp <hostpath> …` fails. All data crosses the host/container
  boundary by streaming over `docker exec` (stdout redirect for Postgres; `tar -cf -` pipe for
  the Influx backup directory).
- **Atomic completion.** Backups are built under a `<timestamp>.partial/` staging directory and
  renamed to their final `<timestamp>/` name only after all dumps and the manifest succeed, so an
  interrupted run never looks complete.

## Facts established during Phase 1 (Step-0 investigation)

- **`luzgres` is primarily the Postgres *superuser role*.** It is `POSTGRES_USER` in the
  gitignored `deploy/.env` (which is why `rg -i luzgres` over the tracked tree finds nothing). It
  *also* exists as an unused default **database** of the same name: the official Postgres image
  auto-creates a database named after `POSTGRES_USER` when `POSTGRES_DB` is unset (confirmed — a
  test run's manifest lists databases `conluz_db`, `conluz_db_test`, `luzgres`, `postgres`). The
  real application databases are `conluz_db` / `conluz_db_test`. Whole-cluster `pg_dumpall`
  captures the role (in the globals section) and every database, including `luzgres`, regardless —
  so nothing special is needed for it.
- **Container names** (from `deploy/docker-compose.yaml`): `postgres` (image `postgres:16`),
  `influxdb` (image `influxdb:1.8`), `conluz` (app). No Compose project name is set, so targets
  are addressed by these fixed `container_name`s.
- **Backup mount** `/media/data` is net-new: nothing in the repo referenced it before Phase 1, and
  there is no committed Phase-0 runbook (the host-side drive/mount setup was done by hand). It is
  parameterized in `backup.env` and enforced by the mountpoint guard.

[restic]: https://restic.net/

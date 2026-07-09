# Conluz backup engine (Phase 1 — local consistent dumps)

This directory holds the **Phase 1** backup engine of the Conluz Backup/DR epic. It produces
**application-consistent, un-anonymized dumps** of both databases onto the external backup drive
on the **production host**. That is the whole job of this phase.

What it is **not** (all later phases): no encryption, no [restic][restic], no deduplication, no
compression beyond what the DB tools do natively, no retention/pruning, no offsite replication,
no scheduling (systemd/cron), and **no restore**. See [`DECISIONS.md`](./DECISIONS.md) for the
locked design decisions and rationale.

> This is a different subsystem from [`../snapshot/`](../snapshot). That tool produces
> *anonymized*, SSH-driven clones of production for local development. This engine produces
> *faithful* DR dumps and is meant to run **on the prod host itself**, writing to a locally
> mounted drive.

## What the engine does

`backup.sh` runs, in order:

1. **Guards the mount.** It sources [`lib/guard.sh`](./lib/guard.sh) and calls
   `guard_mountpoint "$BACKUP_MOUNT"` **before writing anything**. If the backup drive is not
   mounted at `$BACKUP_MOUNT`, it logs and exits non-zero — it never writes to an unmounted path.
2. **Stages atomically.** It creates a fresh timestamped staging directory under a `.partial`
   name: `"$BACKUP_MOUNT"/staging/<UTC-timestamp>.partial/`.
3. **Dumps PostgreSQL** — a whole-cluster `pg_dumpall` (globals + every database) via
   `docker exec`, streamed to `postgres/cluster.sql`. Plain SQL (not `-Fc`): the cluster is
   small and a plain-text dump is the simplest faithful whole-cluster artifact.
4. **Dumps InfluxDB** — `influxd backup -portable` (InfluxDB 1.8, **whole instance**: all
   databases + metastore, no incremental chain) via `docker exec` into a fresh `influx/`
   subdirectory.
5. **Writes `manifest.json`** — UTC timestamp, tool versions, the Postgres database list, byte
   sizes, and a **SHA-256 checksum of every artifact**.
6. **Finalizes atomically** — only after every step *and* the manifest succeed does it `mv` the
   `.partial` staging directory to its final timestamped name. An interrupted run therefore never
   looks complete: it leaves only a `<timestamp>.partial/` directory.

Every step logs its result; any failure exits non-zero with a clear message.

### Why no `docker cp`

The production Docker daemon is a confined snap that cannot read host paths, so `docker cp
<hostpath> …` fails. The engine instead **streams over `docker exec`**: it redirects
`docker exec … pg_dumpall` stdout straight to the host file, and pulls the Influx backup
directory out with `docker exec … tar -cf -` piped to a host `tar -xf -`. This is the same
approach as [`../snapshot/create-snapshot.sh`](../snapshot/create-snapshot.sh). (The older
`../backup_postgres.sh` / `../backup_influxdb.sh` scripts use `docker cp` and are unreliable on
the prod host; they are not used here.)

## Layout of produced artifacts

```
$BACKUP_MOUNT/staging/
└── 20260709T101500Z/            # final name (UTC, ISO-8601 basic); *.partial while in progress
    ├── postgres/
    │   └── cluster.sql          # pg_dumpall of the whole cluster
    ├── influx/                  # influxd backup -portable output (meta + shard files)
    │   ├── *.meta
    │   ├── *.manifest
    │   └── *.tar.gz
    └── manifest.json            # timestamp, tool versions, DB list, sizes, SHA-256 per artifact
```

## Configuration

All parameters live in [`backup.env`](./backup.env), seeded with the current production values:

| Variable         | Default        | Meaning                                              |
| ---------------- | -------------- | ---------------------------------------------------- |
| `BACKUP_MOUNT`   | `/media/data`  | Mountpoint of the external backup drive (guarded).   |
| `PG_CONTAINER`   | `postgres`     | PostgreSQL container name.                            |
| `PG_SUPERUSER`   | `luzgres`      | Postgres superuser role used for `pg_dumpall`.       |
| `INFLUX_CONTAINER` | `influxdb`   | InfluxDB (1.8) container name.                        |

`pg_dumpall` needs no password: it connects over the container's local socket, which the
official Postgres image trusts (the same way the existing `deploy/` scripts run `pg_dump`). If a
future host requires one, set `PGPASSWORD` in `backup.env`. The Influx portable backup connects
to the backup RPC service (`127.0.0.1:8088`), which is independent of HTTP auth (`8086`), so it
needs no credentials and no `-host` flag.

## How to run

On the production host, with the stack up and the backup drive mounted at `$BACKUP_MOUNT`:

```bash
cd deploy/backup
./backup.sh
```

A successful run prints the path of the finalized backup directory.

### Verify a run

```bash
BK=$(ls -dt "$BACKUP_MOUNT"/staging/*/ | head -1)   # newest finalized backup
ls -R "$BK"                                          # postgres/cluster.sql, influx/, manifest.json
cat "$BK/manifest.json"                              # sizes + SHA-256 per artifact
( cd "$BK" && sha256sum -c <(jq -r '.artifacts[] | "\(.sha256)  \(.path)"' manifest.json) )
```

### Demonstrate the guard aborts

Point `BACKUP_MOUNT` at a non-mountpoint (or run with the drive unmounted): the script exits
non-zero and writes nothing.

```bash
BACKUP_MOUNT=/tmp/not-a-mount ./backup.sh   # -> "ERROR: ... is not a mountpoint. Refusing to write."; exit 1
```

[restic]: https://restic.net/

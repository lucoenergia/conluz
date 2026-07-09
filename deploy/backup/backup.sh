#!/usr/bin/env bash
#
# backup.sh
#
# Phase 1 of the Conluz Backup/DR epic: produce application-consistent, un-anonymized dumps of
# PostgreSQL (whole cluster) and InfluxDB 1.8 (whole instance) onto the external backup drive.
# Run it MANUALLY on the production host, with the stack up and the backup drive mounted.
#
# No encryption, restic, retention, offsite, scheduling or restore here -- those are later phases
# (see DECISIONS.md). This script only creates a consistent, checksummed local dump.
#
# Order of operations:
#   1. Guard the mount (guard_mountpoint) BEFORE writing anything.
#   2. Create a fresh timestamped staging dir under a *.partial name.
#   3. pg_dumpall the whole cluster              -> postgres/cluster.sql
#   4. influxd backup -portable the whole instance -> influx/
#   5. Write manifest.json (versions, DB list, sizes, SHA-256 per artifact).
#   6. Atomically rename *.partial -> final name only after every step succeeded.
#
# All data crosses the host/container boundary by streaming over "docker exec" (no "docker cp":
# the prod Docker is a confined snap that cannot read host paths).

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# --- Configuration ---------------------------------------------------------------------------

ENV_FILE="${ENV_FILE:-$SCRIPT_DIR/backup.env}"
if [[ ! -f "$ENV_FILE" ]]; then
  echo "ERROR: configuration file not found: $ENV_FILE" >&2
  exit 1
fi
# An explicit BACKUP_MOUNT from the environment wins over backup.env (e.g. the guard demo:
# BACKUP_MOUNT=/tmp/not-a-mount ./backup.sh). Capture it before sourcing clobbers it.
BACKUP_MOUNT_OVERRIDE="${BACKUP_MOUNT:-}"
set -o allexport
# shellcheck disable=SC1090
source "$ENV_FILE"
set +o allexport
[[ -n "$BACKUP_MOUNT_OVERRIDE" ]] && BACKUP_MOUNT="$BACKUP_MOUNT_OVERRIDE"

# Required variables -- fail fast if any is unset or empty. BACKUP_MOUNT may still be overridden
# from the environment for the guard demo (e.g. BACKUP_MOUNT=/tmp/x ./backup.sh).
REQUIRED_VARS=(BACKUP_MOUNT PG_CONTAINER PG_SUPERUSER INFLUX_CONTAINER)
for var in "${REQUIRED_VARS[@]}"; do
  if [[ -z "${!var:-}" ]]; then
    echo "ERROR: required variable '$var' is unset. See backup.env." >&2
    exit 1
  fi
done

# Container path the InfluxDB portable backup is written to before being streamed out.
INFLUX_TMP_DIR="/tmp/conluz-influx-backup"

TIMESTAMP="$(date -u +%Y%m%dT%H%M%SZ)"

# --- Helpers ---------------------------------------------------------------------------------

log() { echo ">> $*"; }

die() {
  echo "ERROR: $*" >&2
  exit 1
}

# Run a command in the Postgres container, forwarding PGPASSWORD only when it is set (default
# auth over the container's local socket is trust, so no password is normally needed).
pg_docker_exec() {
  if [[ -n "${PGPASSWORD:-}" ]]; then
    docker exec -e PGPASSWORD="$PGPASSWORD" "$PG_CONTAINER" "$@"
  else
    docker exec "$PG_CONTAINER" "$@"
  fi
}

# Minimal JSON string escaping for values embedded in manifest.json (backslash and double quote).
json_escape() {
  local s="$1"
  s="${s//\\/\\\\}"
  s="${s//\"/\\\"}"
  printf '%s' "$s"
}

# --- Step 1: Guard the mount (before writing anything) ---------------------------------------

# shellcheck source=lib/guard.sh
source "$SCRIPT_DIR/lib/guard.sh"
guard_mountpoint "$BACKUP_MOUNT"

# --- Step 2: Staging dir -----------------------------------------------------------------------

STAGING_ROOT="$BACKUP_MOUNT/staging"
PARTIAL_DIR="$STAGING_ROOT/${TIMESTAMP}.partial"
FINAL_DIR="$STAGING_ROOT/${TIMESTAMP}"

[[ -e "$FINAL_DIR" ]] && die "a backup for timestamp '$TIMESTAMP' already exists at '$FINAL_DIR'"
[[ -e "$PARTIAL_DIR" ]] && die "a partial staging dir already exists at '$PARTIAL_DIR'"

mkdir -p "$PARTIAL_DIR/postgres" "$PARTIAL_DIR/influx" \
  || die "could not create staging directory '$PARTIAL_DIR'"
log "[1/5] staging at $PARTIAL_DIR"

# --- Step 3: PostgreSQL whole-cluster dump -----------------------------------------------------

log "[2/5] dumping PostgreSQL cluster (pg_dumpall as '$PG_SUPERUSER')..."
PG_DUMP_FILE="$PARTIAL_DIR/postgres/cluster.sql"
pg_docker_exec pg_dumpall -U "$PG_SUPERUSER" > "$PG_DUMP_FILE" \
  || die "pg_dumpall failed"
[[ -s "$PG_DUMP_FILE" ]] || die "pg_dumpall produced an empty file"

# --- Step 4: InfluxDB whole-instance portable backup -------------------------------------------

log "[3/5] backing up InfluxDB (influxd backup -portable, whole instance)..."
# Clean any leftover from a prior interrupted run, back up, stream out via tar, then clean up.
# No -host and no -database flags: -host (even the default) is rejected by the 1.8 RPC service,
# and omitting -database captures all databases + the metastore. This runs without credentials
# because the backup RPC (127.0.0.1:8088) is independent of HTTP auth -- verified by a successful
# run (if it ever fails without creds, that assumption is the first thing to check).
docker exec "$INFLUX_CONTAINER" rm -rf "$INFLUX_TMP_DIR" >/dev/null 2>&1 || true
docker exec "$INFLUX_CONTAINER" influxd backup -portable "$INFLUX_TMP_DIR" \
  || die "influxd backup failed"
docker exec "$INFLUX_CONTAINER" tar -C "$INFLUX_TMP_DIR" -cf - . \
  | tar -C "$PARTIAL_DIR/influx" -xf - \
  || die "could not stream InfluxDB backup out of the container"
docker exec "$INFLUX_CONTAINER" rm -rf "$INFLUX_TMP_DIR" >/dev/null 2>&1 || true
# Sanity: the portable backup must contain at least its metastore file.
compgen -G "$PARTIAL_DIR/influx/*" > /dev/null || die "InfluxDB backup directory is empty"

# --- Step 5: Manifest --------------------------------------------------------------------------

log "[4/5] writing manifest.json..."

PG_TOOL_VERSION="$(pg_docker_exec pg_dumpall --version | head -1)"
INFLUX_VERSION="$(docker exec "$INFLUX_CONTAINER" influxd version 2>&1 | head -1)"

# Postgres database list (excludes template databases), as a JSON array.
pg_databases_json() {
  local dbs first=1 line
  dbs="$(pg_docker_exec psql -U "$PG_SUPERUSER" -Atc \
    'SELECT datname FROM pg_database WHERE NOT datistemplate ORDER BY datname')"
  printf '['
  while IFS= read -r line; do
    [[ -z "$line" ]] && continue
    [[ $first -eq 0 ]] && printf ', '
    first=0
    printf '"%s"' "$(json_escape "$line")"
  done <<< "$dbs"
  printf ']'
}

# All produced files, each with byte size and SHA-256, as a JSON array. Paths are relative to the
# staging dir so the checksums stay verifiable after the *.partial -> final rename. The whole
# body runs with cwd = staging dir so find/stat/sha256sum all resolve the same relative paths; a
# failure to stat or checksum any file aborts (exit 1 propagates through the command sub).
artifacts_json() {
  cd "$PARTIAL_DIR" || return 1
  local first=1 f rel bytes sha
  while IFS= read -r -d '' f; do
    rel="${f#./}"
    bytes="$(stat -c %s "$f")" || { echo "ERROR: stat failed for '$rel'" >&2; exit 1; }
    sha="$(sha256sum "$f" | awk '{print $1}')" || { echo "ERROR: sha256sum failed for '$rel'" >&2; exit 1; }
    [[ $first -eq 0 ]] && printf ',\n'
    first=0
    printf '    { "path": "%s", "bytes": %s, "sha256": "%s" }' \
      "$(json_escape "$rel")" "$bytes" "$sha"
  done < <(find . -type f -not -name manifest.json -print0 | sort -z)
}

PG_DATABASES="$(pg_databases_json)"
ARTIFACTS="$(artifacts_json)"
TOTAL_BYTES="$(du -sb "$PARTIAL_DIR" | awk '{print $1}')"

cat > "$PARTIAL_DIR/manifest.json" <<EOF
{
  "schema_version": 1,
  "created_utc": "$TIMESTAMP",
  "total_bytes": $TOTAL_BYTES,
  "postgres": {
    "tool_version": "$(json_escape "$PG_TOOL_VERSION")",
    "dump": "postgres/cluster.sql",
    "scope": "whole-cluster (pg_dumpall: globals + all databases)",
    "databases": $PG_DATABASES
  },
  "influxdb": {
    "tool_version": "$(json_escape "$INFLUX_VERSION")",
    "dir": "influx/",
    "scope": "whole-instance (influxd backup -portable, all databases + metastore)"
  },
  "artifacts": [
$ARTIFACTS
  ]
}
EOF

# --- Step 6: Atomic finalize -------------------------------------------------------------------

log "[5/5] finalizing..."
mv "$PARTIAL_DIR" "$FINAL_DIR" || die "could not finalize backup (mv to '$FINAL_DIR')"

log "OK: backup written to $FINAL_DIR"
exit 0

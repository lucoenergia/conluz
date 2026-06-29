#!/usr/bin/env bash
#
# create-snapshot.sh
#
# Produces a versioned, anonymized snapshot bundle of PRODUCTION data (PostgreSQL + InfluxDB
# 1.8). Run it MANUALLY from a workstation on the Tailnet; it drives the production host over
# SSH and brings back a single tarball containing:
#
#   postgres.dump   -- anonymized custom-format PostgreSQL dump (no member-identity PII)
#   influx/         -- portable InfluxDB 1.8 backup (CUPS tags preserved, not rewritten)
#   manifest.json   -- provenance (app version/commit, UTC timestamp, source host, etc.)
#
# SAFETY INVARIANTS (must hold unconditionally):
#   1. Production databases are strictly READ-ONLY. Against prod we only run "pg_dump" and
#      "influxd backup". Do read prod -> do not run pg_restore / psql writes / influx writes
#      against the prod containers.
#   2. ALL restore + anonymization happen in a SEPARATE EPHEMERAL Postgres container with a
#      distinct name/port/volume. assert_ephemeral_target() guards every write so a restore can
#      never be aimed at the prod container.
#   3. Data flows prod -> workstation only.
#   4. PII never enters the shippable bundle: pseudonymization runs on the prod host inside the
#      ephemeral container; only the cleaned dump is transferred.
#   5. No partial bundles: assemble in a temp location, finalize only on full success, and tear
#      everything down on any exit (trap cleanup EXIT).
#
# IMPLEMENTATION NOTE: all data crosses host<->container boundaries via "docker exec" pipes,
# never via "docker cp" or host temp files. The production Docker is a confined snap whose
# daemon cannot access the host's /tmp, so "docker cp <hostpath> ..." fails with
# "lstat <path>: no such file or directory". Streaming over docker exec sidesteps this and is
# portable across snap/rootless/PrivateTmp daemons. As a bonus the raw PII dump never touches
# the prod disk -- it is streamed straight from the prod container into the ephemeral one.
#
# Exit codes:
#   0  success
#   2  usage / missing configuration
#   11 ephemeral restore / anonymization / re-dump failed (incl. the prod read-only pg_dump
#      that feeds the restore stream)
#   20 InfluxDB backup failed
#   30 bundle assembly / transfer failed

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# --- Configuration ---------------------------------------------------------------------------

ENV_FILE="${ENV_FILE:-$SCRIPT_DIR/.env}"
if [[ ! -f "$ENV_FILE" ]]; then
  echo "ERROR: configuration file not found: $ENV_FILE" >&2
  echo "Copy .env.example to .env and fill it in." >&2
  exit 2
fi
# Load environment-specific values (same pattern as the other deploy/ scripts).
set -o allexport
# shellcheck disable=SC1090
source "$ENV_FILE"
set +o allexport

# Required variables -- fail fast if any is unset or empty.
REQUIRED_VARS=(
  PROD_SSH_HOST
  PG_CONTAINER PG_USER PG_DB PG_VERSION
  INFLUX_CONTAINER INFLUX_DB
  EPHEMERAL_PG_CONTAINER EPHEMERAL_PG_PASSWORD
  OUTPUT_DIR
)
for var in "${REQUIRED_VARS[@]}"; do
  if [[ -z "${!var:-}" ]]; then
    echo "ERROR: required variable '$var' is unset. See .env.example." >&2
    exit 2
  fi
done

# Container running the app, used only to read the deployed image tag for the manifest.
APP_CONTAINER="${APP_CONTAINER:-conluz}"

# INVARIANT 1+2, fail-closed BEFORE any trap is installed: the ephemeral identifiers must
# be distinct from the production ones. The per-write assert_ephemeral_target() guards the
# main path, but the EXIT trap's "docker rm -f $EPHEMERAL_PG_CONTAINER" runs even when that
# assert aborts -- so a misconfigured EPHEMERAL_PG_CONTAINER (e.g. a typo matching the prod
# container) would otherwise let cleanup force-remove a production container. Validate here,
# before 'trap cleanup EXIT' exists, so a config collision can never reach cleanup.
# shellcheck disable=SC2153
if [[ "$EPHEMERAL_PG_CONTAINER" == "$PG_CONTAINER" \
   || "$EPHEMERAL_PG_CONTAINER" == "$INFLUX_CONTAINER" \
   || "$EPHEMERAL_PG_CONTAINER" == "$APP_CONTAINER" ]]; then
  echo "ERROR: EPHEMERAL_PG_CONTAINER ('$EPHEMERAL_PG_CONTAINER') must differ from the" >&2
  echo "       production container names (PG_CONTAINER/INFLUX_CONTAINER/APP_CONTAINER)." >&2
  exit 2
fi

# Superuser of the ephemeral throwaway instance (official postgres image default).
EPHEMERAL_PG_USER="postgres"

TIMESTAMP="$(date -u +%Y%m%dT%H%M%SZ)"
BUNDLE_NAME="snapshot-${TIMESTAMP}"

# --- Helpers ---------------------------------------------------------------------------------

# Run a command on the production host over SSH.
# Local config variables are intentionally expanded on the client side before the command is
# sent to the remote shell, hence the SC2029 suppression.
prod() {
  # shellcheck disable=SC2029
  ssh "$PROD_SSH_HOST" "$@"
}

die() {
  local code="$1"; shift
  echo "ERROR: $*" >&2
  exit "$code"
}

# INVARIANT 2: every restore/anonymize/write command must target the ephemeral container.
# Refuse to proceed if the target is anything else (in particular the prod container).
assert_ephemeral_target() {
  local target="$1"
  if [[ "$target" != "$EPHEMERAL_PG_CONTAINER" ]]; then
    die 11 "refusing to write to non-ephemeral container '$target' (expected '$EPHEMERAL_PG_CONTAINER')"
  fi
  # PG_CONTAINER is sourced from .env (SC2153 is a false positive here).
  # shellcheck disable=SC2153
  if [[ "$target" == "$PG_CONTAINER" ]]; then
    die 11 "refusing to write to the PRODUCTION container '$PG_CONTAINER'"
  fi
}

# --- Cleanup (runs on ANY exit: success or failure) ------------------------------------------

LOCAL_STAGE=""

# Invoked indirectly via 'trap cleanup EXIT' (SC2329 false positive).
# shellcheck disable=SC2329
cleanup() {
  # Tear down the ephemeral container so no clone of prod data lingers, and remove all temp
  # files on both hosts. Best-effort: never let cleanup itself abort the trap.
  #
  # Defence in depth: only force-remove the ephemeral container if its name is distinct
  # from every production container. The up-front guard already rejects such collisions,
  # but cleanup must never run "docker rm -f" against a prod container even if reached via
  # an unexpected path.
  if [[ "$EPHEMERAL_PG_CONTAINER" != "$PG_CONTAINER" \
     && "$EPHEMERAL_PG_CONTAINER" != "$INFLUX_CONTAINER" \
     && "$EPHEMERAL_PG_CONTAINER" != "$APP_CONTAINER" ]]; then
    prod "docker rm -f '$EPHEMERAL_PG_CONTAINER'" >/dev/null 2>&1 || true
  fi
  prod "docker exec '$INFLUX_CONTAINER' rm -rf /tmp/influx-backup" >/dev/null 2>&1 || true
  if [[ -n "$LOCAL_STAGE" && -d "$LOCAL_STAGE" ]]; then
    rm -rf "$LOCAL_STAGE" || true
  fi
}
trap cleanup EXIT

# --- Workspace -------------------------------------------------------------------------------

# Local staging directory; the bundle is assembled here and only moved to OUTPUT_DIR on success.
# Nothing is staged on the prod host: data is streamed prod-container -> ephemeral-container or
# prod-container -> workstation via "docker exec" pipes (see the IMPLEMENTATION NOTE above).
LOCAL_STAGE="$(mktemp -d "${TMPDIR:-/tmp}/conluz-snapshot.XXXXXX")"
mkdir -p "$LOCAL_STAGE/bundle"

# =============================================================================================
# Step 1: Spin the EPHEMERAL Postgres  (on the prod host)
# Why (invariant 2): isolates ALL writes from prod. Distinct name; the image major version
# matches prod so pg_restore is compatible. No port is published: every interaction uses
# "docker exec", so the transient un-anonymized clone is never reachable over TCP. It is
# started first so the prod dump can be piped straight into it (no host temp file).
# =============================================================================================
# PG_VERSION is sourced from .env (SC2153 is a false positive here).
# shellcheck disable=SC2153
echo ">> [1/6] Starting ephemeral Postgres ($EPHEMERAL_PG_CONTAINER, postgres:$PG_VERSION)..."
assert_ephemeral_target "$EPHEMERAL_PG_CONTAINER"
prod "docker rm -f '$EPHEMERAL_PG_CONTAINER'" >/dev/null 2>&1 || true
prod "docker run -d --name '$EPHEMERAL_PG_CONTAINER' \
        -e POSTGRES_PASSWORD='$EPHEMERAL_PG_PASSWORD' \
        -e POSTGRES_DB='$PG_DB' \
        'postgres:$PG_VERSION'" >/dev/null \
  || die 11 "could not start ephemeral Postgres container"

# Wait until the ephemeral instance accepts connections.
echo "   waiting for ephemeral Postgres to become ready..."
ready=false
for _ in $(seq 1 60); do
  if prod "docker exec '$EPHEMERAL_PG_CONTAINER' pg_isready -U '$EPHEMERAL_PG_USER' -q" >/dev/null 2>&1; then
    ready=true
    break
  fi
  sleep 1
done
[[ "$ready" == true ]] || die 11 "ephemeral Postgres did not become ready in time"

# =============================================================================================
# Step 2: Stream prod pg_dump -> ephemeral pg_restore, then anonymize  (EPHEMERAL ONLY writes)
# Why: a custom-format (-Fc) dump is the source of truth for the relational clone, and we
# pseudonymize before anything leaves the prod host. The dump (READ-ONLY on prod) is piped
# straight into pg_restore on the ephemeral container -- both on the prod host, so the raw PII
# dump never touches the prod filesystem and we avoid "docker cp" (blocked by the confined snap
# Docker). "set -o pipefail" on the remote pipeline makes a pg_dump failure abort the run too,
# not just a pg_restore failure.
# =============================================================================================
echo ">> [2/6] Streaming prod pg_dump -> ephemeral pg_restore, then anonymizing..."
assert_ephemeral_target "$EPHEMERAL_PG_CONTAINER"
prod "set -o pipefail; docker exec '$PG_CONTAINER' pg_dump -Fc -U '$PG_USER' '$PG_DB' \
        | docker exec -i '$EPHEMERAL_PG_CONTAINER' pg_restore --no-owner --no-privileges \
            -U '$EPHEMERAL_PG_USER' -d '$PG_DB'" \
  || die 11 "stream pg_dump | pg_restore into ephemeral failed"

# Stream anonymize.sql straight into psql with ON_ERROR_STOP so any failure aborts the run
# (no half-anonymized data is ever bundled). No temp file/copy needed.
assert_ephemeral_target "$EPHEMERAL_PG_CONTAINER"
prod "docker exec -i '$EPHEMERAL_PG_CONTAINER' psql -v ON_ERROR_STOP=1 \
        -U '$EPHEMERAL_PG_USER' -d '$PG_DB'" < "$SCRIPT_DIR/anonymize.sql" \
  || die 11 "anonymization (anonymize.sql) failed"

# =============================================================================================
# Step 3: Re-dump the CLEANED database  (this is the shippable artifact)
# Why: only this anonymized dump is transferred; it is streamed over SSH stdout to the
# workstation (no host temp file, no "docker cp").
# =============================================================================================
echo ">> [3/6] Re-dumping anonymized database -> postgres.dump..."
prod "docker exec '$EPHEMERAL_PG_CONTAINER' pg_dump -Fc \
        -U '$EPHEMERAL_PG_USER' '$PG_DB'" > "$LOCAL_STAGE/bundle/postgres.dump" \
  || die 11 "re-dump of anonymized database failed"
[[ -s "$LOCAL_STAGE/bundle/postgres.dump" ]] || die 11 "anonymized dump is empty"

# =============================================================================================
# Step 4: InfluxDB 1.8 backup  (READ-ONLY on prod)
# Why: time-series telemetry keyed by the CUPS tag. Portable backup; tags are NOT rewritten so
# the CUPS join key stays aligned with the (retained) CUPS in postgres.
# "influxd backup" must write to a directory, so it writes to the container's OWN /tmp (a
# container path -- the snap confinement only restricts HOST paths, so this is fine). The
# backup directory is then streamed out of the container via "tar -cf -" piped over SSH to a
# matching "tar -xf -" on the workstation -- no "docker cp", no host temp file, no scp.
# =============================================================================================
echo ">> [4/6] Backing up production InfluxDB (read-only) and streaming it out..."
prod "docker exec '$INFLUX_CONTAINER' rm -rf /tmp/influx-backup" >/dev/null 2>&1 || true
# No -host flag: let influxd backup use its built-in default RPC endpoint (localhost:8088), the
# same way deploy/backup_influxdb.sh does. Passing -host explicitly (even the default value) is
# rejected by the 1.8 RPC service with "invalid metadata blob".
prod "docker exec '$INFLUX_CONTAINER' influxd backup -portable \
        -database '$INFLUX_DB' /tmp/influx-backup" \
  || die 20 "influxd backup failed"
mkdir -p "$LOCAL_STAGE/bundle/influx"
prod "docker exec '$INFLUX_CONTAINER' tar -C /tmp/influx-backup -cf - ." \
  | tar -C "$LOCAL_STAGE/bundle/influx" -xf - \
  || die 20 "could not stream InfluxDB backup to workstation"

# =============================================================================================
# Step 5: Manifest
# Why: provenance for the bundle so consumers know what they restored.
# =============================================================================================
echo ">> [5/6] Writing manifest.json..."
APP_IMAGE="$(prod "docker inspect --format '{{.Config.Image}}' '$APP_CONTAINER'" 2>/dev/null || echo "unknown")"
APP_VERSION="${APP_IMAGE#*:}"
[[ "$APP_VERSION" == "$APP_IMAGE" ]] && APP_VERSION="unknown"
APP_COMMIT="unknown"
if [[ "$APP_VERSION" == *-* ]]; then
  APP_COMMIT="${APP_VERSION##*-}"
fi
LIQUIBASE_STATE="$(prod "docker exec '$EPHEMERAL_PG_CONTAINER' psql -tAc \
  'SELECT count(*) FROM databasechangelog' -U '$EPHEMERAL_PG_USER' -d '$PG_DB'" 2>/dev/null | tr -d '[:space:]')"
[[ -z "$LIQUIBASE_STATE" ]] && LIQUIBASE_STATE="unknown"

cat > "$LOCAL_STAGE/bundle/manifest.json" <<EOF
{
  "schema_version": 1,
  "created_utc": "$TIMESTAMP",
  "source_host": "$PROD_SSH_HOST",
  "app_image": "$APP_IMAGE",
  "app_version": "$APP_VERSION",
  "app_commit": "$APP_COMMIT",
  "postgres": {
    "database": "$PG_DB",
    "image_version": "$PG_VERSION",
    "liquibase_changesets_applied": "$LIQUIBASE_STATE",
    "anonymized": true
  },
  "influxdb": {
    "database": "$INFLUX_DB",
    "format": "portable",
    "cups_tags_rewritten": false
  }
}
EOF

# =============================================================================================
# Step 6: Assemble + transfer  (finalize only on full success)
# Why (invariant 5): no partial bundles. The tarball appears in OUTPUT_DIR only if every prior
# step succeeded.
# =============================================================================================
echo ">> [6/6] Assembling bundle..."
tar -czf "$LOCAL_STAGE/$BUNDLE_NAME.tar.gz" -C "$LOCAL_STAGE/bundle" . \
  || die 30 "could not create bundle tarball"

mkdir -p "$OUTPUT_DIR" || die 30 "could not create output directory '$OUTPUT_DIR'"
mv "$LOCAL_STAGE/$BUNDLE_NAME.tar.gz" "$OUTPUT_DIR/$BUNDLE_NAME.tar.gz" \
  || die 30 "could not move bundle to '$OUTPUT_DIR'"

echo "OK: snapshot written to $OUTPUT_DIR/$BUNDLE_NAME.tar.gz"
exit 0

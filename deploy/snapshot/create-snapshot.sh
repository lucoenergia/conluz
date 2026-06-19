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
# Exit codes:
#   0  success
#   2  usage / missing configuration
#   10 PostgreSQL dump (prod, read-only) failed
#   11 ephemeral restore / anonymization / re-dump failed
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
  INFLUX_CONTAINER INFLUX_DB INFLUX_BACKUP_HOST
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

REMOTE_TMP=""
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
  if [[ -n "$REMOTE_TMP" ]]; then
    prod "rm -rf '$REMOTE_TMP'" >/dev/null 2>&1 || true
  fi
  if [[ -n "$LOCAL_STAGE" && -d "$LOCAL_STAGE" ]]; then
    rm -rf "$LOCAL_STAGE" || true
  fi
}
trap cleanup EXIT

# --- Workspace -------------------------------------------------------------------------------

# Temp working directory on the PROD host (raw PII dump and influx backup live here; never
# transferred as-is).
REMOTE_TMP="$(prod 'mktemp -d /tmp/conluz-snapshot.XXXXXX')" \
  || die 30 "could not create remote temp directory on prod host"
# Local staging directory; the bundle is assembled here and only moved to OUTPUT_DIR on success.
LOCAL_STAGE="$(mktemp -d "${TMPDIR:-/tmp}/conluz-snapshot.XXXXXX")"
mkdir -p "$LOCAL_STAGE/bundle"

# =============================================================================================
# Step 1: PostgreSQL dump  (READ-ONLY on prod)
# Why: a custom-format (-Fc) dump enables selective/parallel restore later and is the source of
# truth for the relational clone. The raw dump still contains PII, so it stays on the prod host.
# =============================================================================================
echo ">> [1/7] Dumping production PostgreSQL (read-only)..."
prod "docker exec '$PG_CONTAINER' pg_dump -Fc -U '$PG_USER' '$PG_DB' > '$REMOTE_TMP/raw.dump'" \
  || die 10 "pg_dump of production database failed"

# =============================================================================================
# Step 2: Spin the EPHEMERAL Postgres  (on the prod host)
# Why (invariant 2): isolates ALL writes from prod. Distinct name; the image major version
# matches prod so pg_restore is compatible. No port is published: every interaction uses
# "docker exec", so the transient un-anonymized clone is never reachable over TCP.
# =============================================================================================
# PG_VERSION is sourced from .env (SC2153 is a false positive here).
# shellcheck disable=SC2153
echo ">> [2/7] Starting ephemeral Postgres ($EPHEMERAL_PG_CONTAINER, postgres:$PG_VERSION)..."
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
# Step 3: Restore + anonymize  (EPHEMERAL ONLY)
# Why: pseudonymize before anything leaves the prod host. The PII dump is restored into the
# isolated clone, then anonymize.sql overwrites member identity columns in place.
# =============================================================================================
echo ">> [3/7] Restoring into ephemeral and anonymizing..."
assert_ephemeral_target "$EPHEMERAL_PG_CONTAINER"
prod "docker cp '$REMOTE_TMP/raw.dump' '$EPHEMERAL_PG_CONTAINER:/tmp/raw.dump'" \
  || die 11 "could not copy raw dump into ephemeral container"
prod "docker exec '$EPHEMERAL_PG_CONTAINER' pg_restore --no-owner --no-privileges \
        -U '$EPHEMERAL_PG_USER' -d '$PG_DB' /tmp/raw.dump" \
  || die 11 "pg_restore into ephemeral database failed"

# Stream anonymize.sql straight into psql with ON_ERROR_STOP so any failure aborts the run
# (no half-anonymized data is ever bundled). No temp file/copy needed.
assert_ephemeral_target "$EPHEMERAL_PG_CONTAINER"
prod "docker exec -i '$EPHEMERAL_PG_CONTAINER' psql -v ON_ERROR_STOP=1 \
        -U '$EPHEMERAL_PG_USER' -d '$PG_DB'" < "$SCRIPT_DIR/anonymize.sql" \
  || die 11 "anonymization (anonymize.sql) failed"

# =============================================================================================
# Step 4: Re-dump the CLEANED database  (this is the shippable artifact)
# Why: the raw PII dump never leaves the prod host; only this anonymized dump is transferred.
# =============================================================================================
echo ">> [4/7] Re-dumping anonymized database -> postgres.dump..."
prod "docker exec '$EPHEMERAL_PG_CONTAINER' pg_dump -Fc \
        -U '$EPHEMERAL_PG_USER' '$PG_DB'" > "$LOCAL_STAGE/bundle/postgres.dump" \
  || die 11 "re-dump of anonymized database failed"
[[ -s "$LOCAL_STAGE/bundle/postgres.dump" ]] || die 11 "anonymized dump is empty"

# =============================================================================================
# Step 5: InfluxDB 1.8 backup  (READ-ONLY on prod)
# Why: time-series telemetry keyed by the CUPS tag. Portable backup; tags are NOT rewritten so
# the CUPS join key stays aligned with the (retained) CUPS in postgres.
# =============================================================================================
echo ">> [5/7] Backing up production InfluxDB (read-only)..."
prod "docker exec '$INFLUX_CONTAINER' rm -rf /tmp/influx-backup" >/dev/null 2>&1 || true
prod "docker exec '$INFLUX_CONTAINER' influxd backup -portable \
        -host '$INFLUX_BACKUP_HOST' -database '$INFLUX_DB' /tmp/influx-backup" \
  || die 20 "influxd backup failed"
prod "docker cp '$INFLUX_CONTAINER:/tmp/influx-backup' '$REMOTE_TMP/influx'" \
  || die 20 "could not copy InfluxDB backup out of container"
scp -q -r "$PROD_SSH_HOST:$REMOTE_TMP/influx" "$LOCAL_STAGE/bundle/" \
  || die 20 "could not transfer InfluxDB backup to workstation"

# =============================================================================================
# Step 6: Manifest
# Why: provenance for the bundle so consumers know what they restored.
# =============================================================================================
echo ">> [6/7] Writing manifest.json..."
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
# Step 7: Assemble + transfer  (finalize only on full success)
# Why (invariant 5): no partial bundles. The tarball appears in OUTPUT_DIR only if every prior
# step succeeded.
# =============================================================================================
echo ">> [7/7] Assembling bundle..."
tar -czf "$LOCAL_STAGE/$BUNDLE_NAME.tar.gz" -C "$LOCAL_STAGE/bundle" . \
  || die 30 "could not create bundle tarball"

mkdir -p "$OUTPUT_DIR" || die 30 "could not create output directory '$OUTPUT_DIR'"
mv "$LOCAL_STAGE/$BUNDLE_NAME.tar.gz" "$OUTPUT_DIR/$BUNDLE_NAME.tar.gz" \
  || die 30 "could not move bundle to '$OUTPUT_DIR'"

echo "OK: snapshot written to $OUTPUT_DIR/$BUNDLE_NAME.tar.gz"
exit 0

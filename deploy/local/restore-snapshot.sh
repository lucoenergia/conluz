#!/usr/bin/env bash
#
# restore-snapshot.sh — restore a Phase 1 snapshot bundle into the LOCAL Postgres + InfluxDB
# stack, reusing the shared deploy/restore_postgres.sh and deploy/restore_influxdb.sh scripts.
#
# This operation is DESTRUCTIVE (it drops and recreates databases). It is fenced behind a
# local-only guardrail gate and defaults to a no-op dry-run; destructive work requires --execute.
#
# Exit codes:
#    2  usage / bad arguments
#   10  guardrail abort (the target is not provably a local throwaway stack)
#   20  bundle / manifest validation failure (or local stack not running)
#   30  Postgres restore failure
#   40  InfluxDB restore failure
#
set -euo pipefail

# --- Paths -------------------------------------------------------------------------------------
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
ENV_FILE="$SCRIPT_DIR/.env"
PROD_ENV_FILE="$DEPLOY_DIR/snapshot/.env"   # NEVER sourced — only referenced to assert we don't.

# --- Mutable globals cleaned up by the trap ----------------------------------------------------
UNPACK_DIR=""
SENTINEL_DUMP=""

cleanup() {
  [[ -n "$UNPACK_DIR" && -d "$UNPACK_DIR" ]] && rm -rf "$UNPACK_DIR"
  [[ -n "$SENTINEL_DUMP" && -f "$DEPLOY_DIR/$SENTINEL_DUMP" ]] && rm -f "$DEPLOY_DIR/$SENTINEL_DUMP"
  return 0
}
trap cleanup EXIT

die() { local code="$1"; shift; echo "❌ $*" >&2; exit "$code"; }
info() { echo "▶ $*"; }

# --- Argument parsing --------------------------------------------------------------------------
EXECUTE="${FORCE:-0}"   # FORCE=1 is an alias for --execute
BUNDLE_ARG=""
for arg in "$@"; do
  case "$arg" in
    --execute) EXECUTE=1 ;;
    --dry-run) EXECUTE=0 ;;
    -h|--help)
      echo "Usage: $0 [--dry-run|--execute] [path/to/snapshot-<UTC>.tar.gz]"
      echo "Default is --dry-run (no destructive action). FORCE=1 is an alias for --execute."
      exit 0 ;;
    -*) die 2 "Unknown option: $arg" ;;
    *)  [[ -n "$BUNDLE_ARG" ]] && die 2 "Only one bundle path may be given"; BUNDLE_ARG="$arg" ;;
  esac
done

# --- 1. Load local environment -----------------------------------------------------------------
[[ -f "$ENV_FILE" ]] || die 10 "Missing $ENV_FILE (copy .env.example to .env). Refusing to run."
set -o allexport
# shellcheck source=/dev/null
source "$ENV_FILE"
set +o allexport

require() { [[ -n "${!1:-}" ]] || die 10 "Required variable '$1' is unset in $ENV_FILE"; }
require CONLUZ_ENV
require BUNDLE_DIR
require SPRING_DATASOURCE_URL
require SPRING_INFLUXDB_URL
require SPRING_INFLUXDB_DATABASE
require CONLUZ_JWT_SECRET_KEY
require LOCAL_POSTGRES_CONTAINER
require LOCAL_INFLUX_CONTAINER
require POSTGRES_SUPERUSER
require POSTGRES_DB
require APP_DB_USER

CONLUZ_PROD_HOST_DENYLIST="${CONLUZ_PROD_HOST_DENYLIST:-lucobot1 conluz-prod}"
POSTGRES_MAJOR_EXPECTED="${POSTGRES_MAJOR_EXPECTED:-16}"
ALLOWED_DOCKER_CONTEXTS="${ALLOWED_DOCKER_CONTEXTS:-default desktop-linux}"

# --- URL parsing helpers -----------------------------------------------------------------------
url_host() {  # extract host from scheme://host[:port][/...] or jdbc:scheme://host[:port]/db
  local url="$1" rest
  rest="${url#*://}"; rest="${rest%%/*}"; rest="${rest%%\?*}"
  printf '%s' "${rest%%:*}"
}
url_port() {
  local url="$1" rest
  rest="${url#*://}"; rest="${rest%%/*}"; rest="${rest%%\?*}"
  case "$rest" in *:*) printf '%s' "${rest##*:}";; *) printf '%s' "" ;; esac
}

is_loopback_host() {  # 0 if host is loopback, by literal allowlist then DNS resolution (fail closed)
  local host="$1" ip
  case "$host" in
    127.0.0.1|::1|localhost) return 0 ;;
  esac
  command -v getent >/dev/null 2>&1 || return 1
  while read -r ip _; do
    case "$ip" in 127.*|::1) ;; *) return 1 ;; esac
  done < <(getent ahosts "$host" 2>/dev/null)
  # If getent produced no output, the host is unresolvable -> fail closed.
  getent ahosts "$host" >/dev/null 2>&1
}

# --- 2. Guardrail gate (fail closed) -----------------------------------------------------------
guardrails() {
  info "Running guardrail gate (local-only, fail-closed)..."

  # Invariant 4 — explicit local marker (exact match).
  [[ "${CONLUZ_ENV}" == "local" ]] || die 10 "CONLUZ_ENV must be exactly 'local' (got '${CONLUZ_ENV:-}')."

  # Invariant 6 — local credentials only: never source the prod/snapshot env file.
  # (We simply never read $PROD_ENV_FILE; assert it isn't the file we loaded.)
  [[ "$ENV_FILE" != "$PROD_ENV_FILE" ]] || die 10 "Refusing to use the production snapshot env file."

  local pg_host influx_host
  pg_host="$(url_host "$SPRING_DATASOURCE_URL")"
  influx_host="$(url_host "$SPRING_INFLUXDB_URL")"

  # Invariant 1 — local-only target (loopback, resolvable).
  is_loopback_host "$pg_host"     || die 10 "Postgres host '$pg_host' is not loopback/unresolvable."
  is_loopback_host "$influx_host" || die 10 "InfluxDB host '$influx_host' is not loopback/unresolvable."

  # Invariant 2 — production denylist (host names, ports and container names).
  local token hay="$pg_host $influx_host $SPRING_DATASOURCE_URL $SPRING_INFLUXDB_URL $LOCAL_POSTGRES_CONTAINER $LOCAL_INFLUX_CONTAINER"
  for token in $CONLUZ_PROD_HOST_DENYLIST; do
    [[ -z "$token" ]] && continue
    case "$hay" in
      *"$token"*) die 10 "Target matches production denylist entry '$token'. Aborting." ;;
    esac
  done

  # Invariant 3 — no remote Docker context / no SSH.
  local ctx endpoint
  ctx="$(docker context show 2>/dev/null)" || die 10 "Could not determine the Docker context."
  case " $ALLOWED_DOCKER_CONTEXTS " in
    *" $ctx "*) ;;
    *) die 10 "Docker context '$ctx' is not in the local allowlist ($ALLOWED_DOCKER_CONTEXTS)." ;;
  esac
  endpoint="$(docker context inspect "$ctx" --format '{{.Endpoints.docker.Host}}' 2>/dev/null || true)"
  case "$endpoint" in
    unix://*|npipe://*|"") ;;  # local engine (or older docker without the field)
    *) die 10 "Docker endpoint '$endpoint' is not local (ssh:// / tcp:// rejected)." ;;
  esac

  RESOLVED_PG_HOST="$pg_host"
  RESOLVED_INFLUX_HOST="$influx_host"
  RESOLVED_CTX="$ctx"
  RESOLVED_ENDPOINT="$endpoint"
}

# --- 3. Bundle selection + manifest validation -------------------------------------------------
select_bundle() {
  if [[ -n "$BUNDLE_ARG" ]]; then
    BUNDLE="$BUNDLE_ARG"
  else
    local dir="$BUNDLE_DIR"
    [[ "$dir" = /* ]] || dir="$SCRIPT_DIR/$dir"
    # Newest bundle by mtime; find handles non-alphanumeric names better than ls.
    BUNDLE="$(find "$dir" -maxdepth 1 -name 'snapshot-*.tar.gz' -printf '%T@ %p\n' 2>/dev/null \
      | sort -rn | head -1 | cut -d' ' -f2-)"
    [[ -n "$BUNDLE" ]] || die 20 "No snapshot-*.tar.gz found in '$dir' (and no bundle path given)."
  fi
  [[ -f "$BUNDLE" ]] || die 20 "Bundle not found: $BUNDLE"
}

manifest_field() {  # $1=file $2=jq-path (e.g. .postgres.image_version)
  local file="$1" path="$2" key
  if command -v jq >/dev/null 2>&1; then
    jq -r "$path // empty" "$file"
    return
  fi
  key="${path##*.}"
  grep -oE "\"$key\"[[:space:]]*:[[:space:]]*(\"[^\"]*\"|[^,}[:space:]]+)" "$file" \
    | head -1 | sed -E 's/.*:[[:space:]]*"?([^"]*)"?$/\1/'
}

validate_and_unpack() {
  UNPACK_DIR="$(mktemp -d "${TMPDIR:-/tmp}/conluz-local-restore.XXXXXX")"
  info "Unpacking bundle into $UNPACK_DIR ..."
  tar -xzf "$BUNDLE" -C "$UNPACK_DIR" || die 20 "Could not extract bundle: $BUNDLE"

  [[ -f "$UNPACK_DIR/postgres.dump" ]] || die 20 "Bundle missing postgres.dump"
  [[ -d "$UNPACK_DIR/influx" ]]        || die 20 "Bundle missing influx/ directory"
  [[ -f "$UNPACK_DIR/manifest.json" ]] || die 20 "Bundle missing manifest.json"

  local pg_ver influx_fmt pg_major
  pg_ver="$(manifest_field "$UNPACK_DIR/manifest.json" .postgres.image_version)"
  influx_fmt="$(manifest_field "$UNPACK_DIR/manifest.json" .influxdb.format)"
  MANIFEST_CREATED="$(manifest_field "$UNPACK_DIR/manifest.json" .created_utc)"
  MANIFEST_APPVER="$(manifest_field "$UNPACK_DIR/manifest.json" .app_version)"
  MANIFEST_COMMIT="$(manifest_field "$UNPACK_DIR/manifest.json" .app_commit)"

  pg_major="${pg_ver%%.*}"
  [[ "$pg_major" == "$POSTGRES_MAJOR_EXPECTED" ]] \
    || die 20 "Postgres major mismatch: bundle '$pg_ver' vs expected '$POSTGRES_MAJOR_EXPECTED'."
  [[ "$influx_fmt" == "portable" ]] \
    || die 20 "InfluxDB backup format must be 'portable' (got '$influx_fmt')."
}

# --- 4. Destructive reset + restore via the shared scripts -------------------------------------
assert_stack_running() {
  local names
  names="$(docker ps --format '{{.Names}}')"
  grep -qx "$LOCAL_POSTGRES_CONTAINER" <<<"$names" || die 20 "Container '$LOCAL_POSTGRES_CONTAINER' is not running. Run 'make up' first."
  grep -qx "$LOCAL_INFLUX_CONTAINER" <<<"$names"   || die 20 "Container '$LOCAL_INFLUX_CONTAINER' is not running. Run 'make up' first."
}

restore_postgres() {
  info "Recreating local Postgres database '$POSTGRES_DB' for a clean slate..."
  docker exec -i "$LOCAL_POSTGRES_CONTAINER" psql -U "$POSTGRES_SUPERUSER" -d postgres -v ON_ERROR_STOP=1 <<SQL || die 30 "Failed to recreate database"
SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '$POSTGRES_DB' AND pid <> pg_backend_pid();
DROP DATABASE IF EXISTS "$POSTGRES_DB";
CREATE DATABASE "$POSTGRES_DB";
GRANT ALL PRIVILEGES ON DATABASE "$POSTGRES_DB" TO "$APP_DB_USER";
SQL

  # The shared script concatenates /tmp/<arg>, so the argument must be a bare filename with no
  # slashes, and it `source .env` from its own directory — hence the copy into DEPLOY_DIR and the
  # subshell cd.
  SENTINEL_DUMP="deploy-local-restore-$$.dump"
  cp "$UNPACK_DIR/postgres.dump" "$DEPLOY_DIR/$SENTINEL_DUMP"

  info "Restoring Postgres via deploy/restore_postgres.sh ..."
  # pg_restore --clean (without --if-exists) emits benign "does not exist" errors on a fresh DB and
  # may exit non-zero; we verify success below instead of trusting the exit code.
  ( cd "$DEPLOY_DIR" && ./restore_postgres.sh "$SENTINEL_DUMP" ) || true

  docker exec -i "$LOCAL_POSTGRES_CONTAINER" psql -U "$POSTGRES_SUPERUSER" -d "$POSTGRES_DB" \
    -c "GRANT ALL ON SCHEMA public TO \"$APP_DB_USER\";" >/dev/null 2>&1 || true

  local tables
  tables="$(docker exec -i "$LOCAL_POSTGRES_CONTAINER" psql -U "$POSTGRES_SUPERUSER" -d "$POSTGRES_DB" \
    -tAc "SELECT count(*) FROM information_schema.tables WHERE table_schema='public';" | tr -d '[:space:]')"
  [[ "${tables:-0}" -gt 0 ]] || die 30 "Postgres restore verification failed (0 public tables present)."
  info "Postgres restore verified ($tables public tables)."
}

restore_influx() {
  info "Restoring InfluxDB via deploy/restore_influxdb.sh ..."
  # Sentinel container names guarantee the shared script never stops/starts a real app container.
  (
    cd "$DEPLOY_DIR" &&
    INFLUX_CONTAINER="$LOCAL_INFLUX_CONTAINER" \
    CONLUZ_CONTAINER="conluz-local-noop" \
    TELEGRAF_CONTAINER="telegraf-local-noop" \
    ./restore_influxdb.sh "$UNPACK_DIR/influx"
  ) || die 40 "InfluxDB restore failed."
  info "InfluxDB restore completed."
}

# --- Orchestration -----------------------------------------------------------------------------
guardrails
select_bundle
validate_and_unpack

cat <<SUMMARY

================ PRE-FLIGHT SUMMARY ================
  Mode            : $([[ "$EXECUTE" == 1 ]] && echo 'EXECUTE (DESTRUCTIVE)' || echo 'dry-run (no changes)')
  Docker context  : $RESOLVED_CTX  ($RESOLVED_ENDPOINT)
  Postgres target : $LOCAL_POSTGRES_CONTAINER  host=$RESOLVED_PG_HOST  db=$POSTGRES_DB
  InfluxDB target : $LOCAL_INFLUX_CONTAINER  host=$RESOLVED_INFLUX_HOST  db=$SPRING_INFLUXDB_DATABASE
  Bundle          : $BUNDLE
  Bundle identity : created=$MANIFEST_CREATED  app=$MANIFEST_APPVER  commit=$MANIFEST_COMMIT
  Will DROP + RESTORE both databases listed above.
===================================================

SUMMARY

if [[ "$EXECUTE" != 1 ]]; then
  info "Dry-run: no database was touched. Re-run with --execute (or FORCE=1) to perform the restore."
  exit 0
fi

assert_stack_running
restore_postgres
restore_influx
info "✅ Local restore complete. Start the branch with 'make run' so Liquibase migrates on startup."

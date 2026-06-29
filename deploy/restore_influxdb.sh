#!/bin/bash
set -e

#
# This is a bash script designed to restore a database from a backup in an InfluxDB instance that is running in a Docker container.
#

# Load environment variables from .env file
set -o allexport # Automatically exports all variables defined after this line into the environment.
# shellcheck source=/dev/null
source .env # Loads the variables from the .env file.
set +o allexport # Disables automatic export of variables.

# Configuration
TMP_RESTORE_DIR="./backups/influxdb_restore"
CONTAINER_TMP_RESTORE_DIR="/tmp/influxdb_restore"

# Overridable targets. Defaults reproduce the production/DR values, so the DR path behaves
# exactly as before when these variables are not set. The local data-clone workflow overrides
# them (e.g. CONLUZ_CONTAINER/TELEGRAF_CONTAINER pointing at non-existent names) so that a
# database-only stack never aborts and never touches a real application container.
INFLUX_CONTAINER="${INFLUX_CONTAINER:-influxdb}"
DB="${SPRING_INFLUXDB_DATABASE:-conluz_db}"
CONLUZ_CONTAINER="${CONLUZ_CONTAINER:-conluz}"
TELEGRAF_CONTAINER="${TELEGRAF_CONTAINER:-telegraf}"

# Check input parameter
if [ -z "$1" ]; then
  echo "❌ Error: You must provide the path to the backup directory."
  echo "Usage: $0 /path/to/backup"
  exit 1
fi

SOURCE_BACKUP_DIR="$1"

# Prepare temporary directory
echo "📁 Preparing temporary restore directory..."
rm -rf "$TMP_RESTORE_DIR"
mkdir -p "$TMP_RESTORE_DIR"
cp -r "$SOURCE_BACKUP_DIR"/. "$TMP_RESTORE_DIR"/

# Remove the database (if exists)
echo "🧹 Removing existing database '$DB'..."
docker exec -i "$INFLUX_CONTAINER" influx -username "$INFLUXDB_ADMIN_USER" -password "$INFLUXDB_ADMIN_PASSWORD" -execute "DROP DATABASE $DB" || echo "No database to drop."

# Stop containers that write data in InfluxDB (tolerate absence so a database-only stack works)
echo "🛑 Stopping container '$CONLUZ_CONTAINER'..."
docker stop "$CONLUZ_CONTAINER" 2>/dev/null || true

echo "🛑 Stopping container '$TELEGRAF_CONTAINER'..."
docker stop "$TELEGRAF_CONTAINER" 2>/dev/null || true

echo "⏳ Waiting till services stop..."
sleep 10

# Perform the restore
echo "📦 Restoring database '$DB' from backup..."
docker cp "$TMP_RESTORE_DIR"/ "$INFLUX_CONTAINER":"$CONTAINER_TMP_RESTORE_DIR"
docker exec -i "$INFLUX_CONTAINER" influxd restore -portable -db "$DB" "$CONTAINER_TMP_RESTORE_DIR"

#
# Restore users and retention policies
#
echo "👤 Creating user '$INFLUXDB_CONLUZ_USER'..."
docker exec -i "$INFLUX_CONTAINER" influx -username "${INFLUXDB_ADMIN_USER}" -password "${INFLUXDB_ADMIN_PASSWORD}" -execute "CREATE USER ${INFLUXDB_CONLUZ_USER} WITH PASSWORD '${INFLUXDB_CONLUZ_USER_PASSWORD}'"

echo "🔐 Granting privileges to user '$INFLUXDB_CONLUZ_USER'..."
docker exec -i "$INFLUX_CONTAINER" influx -username "${INFLUXDB_ADMIN_USER}" -password "${INFLUXDB_ADMIN_PASSWORD}" -execute "GRANT ALL ON $DB TO ${INFLUXDB_CONLUZ_USER}"

echo "📐 Creating retention policies..."
docker exec -i "$INFLUX_CONTAINER" influx -username "${INFLUXDB_ADMIN_USER}" -password "${INFLUXDB_ADMIN_PASSWORD}" -execute "CREATE RETENTION POLICY one_month ON $DB DURATION 30d REPLICATION 1"
docker exec -i "$INFLUX_CONTAINER" influx -username "${INFLUXDB_ADMIN_USER}" -password "${INFLUXDB_ADMIN_PASSWORD}" -execute "CREATE RETENTION POLICY one_year ON $DB DURATION 365d REPLICATION 1"
docker exec -i "$INFLUX_CONTAINER" influx -username "${INFLUXDB_ADMIN_USER}" -password "${INFLUXDB_ADMIN_PASSWORD}" -execute "CREATE RETENTION POLICY forever ON $DB DURATION INF REPLICATION 1 DEFAULT"

# Start containers that write data in InfluxDB (tolerate absence so a database-only stack works)
echo "▶️ Starting container '$CONLUZ_CONTAINER'..."
docker start "$CONLUZ_CONTAINER" 2>/dev/null || true

echo "▶️ Starting container '$TELEGRAF_CONTAINER'..."
docker start "$TELEGRAF_CONTAINER" 2>/dev/null || true

echo "✅ Restore completed successfully."

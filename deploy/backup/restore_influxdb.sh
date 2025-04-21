#!/bin/bash
set -e

#
# This is a bash script designed to restore a database from a backup in an InfluxDB instance that is running in a Docker container.
#

# Load environment variables from .env file
set -a # Automatically exports all variables defined after this line into the environment.
source ../.env # Loads the variables from the .env file.
set +a # Disables automatic export of variables.

# Configuration
TMP_RESTORE_DIR="./backups/influxdb_restore"
CONTAINER_TMP_RESTORE_DIR="/tmp/influxdb_restore"

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
echo "🧹 Removing existing database '$SPRING_INFLUXDB_DATABASE'..."
docker exec -it influxdb influx -username "$INFLUXDB_ADMIN_USER" -password "$INFLUXDB_ADMIN_PASSWORD" -execute "DROP DATABASE $SPRING_INFLUXDB_DATABASE" || echo "No database to drop."

# Start containers that write data in InfluxDB
echo "🛑 Stopping container conluz..."
docker stop conluz

echo "🛑 Stopping container telegraf..."
docker stop telegraf

echo "⏳ Waiting till services stop..."
sleep 10

# Perform the restore
echo "📦 Restoring database '$SPRING_INFLUXDB_DATABASE' from backup..."
docker cp "$TMP_RESTORE_DIR"/ influxdb:"$CONTAINER_TMP_RESTORE_DIR"
docker exec -it influxdb influxd restore -portable -db "$SPRING_INFLUXDB_DATABASE" "$CONTAINER_TMP_RESTORE_DIR"

#
# Restore users and retention policies
#
echo "👤 Creating user '$INFLUXDB_CONLUZ_USER'..."
docker exec -it influxdb influx -username "${INFLUXDB_ADMIN_USER}" -password "${INFLUXDB_ADMIN_PASSWORD}" -execute "CREATE USER ${INFLUXDB_CONLUZ_USER} WITH PASSWORD '${INFLUXDB_CONLUZ_USER_PASSWORD}'"

echo "🔐 Granting privileges to user '$INFLUXDB_CONLUZ_USER'..."
docker exec -it influxdb influx -username "${INFLUXDB_ADMIN_USER}" -password "${INFLUXDB_ADMIN_PASSWORD}" -execute "GRANT ALL ON conluz_db TO ${INFLUXDB_CONLUZ_USER}"

echo "📐 Creating retention policies..."
docker exec -it influxdb influx -username "${INFLUXDB_ADMIN_USER}" -password "${INFLUXDB_ADMIN_PASSWORD}" -execute "CREATE RETENTION POLICY one_month ON conluz_db DURATION 30d REPLICATION 1"
docker exec -it influxdb influx -username "${INFLUXDB_ADMIN_USER}" -password "${INFLUXDB_ADMIN_PASSWORD}" -execute "CREATE RETENTION POLICY one_year ON conluz_db DURATION 365d REPLICATION 1"
docker exec -it influxdb influx -username "${INFLUXDB_ADMIN_USER}" -password "${INFLUXDB_ADMIN_PASSWORD}" -execute "CREATE RETENTION POLICY forever ON conluz_db DURATION INF REPLICATION 1 DEFAULT"

# Start containers that write data in InfluxDB
echo "▶️ Starting container conluz..."
docker start conluz

echo "▶️ Starting container telegraf..."
docker start telegraf

echo "✅ Restore completed successfully."

#!/bin/bash

# === Configuration ===
# Overridable targets. Defaults reproduce the production/DR values, so the DR path behaves exactly
# as before when these variables are not set. The local data-clone workflow overrides them to point
# at an isolated stack (e.g. POSTGRES_CONTAINER=conluz-local-postgres).
CONTAINER_NAME="${POSTGRES_CONTAINER:-postgres}"
DB_NAME="${POSTGRES_DB:-conluz_db}"
BACKUP_FILE="$1"  # Path to .sql.gz backup file (passed as argument)
CONTAINER_TMP_RESTORE_DIR="/tmp/"

# Load environment variables from .env file
set -o allexport # Automatically exports all variables defined after this line into the environment.
# shellcheck source=/dev/null
source .env # Loads the variables from the .env file.
set +o allexport # Disables automatic export of variables.

# === Check if file is provided ===
if [ -z "$BACKUP_FILE" ]; then
  echo "❌ Error: You must provide the path to the backup file."
  echo "Usage: $0 /path/to/backup.sql.gz"
  exit 1
fi

# === Check if file exists ===
if [ ! -f "$BACKUP_FILE" ]; then
  echo "Error: file '$BACKUP_FILE' not found."
  exit 1
fi

echo "📁 Copying dump to postgres container '$DB_NAME'..."
docker cp "$BACKUP_FILE" "$CONTAINER_NAME":"$CONTAINER_TMP_RESTORE_DIR"/

echo "🔄 Restoring backup '$BACKUP_FILE' into database '$DB_NAME'..."
docker exec -i "$CONTAINER_NAME" bash -c "pg_restore -v -Fc -v --clean -U '$POSTGRES_USER' -d '$DB_NAME' '$CONTAINER_TMP_RESTORE_DIR$BACKUP_FILE'"

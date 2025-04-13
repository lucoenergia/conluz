#!/bin/bash

# === Configuration ===
CONTAINER_NAME="postgres"
DB_NAME="conluz_db"
BACKUP_FILE="$1"  # Path to .sql.gz backup file (passed as argument)
CONTAINER_TMP_RESTORE_DIR="/tmp/"

# Load environment variables from .env file
set -a # Automatically exports all variables defined after this line into the environment.
source ../.env # Loads the variables from the .env file.
set +a # Disables automatic export of variables.

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

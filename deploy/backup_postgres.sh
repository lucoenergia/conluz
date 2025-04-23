#!/bin/bash

# Configuration
DB_NAME="conluz_db"
TIMESTAMP=$(date +"%Y-%m-%d_%H-%M")
FILENAME="${DB_NAME}_${TIMESTAMP}.dump"
CONTAINER_NAME="postgres"
CONTAINER_TMP_RESTORE_DIR="/tmp/"

# Load environment variables from .env file
set -o allexport # Automatically exports all variables defined after this line into the environment.
source .env # Loads the variables from the .env file.
set +o allexport # Disables automatic export of variables.

# Check input parameter
if [ -z "$1" ]; then
  echo "❌ Error: You must provide the path to the backup directory."
  echo "Usage: $0 /path/to/backup"
  exit 1
fi

BACKUP_DIR="$1"

# Create folder if does not exists yet
mkdir -p "$BACKUP_DIR"

# Execute pg_dump within the container
docker exec -t "$CONTAINER_NAME" pg_dump -Fc -v -f "$CONTAINER_TMP_RESTORE_DIR$FILENAME" -U "$POSTGRES_USER" "$DB_NAME"

# Download backup generated
docker cp "$CONTAINER_NAME:$CONTAINER_TMP_RESTORE_DIR$FILENAME" "$BACKUP_DIR"

# (Optional) Remove backups older than 90 days
find "$BACKUP_DIR" -type f -name "*.dump" -mtime +90 -exec rm {} \;

# Display a success message
echo "Backup done: $BACKUP_DIR/$FILENAME"

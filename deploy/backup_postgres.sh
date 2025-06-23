#!/bin/bash

# Configuration
DB_NAME="conluz_db"
TIMESTAMP=$(date +"%Y-%m-%d_%H-%M")
FILENAME="${DB_NAME}_${TIMESTAMP}.dump"
CONTAINER_NAME="postgres"
CONTAINER_TMP_RESTORE_DIR="/tmp/"

# Check path to backup parameter
if [ -z "$1" ]; then
  echo "❌ Error: You must provide the path to the backup directory."
  echo "Usage: $0 /path/to/backup"
  exit 1
fi

# Check path to backup parameter
if [ -z "$2" ]; then
  echo "❌ Error: You must provide the database username."
  echo "Usage: $0 username"
  exit 1
fi

BACKUP_DIR="$1"
POSTGRES_USER="$2"

# Create folder if does not exists yet
mkdir -p "$BACKUP_DIR"

# Execute pg_dump within the container
docker exec -t "$CONTAINER_NAME" pg_dump -Fc -v -f "$CONTAINER_TMP_RESTORE_DIR$FILENAME" -U "$POSTGRES_USER" "$DB_NAME"

# Download backup generated
docker cp "$CONTAINER_NAME:$CONTAINER_TMP_RESTORE_DIR$FILENAME" backups

# Move the generated dump to the destination backup directory
mv backups/"$FILENAME" "$BACKUP_DIR"

# Compress the generated dump
gzip "$BACKUP_DIR/$FILENAME"

# Remove the temp files
docker exec "$CONTAINER_NAME" rm -rf "$CONTAINER_TMP_RESTORE_DIR$FILENAME"

# Display a success message
echo "Backup done: $BACKUP_DIR/$FILENAME"
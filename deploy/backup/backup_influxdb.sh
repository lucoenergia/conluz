#!/bin/bash

# Define variables
BACKUP_FOLDER="conluz_db_backup_$(date +'%Y%m%d_%H%M%S')"
CONTAINER_NAME="influxdb"  # Name of InfluxDB container
CONTAINER_TMP_BACKUP_DIR="/tmp/backup"

# Load environment variables from .env file
set -a # Automatically exports all variables defined after this line into the environment.
source ../.env # Loads the variables from the .env file.
set +a # Disables automatic export of variables.

# Check input parameter
if [ -z "$1" ]; then
  echo "❌ Error: You must provide the path to the backup directory."
  echo "Usage: $0 /path/to/backup"
  exit 1
fi

BACKUP_DIR="$1"

# Create a directory for backups if not exists yet
mkdir -p "$BACKUP_DIR"

# Launch de backup using the Docker container
docker exec $CONTAINER_NAME influxd backup -portable -database "$SPRING_INFLUXDB_DATABASE" "$CONTAINER_TMP_BACKUP_DIR"

# Compress the backup
docker cp $CONTAINER_NAME:"$CONTAINER_TMP_BACKUP_DIR" backups/"$BACKUP_FOLDER"

mv backups/"$BACKUP_FOLDER" "$BACKUP_DIR/$BACKUP_FOLDER"

# Remove the temp files
docker exec $CONTAINER_NAME rm -rf "$CONTAINER_TMP_BACKUP_DIR"

# Display a success message
echo "Backup done: $BACKUP_DIR/$BACKUP_FOLDER"

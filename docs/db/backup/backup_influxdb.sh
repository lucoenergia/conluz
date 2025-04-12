#!/bin/bash

# Define variables
BACKUP_DIR="/media/data/influxdb/backups"
BACKUP_FOLDER="conluz_db_backup_$(date +'%Y%m%d_%H%M%S')"
CONTAINER_NAME="influxdb"  # Name of InfluxDB container

# Craete a directory for backups if not exists yet
mkdir -p "$BACKUP_DIR"

# Launch de backup using the Docker container
docker exec $CONTAINER_NAME influxd backup -portable -database conluz_db /tmp/backup

# Compress the backup
docker cp $CONTAINER_NAME:/tmp/backup backups/$BACKUP_FOLDER

mv backups/$BACKUP_FOLDER "$BACKUP_DIR/$BACKUP_FOLDER"

# Remove the temp files
docker exec $CONTAINER_NAME rm -rf /tmp/backup

# Display a success message
echo "Backup done: $BACKUP_DIR/$BACKUP_FOLDER"

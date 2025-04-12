#!/bin/bash

# Configuration
BACKUP_DIR="/media/data/postgres/backups"
DB_NAME="conluz_db"
TIMESTAMP=$(date +"%Y-%m-%d_%H-%M")
FILENAME="${DB_NAME}_${TIMESTAMP}.sql.gz"
CONTAINER_NAME="postgres"

# Create folder if does not exists yet
mkdir -p "$BACKUP_DIR"

# Execute pg_dump within the container
docker exec -t "$CONTAINER_NAME" pg_dump -U luzgres "$DB_NAME" | gzip > "$BACKUP_DIR/$FILENAME"

# (Optional) Remove backups older than 90 days
find "$BACKUP_DIR" -type f -name "*.sql.gz" -mtime +90 -exec rm {} \;

# Display a success message
echo "Backup done: $BACKUP_DIR/$FILENAME"

#!/bin/bash

# Script to export data from InfluxDB 1.8
# This exports all measurements from the conluz_db database

set -e

EXPORT_DIR="${EXPORT_DIR:-/tmp/influxdb-export}"
DATABASE="${INFLUXDB_DATABASE:-conluz_db}"
INFLUX_HOST="${INFLUX_HOST:-localhost}"
INFLUX_PORT="${INFLUX_PORT:-8086}"
START_DATE="${START_DATE:-1970-01-01T00:00:00Z}"
END_DATE="${END_DATE:-$(date -u +"%Y-%m-%dT%H:%M:%SZ")}"
EXPORT_FILENAME="${EXPORT_FILENAME:-full_export.lp}"

echo "Starting InfluxDB 1.8 data export..."
echo "Database: $DATABASE"
echo "Export directory: $EXPORT_DIR"
echo "Time range: $START_DATE to $END_DATE"

# Create export directory
mkdir -p "$EXPORT_DIR"

##
# Use influx_inspect for database export with configurable time range
##
echo "Creating database backup using influx_inspect..."
docker exec influxdb influx_inspect export \
    -database "$DATABASE" \
    -datadir /var/lib/influxdb/data \
    -waldir /var/lib/influxdb/wal \
    -out "/tmp/$EXPORT_FILENAME" \
    -start "$START_DATE" \
    -end "$END_DATE"

# Copy the backup file from container to host
docker cp "influxdb:/tmp/$EXPORT_FILENAME" "$EXPORT_DIR/$EXPORT_FILENAME"

echo "Export completed!"
echo "Files created:"
ls -lh "$EXPORT_DIR"

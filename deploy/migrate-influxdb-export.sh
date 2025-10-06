#!/bin/bash

# Script to export data from InfluxDB 1.8
# This exports all measurements from the conluz_db database

set -e

EXPORT_DIR="${EXPORT_DIR:-/tmp/influxdb-export}"
DATABASE="${INFLUXDB_DATABASE:-conluz_db}"
INFLUX_HOST="${INFLUX_HOST:-localhost}"
INFLUX_PORT="${INFLUX_PORT:-8086}"
INFLUX_USER="${INFLUXDB_ADMIN_USER:-admin}"
INFLUX_PASS="${INFLUXDB_ADMIN_PASSWORD:-admin}"

echo "Starting InfluxDB 1.8 data export..."
echo "Database: $DATABASE"
echo "Export directory: $EXPORT_DIR"

# Create export directory
mkdir -p "$EXPORT_DIR"

# List of measurements to export
MEASUREMENTS=(
    "datadis_consumption_kwh"
    "huawei_production_hourly"
    "huawei_production_realtime"
    "omie_prices_kwh"
    "shelly_consumption_kw"
    "shelly_mqtt_power_messages"
)

# Export each measurement
for measurement in "${MEASUREMENTS[@]}"; do
    echo "Exporting measurement: $measurement"

    # Use influx CLI to export data
    influx -host "$INFLUX_HOST" \
           -port "$INFLUX_PORT" \
           -username "$INFLUX_USER" \
           -password "$INFLUX_PASS" \
           -database "$DATABASE" \
           -format csv \
           -execute "SELECT * FROM \"$measurement\"" \
           > "$EXPORT_DIR/${measurement}.csv"

    echo "Exported $measurement to $EXPORT_DIR/${measurement}.csv"
done

# Alternative: Use influx_inspect for full database export
echo "Creating full database backup using influx_inspect..."
CURRENT_TIME=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
docker exec influxdb influx_inspect export \
    -database "$DATABASE" \
    -out "/tmp/full_export.lp" \
    -start 1970-01-01T00:00:00Z \
    -end "$CURRENT_TIME"

# Copy the backup file from container to host
docker cp influxdb:/tmp/full_export.lp "$EXPORT_DIR/full_export.lp"

echo "Export completed!"
echo "Files created:"
ls -lh "$EXPORT_DIR"

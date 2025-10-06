#!/bin/bash

# Script to import data into InfluxDB 3
# This imports line protocol data exported from InfluxDB 1.8

set -e

EXPORT_DIR="${EXPORT_DIR:-/tmp/influxdb-export}"
INFLUX3_HOST="${INFLUX3_HOST:-localhost}"
INFLUX3_PORT="${INFLUX3_PORT:-8181}"
INFLUX3_TOKEN="${INFLUX_SETUP_TOKEN}"
INFLUX3_ORG="${INFLUX_SETUP_ORG:-lucoenergia}"
INFLUX3_BUCKET="${INFLUX_SETUP_BUCKET:-conluz_db}"

echo "Starting InfluxDB 3 data import..."
echo "Source directory: $EXPORT_DIR"
echo "Target: $INFLUX3_HOST:$INFLUX3_PORT"
echo "Bucket: $INFLUX3_BUCKET"
echo "Organization: $INFLUX3_ORG"

# Check if export file exists
if [ ! -f "$EXPORT_DIR/full_export.lp" ]; then
    echo "Error: Export file not found at $EXPORT_DIR/full_export.lp"
    echo "Please run migrate-influxdb-export.sh first"
    exit 1
fi

# Check if influx CLI v2 is available
if ! command -v influx &> /dev/null; then
    echo "Error: influx CLI v2 not found"
    echo "Please install InfluxDB CLI v2 from: https://docs.influxdata.com/influxdb/cloud/tools/influx-cli/"
    exit 1
fi

# Import data using influx CLI v2
echo "Importing line protocol data..."
influx write \
    --host "http://$INFLUX3_HOST:$INFLUX3_PORT" \
    --token "$INFLUX3_TOKEN" \
    --org "$INFLUX3_ORG" \
    --bucket "$INFLUX3_BUCKET" \
    --file "$EXPORT_DIR/full_export.lp" \
    --format lp \
    --precision ns

echo "Import completed successfully!"

# Verify import by checking data points count
echo "Verifying import..."
influx query \
    --host "http://$INFLUX3_HOST:$INFLUX3_PORT" \
    --token "$INFLUX3_TOKEN" \
    --org "$INFLUX3_ORG" \
    "
    SELECT COUNT(*) as count
    FROM \"datadis_consumption_kwh\"
    " || echo "Note: Verification query failed, but data may still be imported"

echo "Migration to InfluxDB 3 completed!"

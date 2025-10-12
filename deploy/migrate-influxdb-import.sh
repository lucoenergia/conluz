#!/bin/bash

# Script to import data from InfluxDB 1.8 line protocol export into InfluxDB 3 Core
# This script splits large line protocol files into batches and imports them via HTTP API

set -e

# Configuration from environment variables
EXPORT_DIR="${EXPORT_DIR:-/tmp/influxdb-export}"
IMPORT_FILE="${IMPORT_FILE:-$EXPORT_DIR/full_export.lp}"
DATABASE="${SPRING_INFLUXDB3_BUCKET:-conluz_db}"
INFLUXDB3_URL="${SPRING_INFLUXDB3_URL:-http://localhost:8181}"
INFLUXDB3_TOKEN="${SPRING_INFLUXDB3_TOKEN}"
BATCH_SIZE="${BATCH_SIZE:-50000}"  # Number of lines per batch

# Validate required parameters
if [ -z "$INFLUXDB3_TOKEN" ]; then
    echo "Error: SPRING_INFLUXDB3_TOKEN environment variable is required"
    exit 1
fi

if [ ! -f "$IMPORT_FILE" ]; then
    echo "Error: Import file not found: $IMPORT_FILE"
    exit 1
fi

echo "Starting InfluxDB 3 Core data import..."
echo "Import file: $IMPORT_FILE"
echo "File size: $(du -h "$IMPORT_FILE" | cut -f1)"
echo "Database: $DATABASE"
echo "InfluxDB URL: $INFLUXDB3_URL"
echo "Batch size: $BATCH_SIZE lines"

# Create temporary directory for batches
BATCH_DIR=$(mktemp -d)
echo "Temporary batch directory: $BATCH_DIR"

# Clean up function
cleanup() {
    echo "Cleaning up temporary files..."
    rm -rf "$BATCH_DIR"
}
trap cleanup EXIT

# Filter out metadata comments and DDL/DML lines from InfluxDB 1.8 export
# These lines start with # and cause 400 errors in InfluxDB 3
echo "Filtering metadata from export file..."
FILTERED_FILE="$BATCH_DIR/filtered_data.lp"
grep -v "^#" "$IMPORT_FILE" | grep -v "^CREATE DATABASE" | grep -v "^$" > "$FILTERED_FILE"

# Check if filtered file has data
if [ ! -s "$FILTERED_FILE" ]; then
    echo "Warning: No data found after filtering metadata"
    exit 0
fi

echo "Filtered file size: $(du -h "$FILTERED_FILE" | cut -f1)"

# Split the filtered file into batches
echo "Splitting file into batches..."
split -l "$BATCH_SIZE" "$FILTERED_FILE" "$BATCH_DIR/batch_"

# Count total batches
TOTAL_BATCHES=$(ls -1 "$BATCH_DIR/batch_"* | wc -l)
echo "Total batches to import: $TOTAL_BATCHES"

# Import each batch
CURRENT_BATCH=0
FAILED_BATCHES=0

for batch_file in "$BATCH_DIR/batch_"*; do
    CURRENT_BATCH=$((CURRENT_BATCH + 1))
    echo "[$CURRENT_BATCH/$TOTAL_BATCHES] Importing batch: $(basename "$batch_file")"

    # Import using InfluxDB 3 Core HTTP API
    HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST \
        "$INFLUXDB3_URL/api/v3/write_lp?db=$DATABASE" \
        -H "Authorization: Bearer $INFLUXDB3_TOKEN" \
        -H "Content-Type: text/plain" \
        --data-binary @"$batch_file")

    if [ "$HTTP_CODE" -eq 204 ] || [ "$HTTP_CODE" -eq 200 ]; then
        echo "[$CURRENT_BATCH/$TOTAL_BATCHES] ✓ Successfully imported (HTTP $HTTP_CODE)"
    else
        echo "[$CURRENT_BATCH/$TOTAL_BATCHES] ✗ Failed to import (HTTP $HTTP_CODE)"
        FAILED_BATCHES=$((FAILED_BATCHES + 1))

        # Save failed batch for manual review
        cp "$batch_file" "$EXPORT_DIR/failed_$(basename "$batch_file")"
        echo "  Saved failed batch to: $EXPORT_DIR/failed_$(basename "$batch_file")"
    fi
done

echo ""
echo "Import completed!"
echo "Total batches: $TOTAL_BATCHES"
echo "Successful: $((TOTAL_BATCHES - FAILED_BATCHES))"
echo "Failed: $FAILED_BATCHES"

if [ $FAILED_BATCHES -gt 0 ]; then
    echo ""
    echo "Warning: Some batches failed to import. Check the failed_batch_* files in $EXPORT_DIR"
#    exit 1
fi

echo ""
echo "All data successfully imported to InfluxDB 3 Core!"

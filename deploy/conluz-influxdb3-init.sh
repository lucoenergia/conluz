#!/bin/bash
set -e

# InfluxDB 3 Core initialization script
# Creates database equivalent to InfluxDB 1.8 setup

echo "Initializing InfluxDB 3 Core..."

# Wait for InfluxDB 3 to be ready
echo "Waiting for InfluxDB 3 Core to be ready..."
until influxdb3 --help > /dev/null 2>&1; do
    echo "Waiting for influxdb3 CLI to be available..."
    sleep 2
done

# Create main database
echo "Creating database: conluz_db"
influxdb3 create database conluz_db

echo "InfluxDB 3 Core initialization completed!"
echo ""
echo "Database created: conluz_db"
echo "Note: InfluxDB 3 Core uses SQL instead of InfluxQL"
echo ""
echo "Key differences from InfluxDB 1.8:"
echo "  - No user management (uses token-based auth via INFLUXDB3_AUTH_TOKEN)"
echo "  - No separate retention policies (set retention per database if needed)"
echo "  - Use SQL for queries: SELECT * FROM measurement WHERE time >= '2024-01-01'"
echo "  - Calendar aggregation: DATE_TRUNC('month', time) for monthly, DATE_TRUNC('year', time) for yearly"

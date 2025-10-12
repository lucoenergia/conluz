# InfluxDB Migration Scripts

This directory contains scripts to migrate data from InfluxDB 1.8 to InfluxDB 3 Core.

## Scripts Overview

### 1. `migrate-influxdb-export.sh`
Exports data from InfluxDB 1.8 using `influx_inspect export`.

**Environment Variables:**
- `EXPORT_DIR` - Directory for export files (default: `/tmp/influxdb-export`)
- `INFLUXDB_DATABASE` - Source database name (default: `conluz_db`)
- `INFLUX_HOST` - InfluxDB 1.8 host (default: `localhost`)
- `INFLUX_PORT` - InfluxDB 1.8 port (default: `8086`)
- `START_DATE` - Export start date in ISO format (default: `1970-01-01T00:00:00Z`)
- `END_DATE` - Export end date in ISO format (default: current date)
- `EXPORT_FILENAME` - Output filename (default: `full_export.lp`)

### 2. `migrate-influxdb-import.sh`
Imports line protocol data into InfluxDB 3 Core via HTTP API. Automatically filters out InfluxDB 1.8 metadata comments (DDL/DML statements) that cause 400 errors in InfluxDB 3.

**Environment Variables:**
- `IMPORT_FILE` - Path to line protocol file to import (default: `$EXPORT_DIR/full_export.lp`)
- `EXPORT_DIR` - Directory containing export files (default: `/tmp/influxdb-export`)
- `SPRING_INFLUXDB3_BUCKET` - Target database/bucket name (default: `conluz_db`)
- `SPRING_INFLUXDB3_URL` - InfluxDB 3 URL (default: `http://localhost:8181`)
- `SPRING_INFLUXDB3_TOKEN` - InfluxDB 3 authentication token (**required**)
- `BATCH_SIZE` - Number of lines per batch (default: `50000`)

### 3. `migrate-influxdb-orchestrator.sh` ⭐
**Recommended:** Orchestrates month-by-month migration for large datasets.

**Environment Variables:**
- `MIGRATION_START_DATE` - Start date for migration (format: `YYYY-MM-DD`, default: `2020-01-01`)
- `MIGRATION_END_DATE` - End date for migration (format: `YYYY-MM-DD`, default: current date)
- `DRY_RUN` - Preview what would be processed without executing (default: `false`)
- All variables from export and import scripts above

## Usage Examples

### Quick Start: Full Migration

```bash
# Set required InfluxDB 3 token
export SPRING_INFLUXDB3_TOKEN="your-influxdb3-token"

# Run migration from 2020 onwards (default)
./migrate-influxdb-orchestrator.sh
```

### Custom Date Range

```bash
# Migrate specific time period
export SPRING_INFLUXDB3_TOKEN="your-token"
export MIGRATION_START_DATE="2023-01-01"
export MIGRATION_END_DATE="2023-12-31"

./migrate-influxdb-orchestrator.sh
```

### Dry Run (Preview)

```bash
# See what would be processed without executing
export DRY_RUN=true
./migrate-influxdb-orchestrator.sh
```

### Custom Configuration

```bash
# Full configuration example
export SPRING_INFLUXDB3_TOKEN="your-token"
export SPRING_INFLUXDB3_URL="http://influxdb3:8181"
export SPRING_INFLUXDB3_BUCKET="my_database"
export MIGRATION_START_DATE="2022-01-01"
export MIGRATION_END_DATE="2024-12-31"
export BATCH_SIZE="100000"
export EXPORT_DIR="/data/influxdb-migration"

./migrate-influxdb-orchestrator.sh
```

### Retry Failed Months

If some months fail, you can retry them by adjusting the date range:

```bash
export SPRING_INFLUXDB3_TOKEN="your-token"
export MIGRATION_START_DATE="2023-06-01"
export MIGRATION_END_DATE="2023-08-31"

./migrate-influxdb-orchestrator.sh
```

## Manual Export/Import (Advanced)

If you need to manually export and import specific data:

### Export Single Month

```bash
export START_DATE="2023-06-01T00:00:00Z"
export END_DATE="2023-06-30T23:59:59Z"
export EXPORT_FILENAME="june_2023.lp"

./migrate-influxdb-export.sh
```

### Import Specific File

```bash
export SPRING_INFLUXDB3_TOKEN="your-token"
export IMPORT_FILE="/tmp/influxdb-export/june_2023.lp"

./migrate-influxdb-import.sh
```

## Features

### Orchestrator Benefits

1. **Month-by-Month Processing**: Splits large datasets into manageable monthly chunks
2. **Progress Tracking**: Shows current progress (Month X of Y)
3. **Automatic Cleanup**: Removes temporary files after each month
4. **Error Handling**: Continues processing if one month fails
5. **Detailed Summary**: Shows successful/failed months with statistics
6. **Dry Run Mode**: Preview migration without executing
7. **Validation**: Checks prerequisites before starting

### Output Example

```
╔════════════════════════════════════════════════════════════════╗
║     InfluxDB 1.8 to InfluxDB 3 Migration Orchestrator         ║
╚════════════════════════════════════════════════════════════════╝

[INFO] Configuration:
  Export directory: /tmp/influxdb-export
  Migration period: 2023-01-01 to 2023-12-31
  InfluxDB 1.8 database: conluz_db
  InfluxDB 3 bucket: conluz_db
  InfluxDB 3 URL: http://localhost:8181
  Batch size: 50000 lines

[INFO] Total months to process: 12

[INFO] ========================================
[INFO] Processing [1/12]: January 2023
[INFO] Range: 2023-01-01 to 2023-01-31
[INFO] ========================================
[INFO] Step 1/3: Exporting data from InfluxDB 1.8...
[SUCCESS] Exported 15MB for January 2023
[INFO] Step 2/3: Importing data to InfluxDB 3...
[SUCCESS] Completed January 2023 in 45s

...

[INFO] ========================================
[INFO] MIGRATION SUMMARY
[INFO] ========================================
Time range: 2023-01-01 to 2023-12-31
Total duration: 12m 34s
Total data processed: 156MB

Successful months: 12
Failed months: 0

[SUCCESS] All months processed successfully!
```

## Troubleshooting

### Common Issues

**1. "InfluxDB 1.8 container is not running"**
```bash
# Start the container
cd deploy
docker compose up -d influxdb
```

**2. "SPRING_INFLUXDB3_TOKEN environment variable is required"**
```bash
# Set the token
export SPRING_INFLUXDB3_TOKEN="your-token-here"
```

**3. Import fails with HTTP 401**
- Check that your InfluxDB 3 token is correct
- Verify the token has write permissions

**4. Import fails with HTTP 404**
- Verify the database/bucket exists in InfluxDB 3
- Check the `SPRING_INFLUXDB3_URL` is correct

**5. Export produces empty files**
- Check that data exists for that time period
- Verify the InfluxDB 1.8 container has access to data directories

**6. First batch fails with 400 error**
- This is now automatically handled by the import script
- The script filters out InfluxDB 1.8 metadata comments (lines starting with `#`, `CREATE DATABASE`, etc.)
- These metadata lines are incompatible with InfluxDB 3's line protocol API

### Performance Tuning

**Adjust batch size** for faster imports (requires more memory):
```bash
export BATCH_SIZE="100000"  # Larger batches = fewer HTTP requests
```

**Adjust export directory** to use faster storage:
```bash
export EXPORT_DIR="/fast-ssd/influxdb-export"
```

## Prerequisites

1. Docker installed and running
2. InfluxDB 1.8 container running (named `influxdb`)
3. InfluxDB 3 Core instance accessible
4. Valid InfluxDB 3 authentication token
5. Sufficient disk space in `EXPORT_DIR` (typically ~20% of source data size)

## Notes

- The orchestrator automatically cleans up temporary files after each month
- Failed batches are saved in `$EXPORT_DIR/failed_batch_*` for manual review
- The migration is additive - running it multiple times will duplicate data
- For very large datasets (>100GB), consider increasing batch size to 100000+
- The scripts assume the InfluxDB 1.8 container is named `influxdb`

#!/bin/bash

# Orchestrator script to migrate data from InfluxDB 1.8 to InfluxDB 3 Core month by month
# This script coordinates the export and import process to handle large datasets efficiently

set -e

# Configuration from environment variables or defaults
EXPORT_DIR="${EXPORT_DIR:-/tmp/influxdb-export}"
MIGRATION_START_DATE="${MIGRATION_START_DATE:-2020-01-01}"
MIGRATION_END_DATE="${MIGRATION_END_DATE:-$(date +"%Y-%m-%d")}"
DRY_RUN="${DRY_RUN:-false}"

# InfluxDB 1.8 configuration
INFLUXDB_DATABASE="${INFLUXDB_DATABASE:-conluz_db}"
INFLUX_HOST="${INFLUX_HOST:-localhost}"
INFLUX_PORT="${INFLUX_PORT:-8086}"

# InfluxDB 3 configuration
SPRING_INFLUXDB3_BUCKET="${SPRING_INFLUXDB3_BUCKET:-conluz_db}"
SPRING_INFLUXDB3_URL="${SPRING_INFLUXDB3_URL:-http://localhost:8181}"
SPRING_INFLUXDB3_TOKEN="${SPRING_INFLUXDB3_TOKEN}"
BATCH_SIZE="${BATCH_SIZE:-50000}"

# Script paths
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
EXPORT_SCRIPT="$SCRIPT_DIR/migrate-influxdb-export.sh"
IMPORT_SCRIPT="$SCRIPT_DIR/migrate-influxdb-import.sh"

# Tracking variables
declare -a SUCCESSFUL_MONTHS
declare -a FAILED_MONTHS
TOTAL_SIZE=0
START_TIME=$(date +%s)

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to print colored output
print_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Function to validate prerequisites
validate_prerequisites() {
    print_info "Validating prerequisites..."

    # Check if required scripts exist
    if [ ! -f "$EXPORT_SCRIPT" ]; then
        print_error "Export script not found: $EXPORT_SCRIPT"
        exit 1
    fi

    if [ ! -f "$IMPORT_SCRIPT" ]; then
        print_error "Import script not found: $IMPORT_SCRIPT"
        exit 1
    fi

    # Check if InfluxDB 3 token is set
    if [ -z "$SPRING_INFLUXDB3_TOKEN" ] && [ "$DRY_RUN" != "true" ]; then
        print_error "SPRING_INFLUXDB3_TOKEN environment variable is required"
        exit 1
    fi

    # Check if docker is available
    if ! command -v docker &> /dev/null; then
        print_error "Docker is not installed or not in PATH"
        exit 1
    fi

    # Check if InfluxDB 1.8 container is running
    if ! docker ps | grep -q influxdb; then
        print_error "InfluxDB 1.8 container is not running"
        exit 1
    fi

    # Create export directory
    mkdir -p "$EXPORT_DIR"

    print_success "All prerequisites validated"
}

# Function to calculate months between two dates
calculate_months() {
    local start_date=$1
    local end_date=$2

    local start_year=$(date -d "$start_date" +%Y)
    local start_month=$(date -d "$start_date" +%m)
    local end_year=$(date -d "$end_date" +%Y)
    local end_month=$(date -d "$end_date" +%m)

    echo $(( (end_year - start_year) * 12 + (10#$end_month - 10#$start_month) + 1 ))
}

# Function to get month start date
get_month_start() {
    local year=$1
    local month=$2
    printf "%04d-%02d-01" "$year" "$month"
}

# Function to get month end date
get_month_end() {
    local year=$1
    local month=$2

    # Get first day of next month and subtract one day
    local next_month=$((month + 1))
    local next_year=$year

    if [ $next_month -gt 12 ]; then
        next_month=1
        next_year=$((year + 1))
    fi

    date -d "$(printf "%04d-%02d-01" "$next_year" "$next_month") - 1 day" +%Y-%m-%d
}

# Function to format date for InfluxDB
format_influxdb_date() {
    local date_str=$1
    echo "${date_str}T00:00:00Z"
}

# Function to format date for end of day
format_influxdb_end_date() {
    local date_str=$1
    echo "${date_str}T23:59:59Z"
}

# Function to format bytes to human readable
format_bytes() {
    local bytes=$1
    if [ $bytes -lt 1024 ]; then
        echo "${bytes}B"
    elif [ $bytes -lt 1048576 ]; then
        echo "$((bytes / 1024))KB"
    elif [ $bytes -lt 1073741824 ]; then
        echo "$((bytes / 1048576))MB"
    else
        echo "$((bytes / 1073741824))GB"
    fi
}

# Function to format seconds to human readable duration
format_duration() {
    local seconds=$1
    local hours=$((seconds / 3600))
    local minutes=$(((seconds % 3600) / 60))
    local secs=$((seconds % 60))

    if [ $hours -gt 0 ]; then
        printf "%dh %dm %ds" $hours $minutes $secs
    elif [ $minutes -gt 0 ]; then
        printf "%dm %ds" $minutes $secs
    else
        printf "%ds" $secs
    fi
}

# Function to process a single month
process_month() {
    local year=$1
    local month=$2
    local current_month_num=$3
    local total_months=$4

    local month_start=$(get_month_start "$year" "$month")
    local month_end=$(get_month_end "$year" "$month")
    local month_name=$(date -d "$month_start" +"%B %Y")

    print_info "========================================"
    print_info "Processing [$current_month_num/$total_months]: $month_name"
    print_info "Range: $month_start to $month_end"
    print_info "========================================"

    if [ "$DRY_RUN" = "true" ]; then
        print_warning "DRY RUN: Would process $month_name"
        SUCCESSFUL_MONTHS+=("$month_name")
        return 0
    fi

    local export_filename="export_${year}_${month}.lp"
    local month_start_time=$(date +%s)

    # Export data for this month
    print_info "Step 1/3: Exporting data from InfluxDB 1.8..."

    if ! EXPORT_DIR="$EXPORT_DIR" \
         EXPORT_FILENAME="$export_filename" \
         DATABASE="$INFLUXDB_DATABASE" \
         INFLUX_HOST="$INFLUX_HOST" \
         INFLUX_PORT="$INFLUX_PORT" \
         START_DATE="$(format_influxdb_date "$month_start")" \
         END_DATE="$(format_influxdb_end_date "$month_end")" \
         bash "$EXPORT_SCRIPT"; then
        print_error "Failed to export data for $month_name"
        FAILED_MONTHS+=("$month_name (export failed)")
        return 1
    fi

    # Check if export file exists and get size
    local export_file="$EXPORT_DIR/$export_filename"
    if [ ! -f "$export_file" ]; then
        print_error "Export file not found: $export_file"
        FAILED_MONTHS+=("$month_name (export file missing)")
        return 1
    fi

    local file_size=$(stat -f%z "$export_file" 2>/dev/null || stat -c%s "$export_file" 2>/dev/null)
    TOTAL_SIZE=$((TOTAL_SIZE + file_size))
    print_success "Exported $(format_bytes $file_size) for $month_name"

    # Skip import if file is empty or too small (likely no data)
    if [ $file_size -lt 100 ]; then
        print_warning "Export file is empty or too small, skipping import for $month_name"
        rm -f "$export_file"
        SUCCESSFUL_MONTHS+=("$month_name (no data)")
        return 0
    fi

    # Import data to InfluxDB 3
    print_info "Step 2/3: Importing data to InfluxDB 3..."

    if ! IMPORT_FILE="$export_file" \
         EXPORT_DIR="$EXPORT_DIR" \
         SPRING_INFLUXDB3_BUCKET="$SPRING_INFLUXDB3_BUCKET" \
         SPRING_INFLUXDB3_URL="$SPRING_INFLUXDB3_URL" \
         SPRING_INFLUXDB3_TOKEN="$SPRING_INFLUXDB3_TOKEN" \
         BATCH_SIZE="$BATCH_SIZE" \
         bash "$IMPORT_SCRIPT"; then
        print_error "Failed to import data for $month_name"
        FAILED_MONTHS+=("$month_name (import failed)")
        return 1
    fi

    # Clean up export file
    print_info "Step 3/3: Cleaning up temporary files..."
    rm -f "$export_file"

    local month_duration=$(($(date +%s) - month_start_time))
    print_success "Completed $month_name in $(format_duration $month_duration)"
    SUCCESSFUL_MONTHS+=("$month_name")

    return 0
}

# Function to print summary
print_summary() {
    local end_time=$(date +%s)
    local total_duration=$((end_time - START_TIME))

    echo ""
    print_info "========================================"
    print_info "MIGRATION SUMMARY"
    print_info "========================================"

    echo "Time range: $MIGRATION_START_DATE to $MIGRATION_END_DATE"
    echo "Total duration: $(format_duration $total_duration)"
    echo "Total data processed: $(format_bytes $TOTAL_SIZE)"
    echo ""
    echo "Successful months: ${#SUCCESSFUL_MONTHS[@]}"
    echo "Failed months: ${#FAILED_MONTHS[@]}"
    echo ""

    if [ ${#SUCCESSFUL_MONTHS[@]} -gt 0 ]; then
        print_success "Successfully processed months:"
        for month in "${SUCCESSFUL_MONTHS[@]}"; do
            echo "  ✓ $month"
        done
        echo ""
    fi

    if [ ${#FAILED_MONTHS[@]} -gt 0 ]; then
        print_error "Failed months:"
        for month in "${FAILED_MONTHS[@]}"; do
            echo "  ✗ $month"
        done
        echo ""
        print_warning "You can retry failed months by adjusting MIGRATION_START_DATE and MIGRATION_END_DATE"
        return 1
    fi

    print_success "All months processed successfully!"
    return 0
}

# Main execution
main() {
    echo "╔════════════════════════════════════════════════════════════════╗"
    echo "║     InfluxDB 1.8 to InfluxDB 3 Migration Orchestrator         ║"
    echo "╚════════════════════════════════════════════════════════════════╝"
    echo ""

    print_info "Configuration:"
    echo "  Export directory: $EXPORT_DIR"
    echo "  Migration period: $MIGRATION_START_DATE to $MIGRATION_END_DATE"
    echo "  InfluxDB 1.8 database: $INFLUXDB_DATABASE"
    echo "  InfluxDB 3 bucket: $SPRING_INFLUXDB3_BUCKET"
    echo "  InfluxDB 3 URL: $SPRING_INFLUXDB3_URL"
    echo "  Batch size: $BATCH_SIZE lines"
    echo "  Dry run: $DRY_RUN"
    echo ""

    # Validate prerequisites
    validate_prerequisites

    # Calculate total months to process
    local total_months=$(calculate_months "$MIGRATION_START_DATE" "$MIGRATION_END_DATE")
    print_info "Total months to process: $total_months"
    echo ""

    if [ "$DRY_RUN" = "true" ]; then
        print_warning "DRY RUN MODE: No actual export/import will be performed"
        echo ""
    fi

    # Process each month
    local current_year=$(date -d "$MIGRATION_START_DATE" +%Y)
    local current_month=$(date -d "$MIGRATION_START_DATE" +%m)
    local end_year=$(date -d "$MIGRATION_END_DATE" +%Y)
    local end_month=$(date -d "$MIGRATION_END_DATE" +%m)
    local month_counter=0

    while true; do
        month_counter=$((month_counter + 1))

        process_month "$current_year" "$current_month" "$month_counter" "$total_months"

        # Check if we've reached the end date
        if [ "$current_year" -eq "$end_year" ] && [ "$current_month" -eq "$((10#$end_month))" ]; then
            break
        fi

        # Move to next month
        current_month=$((current_month + 1))
        if [ $current_month -gt 12 ]; then
            current_month=1
            current_year=$((current_year + 1))
        fi

        # Add a small delay between months to avoid overwhelming the system
        sleep 2
    done

    # Print summary
    print_summary
}

# Run main function
main

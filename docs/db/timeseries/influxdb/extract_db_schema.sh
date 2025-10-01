#!/bin/bash

DATABASE="conluz_db"
HOST="100.64.130.69"
PORT="8086"
OUTPUT_FILE="influxdb_schema.txt"
USERNAME="luzflux"
PASSWORD="blank"

echo "# InfluxDB 1.8 Schema for database: $DATABASE" > $OUTPUT_FILE
echo "Generated on: $(date)" >> $OUTPUT_FILE
echo "" >> $OUTPUT_FILE

# List all measurements
echo "## Measurements:" >> $OUTPUT_FILE
influx -host $HOST -port $PORT -username $USERNAME -password $PASSWORD -database $DATABASE -execute "SHOW MEASUREMENTS" -format=column >> $OUTPUT_FILE
echo "" >> $OUTPUT_FILE

# List all field keys with types
echo "## Field Keys and Types:" >> $OUTPUT_FILE
influx -host $HOST -port $PORT -username $USERNAME -password $PASSWORD -database $DATABASE -execute "SHOW FIELD KEYS" -format=column >> $OUTPUT_FILE
echo "" >> $OUTPUT_FILE

# List all tag keys
echo "## Tag Keys:" >> $OUTPUT_FILE
influx -host $HOST -port $PORT -username $USERNAME -password $PASSWORD -database $DATABASE -execute "SHOW TAG KEYS" -format=column >> $OUTPUT_FILE
echo "" >> $OUTPUT_FILE

# Get sample data
echo "## Sample Data (last 5 points):" >> $OUTPUT_FILE
influx -host $HOST -port $PORT -username $USERNAME -password $PASSWORD -database $DATABASE -execute "SELECT * FROM /.*/ LIMIT 5" -format=column >> $OUTPUT_FILE
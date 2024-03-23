#!/bin/bash
set -e

# Create database
influx -username "${INFLUXDB_ADMIN_USER}" -password "${INFLUXDB_ADMIN_PASSWORD}" -execute "CREATE DATABASE conluz_db"
# Create user
influx -username "${INFLUXDB_ADMIN_USER}" -password "${INFLUXDB_ADMIN_PASSWORD}" -execute "CREATE USER ${INFLUXDB_CONLUZ_USER} WITH PASSWORD '${INFLUXDB_CONLUZ_USER_PASSWORD}'"
# Grant user privileges on the database
influx -username "${INFLUXDB_ADMIN_USER}" -password "${INFLUXDB_ADMIN_PASSWORD}" -execute "GRANT ALL ON conluz_db TO ${INFLUXDB_CONLUZ_USER}"

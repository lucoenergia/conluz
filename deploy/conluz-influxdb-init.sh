#!/bin/bash
set -e

# Create database
influx -username "${INFLUXDB_ADMIN_USER}" -password "${INFLUXDB_ADMIN_PASSWORD}" -execute "CREATE DATABASE conluz_db"
# Create user
influx -username "${INFLUXDB_ADMIN_USER}" -password "${INFLUXDB_ADMIN_PASSWORD}" -execute "CREATE USER ${INFLUXDB_CONLUZ_USER} WITH PASSWORD '${INFLUXDB_CONLUZ_USER_PASSWORD}'"
# Grant user privileges on the database
influx -username "${INFLUXDB_ADMIN_USER}" -password "${INFLUXDB_ADMIN_PASSWORD}" -execute "GRANT ALL ON conluz_db TO ${INFLUXDB_CONLUZ_USER}"
# Define retention policies
influx -username "${INFLUXDB_ADMIN_USER}" -password "${INFLUXDB_ADMIN_PASSWORD}" -execute "CREATE RETENTION POLICY one_month ON conluz_db DURATION 30d REPLICATION 1"
influx -username "${INFLUXDB_ADMIN_USER}" -password "${INFLUXDB_ADMIN_PASSWORD}" -execute "CREATE RETENTION POLICY one_year ON conluz_db DURATION 365d REPLICATION 1"
influx -username "${INFLUXDB_ADMIN_USER}" -password "${INFLUXDB_ADMIN_PASSWORD}" -execute "CREATE RETENTION POLICY forever ON conluz_db DURATION INF REPLICATION 1 DEFAULT"

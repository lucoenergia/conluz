#!/usr/bin/env bash

# This script runs pg_dump utility in your Docker container with the name CONTAINER_NAME and dumps the output into a
# db_dump.tar file in your current directory on host machine by using output redirection (>).
# Then it checks the exit status of the pg_dump command by checking $? variable,
# which stores the exit status of last command in bash.
# If the exit status is 0, that means command completed successfully.
# It announces whether the dumping was a success based on this.

DB_NAME="conluz_db"
CONTAINER_NAME=$1 # First argument as Docker Container Name
PG_USER=$2 # Second argument as PostgreSQL user

# validation checks
if [ -z "$CONTAINER_NAME" ]
then
    echo "No container name provided. Please provide the Docker container name as the first argument."
    exit 1
fi

if [ -z "$PG_USER" ]
then
    echo "No PostgreSQL user provided. Please provide the PostgreSQL user as the second argument."
    exit 2
fi

# create postgreSQL dump
docker exec $CONTAINER_NAME pg_dump -U $PG_USER -F t $DB_NAME > ./db_dump.tar

# check whether the dump was successful
if [ $? -eq 0 ]
then
  echo "Database dump has been created successfully."
else
  echo "Failed to create database dump." >&2
fi

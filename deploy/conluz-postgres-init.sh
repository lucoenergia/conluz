#!/bin/bash
set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
  CREATE DATABASE conluz_db;
  CREATE DATABASE conluz_db_test;
  CREATE USER luz WITH PASSWORD 'blank';
  GRANT ALL PRIVILEGES ON DATABASE conluz_db TO luz;
  GRANT ALL PRIVILEGES ON DATABASE conluz_db_test TO luz;
EOSQL

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "conluz_db" <<-EOSQL
  GRANT ALL ON SCHEMA public TO luz;
EOSQL

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "conluz_db_test" <<-EOSQL
  GRANT ALL ON SCHEMA public TO luz;
EOSQL

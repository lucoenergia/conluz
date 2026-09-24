# Database Setup

For new installations, use the sanitized reference example `deploy/docker-compose.example.yml`
(copy `deploy/.env.example` to `deploy/.env` and fill it in first). For existing databases:

**PostgreSQL:**
```sql
CREATE DATABASE conluz_db;
CREATE DATABASE conluz_db_test;
CREATE USER luz WITH PASSWORD 'blank';
GRANT ALL PRIVILEGES ON DATABASE conluz_db TO luz;
GRANT ALL PRIVILEGES ON DATABASE conluz_db_test TO luz;
```

**InfluxDB:**
```sql
CREATE DATABASE conluz_db
CREATE USER luz WITH PASSWORD 'blank'
GRANT ALL ON conluz_db TO luz
CREATE RETENTION POLICY one_month ON conluz_db DURATION 30d REPLICATION 1
CREATE RETENTION POLICY one_year ON conluz_db DURATION 365d REPLICATION 1
CREATE RETENTION POLICY forever ON conluz_db DURATION INF REPLICATION 1 DEFAULT
```

## Upgrading an existing database

If your database was created by version 1.0.76 or lower, or was migrated by any version between
1.0.77 and the release before the conluz-281 fix, see
[`migrations/upgrading_from_1.0.76_or_lower_setup.md`](migrations/upgrading_from_1.0.76_or_lower_setup.md).

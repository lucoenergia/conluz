# InfluxDB 3 Core Setup Guide

This guide explains how to set up and configure InfluxDB 3 Core for the Conluz project.

## Docker Configuration

The `docker-compose.yaml` includes an InfluxDB 3 Core service configured as follows:

```yaml
influxdb3:
  image: influxdb:3-core
  container_name: influxdb3
  ports:
    - "8181:8181"
  environment:
    - INFLUXDB3_AUTH_TOKEN
    - INFLUXDB3_HTTP_BIND_ADDR=0.0.0.0:8181
    - LOG_FILTER=info
  volumes:
    - ${PATH_TO_INFLUXDB3_DATA}:/var/lib/influxdb3/data
  command: >
    influxdb3 serve
    --node-id conluz_node
    --object-store file
    --data-dir /var/lib/influxdb3/data
```

## Environment Variables

Add these variables to your `.env` file in the `deploy/` directory:

```bash
# InfluxDB 3 Core Configuration
PATH_TO_INFLUXDB3_DATA=/path/to/influxdb3/data
INFLUXDB3_AUTH_TOKEN=your-secret-token-here
```

## Initial Setup

1. **Start the container:**
   ```bash
   docker-compose up -d influxdb3
   ```

2. **Create a database:**
   ```bash
   docker exec influxdb3 influxdb3 create database conluz_db
   ```

3. **Create an admin token (optional):**
   ```bash
   docker exec influxdb3 influxdb3 create token --admin
   ```

4. **Verify the setup:**
   ```bash
   curl http://localhost:8181/api/v3/ping
   ```

## Key Differences from InfluxDB 1.8

| Feature | InfluxDB 1.8 | InfluxDB 3 Core |
|---------|-------------|-----------------|
| **Port** | 8086 | 8181 |
| **Query Language** | InfluxQL | SQL |
| **Authentication** | Username/Password | Token-based |
| **Data Organization** | Databases | Databases (no orgs/buckets like v2) |
| **Retention Policies** | Multiple policies per DB | Per database |
| **Time Aggregation** | `GROUP BY time(1d)` | `DATE_TRUNC('day', time)` |

## Application Configuration

Update your `application.properties`:

```properties
# InfluxDB 3 configuration
spring.influxdb3.url=http://localhost:8181
spring.influxdb3.token=your-secret-token-here
spring.influxdb3.org=your-org  # Not used in Core but required by client
spring.influxdb3.bucket=conluz_db
```

## SQL Query Examples

InfluxDB 3 uses SQL instead of InfluxQL:

### Hourly Aggregation
```sql
SELECT
    DATE_TRUNC('hour', time) as time,
    SUM(consumption_kwh) AS consumption_kwh
FROM datadis_consumption_kwh
WHERE time >= '2024-01-01' AND time <= '2024-01-31'
GROUP BY DATE_TRUNC('hour', time)
```

### Monthly Aggregation (Main Benefit!)
```sql
SELECT
    DATE_TRUNC('month', time) as time,
    SUM(consumption_kwh) AS consumption_kwh
FROM datadis_consumption_kwh
WHERE time >= '2024-01-01' AND time <= '2024-12-31'
GROUP BY DATE_TRUNC('month', time)
```

### Yearly Aggregation
```sql
SELECT
    DATE_TRUNC('year', time) as time,
    SUM(consumption_kwh) AS consumption_kwh
FROM datadis_consumption_kwh
WHERE time >= '2020-01-01' AND time <= '2024-12-31'
GROUP BY DATE_TRUNC('year', time)
```

## Data Migration

To migrate data from InfluxDB 1.8 to InfluxDB 3:

1. **Export data from InfluxDB 1.8:**
   ```bash
   cd deploy
   ./migrate-influxdb-export.sh
   ```

2. **Import data into InfluxDB 3:**
   ```bash
   cd deploy
   export INFLUX_SETUP_TOKEN=your-token-here
   ./migrate-influxdb-import.sh
   ```

## Troubleshooting

### Container won't start
- Check that port 8181 is not already in use
- Verify the data directory path in `.env`
- Check container logs: `docker logs influxdb3`

### Authentication errors
- Ensure `INFLUXDB3_AUTH_TOKEN` is set in `.env`
- Verify the token matches in application.properties
- Token is required for all API requests

### Query errors
- Remember: InfluxDB 3 uses SQL, not InfluxQL
- Use `DATE_TRUNC()` instead of `GROUP BY time()`
- Column names are case-sensitive in SQL

## Resources

- [InfluxDB 3 Core Documentation](https://docs.influxdata.com/influxdb3/core/)
- [InfluxDB 3 SQL Reference](https://docs.influxdata.com/influxdb3/core/reference/sql/)
- [Java Client Library](https://github.com/InfluxCommunity/influxdb3-java)

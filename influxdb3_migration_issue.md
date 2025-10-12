## 🚀 Feature / Migration Request: Upgrade from InfluxDB 1.8 to InfluxDB 3 Core

### 🧩 Context
Our current time-series database runs on **InfluxDB 1.8**, which has been stable but presents some limitations in handling calendar-based time grouping (months and years).  

InfluxDB 3 Core (OSS) introduces a **new storage and query engine** based on Apache Arrow and SQL, providing significant advantages in data aggregation, compression, and flexibility.

---

### 🎯 Motivation

The main motivation for this migration is to **improve data analysis and aggregation by natural calendar periods** (e.g., by month or by year), which is **not natively supported in InfluxDB 1.8**.  

InfluxQL in 1.8 allows only fixed durations (e.g., `30d`, `1w`), but cannot align data to actual calendar months or years — a key requirement for our energy and production analytics.  

InfluxDB 3 supports **SQL functions** such as `DATE_TRUNC('month', time)` and `DATE_TRUNC('year', time)`, which provide true calendar-based grouping and make reporting much more accurate and intuitive.

---

### 💡 Expected Benefits

- ✅ True month/year calendar aggregation support (`DATE_TRUNC`, `DATE_BIN`)
- ✅ Improved performance with the new Apache Arrow + Parquet storage engine
- ✅ SQL standard queries instead of InfluxQL
- ✅ Better compression and higher cardinality handling
- ✅ Easier integration with modern tools (Grafana, DuckDB, etc.)

---

### ⚙️ Migration Overview

The migration path will involve **exporting data from InfluxDB 1.8** and **importing it into InfluxDB 3** using Line Protocol or Parquet format.

#### Proposed Steps
1. **Install InfluxDB 3 Core (OSS)** in parallel via Docker or on a dedicated host.
2. **Export data** from InfluxDB 1.8:
   ```bash
   influx_inspect export -database <db> -out /tmp/export.lp -start 1970-01-01T00:00:00Z -end now()
   ```
3. **Import into InfluxDB 3:**
   ```bash
   influx write --bucket energia --file /tmp/export.lp --format lineprotocol
   ```
4. **Update queries and dashboards** to use SQL instead of InfluxQL.
5. **Reconfigure integrations** (e.g., Telegraf, Grafana, scripts).

---

### 🔍 Open Questions / To Be Decided

- How to handle **retention policies** and **continuous queries** equivalents in InfluxDB 3?
- Should we migrate all historical data or only recent datasets?
- How to handle **authentication and user roles** after migration?
- How to automate the migration and validation process?

---

### 📅 Proposed Timeline

| Phase | Description | Estimated Date |
|-------|--------------|----------------|
| 1 | Setup InfluxDB 3 test environment | Week 1 |
| 2 | Export sample dataset (1 month) | Week 2 |
| 3 | Validate queries & dashboards (SQL) | Week 3 |
| 4 | Full migration and production switch | Week 4–5 |

---

### 🧠 References
- [InfluxDB 3 OSS Docs](https://docs.influxdata.com/influxdb3/)
- [InfluxDB 1.8 Export Tool](https://docs.influxdata.com/influxdb/v1/tools/influx_inspect/)
- [InfluxDB 3 SQL Reference](https://docs.influxdata.com/influxdb3/sql-reference/)

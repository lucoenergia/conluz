# Recomputing the Datadis consumption pre-aggregates

Step-by-step procedure for rebuilding the stored monthly and yearly consumption pre-aggregates
(`datadis_consumption_kwh_month` and `datadis_consumption_kwh_year`) after deploying the local
calendar alignment fix.

> This document contains **no environment-specific values**. Hostnames, credentials, community names
> and CUPS codes belong in the private `conluz-infra` repository and in gitignored `.env` files on
> the host. Every command below reads them from the environment.

---

## 1. Why this is needed

Before the fix, both aggregators stamped their point at **local** midnight but summed over **literal
UTC** windows:

| Pre-aggregate | Window used | Effect on the stored total |
|---|---|---|
| Monthly | `yyyy-MM-01T00:00:00Z` … `yyyy-MM-ddT23:59:00Z` | Lost the month's first local hour(s); gained the previous month's last local hour(s). |
| Yearly | `yyyy-01-01T00:00:00Z` … `yyyy-12-31T23:59:59Z` | **January was excluded entirely** — its monthly point sits at `yyyy-1-12-31T23:00Z`, before the window starts — while the *next* January's point, at `yyyy-12-31T23:00Z`, was swallowed. |

So every stored yearly total is effectively February-through-December plus a stray January from the
following year. Both measurements must be rebuilt for the whole stored history.

The fix does **not** change where a point is stamped or what tags it carries, so recomputing
overwrites each existing point in place. It never produces a second point beside it.

---

## 2. Preconditions

- [ ] The release carrying the local calendar alignment is **deployed and running**. Recomputing with
      the old build would just rewrite the same wrong totals.
- [ ] You have credentials for a user who is **Community Admin of every community** to be
      recomputed, or a platform admin.
- [ ] You can reach the InfluxDB instance with a client that can run `SELECT` queries.
- [ ] You have taken the backup described in step 3.

### Choose the time of day

The scheduled jobs write to the same measurements. Run this procedure **outside 02:00–07:00 server
local time**; the two that touch these exact measurements are the tightest constraint.

| Job | Cron | Writes |
|---|---|---|
| `DatadisSuppliesSyncDailyJob` | `0 0 2 * * ?` | supplies |
| `DatadisSyncDailyJob` | `0 0 4 * * ?` | `datadis_consumption_kwh` (hourly source data) |
| `DatadisMonthlyAggregationJob` | `0 0 5 * * ?` | **`datadis_consumption_kwh_month`** |
| `DatadisYearlyAggregationJob` | `0 0 6 * * ?` | **`datadis_consumption_kwh_year`** |

---

## 3. Back up InfluxDB — mandatory

The recomputation **overwrites** both measurements in place. There is no undo other than a restore.

Use the backup tooling in the private `conluz-infra` repository, and confirm the backup completed
before going on. The pre-aggregates live in the database's default retention policy (`forever`), so a
whole-database backup covers them.

```bash
# Record what you are about to change, so a restore can be verified afterwards.
influx -database "$INFLUX_DB" -execute \
  'SELECT COUNT("consumption_kwh") FROM "datadis_consumption_kwh_month" GROUP BY "cups"' \
  -format csv > before-monthly-counts.csv
influx -database "$INFLUX_DB" -execute \
  'SELECT COUNT("consumption_kwh") FROM "datadis_consumption_kwh_year" GROUP BY "cups"' \
  -format csv > before-yearly-counts.csv
```

---

## 4. Find the range of years that hold data

The earliest and latest hourly consumption records bound the years to recompute.

```sql
SELECT FIRST("consumption_kwh") FROM "datadis_consumption_kwh"
SELECT LAST("consumption_kwh")  FROM "datadis_consumption_kwh"
```

Each returns the value together with its timestamp; take the **local** year of each. A record at
`2021-12-31T23:30:00Z` is already 2022 in a UTC+1 zone, so recompute from 2022, not 2021.

To see which supplies have stored consumption at all:

```sql
SHOW TAG VALUES FROM "datadis_consumption_kwh" WITH KEY = "cups"
```

---

## 5. List every community

The aggregation endpoints are community-scoped: they only touch the supplies of the community in the
path. **Recompute every community**, not just one.

```bash
TOKEN=$(curl -sS -X POST "$CONLUZ_URL/api/v1/login" \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"$CONLUZ_ADMIN_USER\",\"password\":\"$CONLUZ_ADMIN_PASSWORD\"}" \
  | jq -r '.token')

curl -sS "$CONLUZ_URL/api/v1/communities" -H "Authorization: Bearer $TOKEN" \
  | jq -r '.[] | "\(.id)\t\(.code)"'
```

`GET /api/v1/communities` returns every community for a platform admin, and only the caller's
communities otherwise. Save the ids; they drive the loop in step 6.

---

## 6. Recompute, monthly before yearly

For **each community**, for **each year**, in ascending year order:

```bash
# 1) all twelve months of the year, for every supply of the community
curl -sS -X POST "$CONLUZ_URL/api/v1/communities/$COMMUNITY_ID/consumption/datadis/sync/monthly" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "{\"year\": $YEAR}"

# 2) only then the year, which reads the monthly measurement just rewritten
curl -sS -X POST "$CONLUZ_URL/api/v1/communities/$COMMUNITY_ID/consumption/datadis/sync/yearly" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "{\"year\": $YEAR}"
```

Omitting `month` aggregates all twelve months; omitting `supplyCode` covers every supply of the
community. `year` is required and is validated to be between 2000 and 2100. **The order matters**: the
yearly aggregation sums the monthly measurement, so running it first would just re-total the old
monthly values.

### What the responses mean

| Status | Meaning | What to do |
|---|---|---|
| `200` | The request was accepted and processed. | Still verify in step 7 — see the caveat below. |
| `409` | Datadis is not enabled for that community. | Skip it; it has no Datadis consumption to aggregate. |
| `403` | The caller is a member of the community but not one of its admins. | Use an account with the right role. |
| `404` | The community does not exist, or the caller is not a member. | Check the id from step 5. |

> **A `200` does not prove every supply succeeded.** The aggregation services catch and log per-supply
> failures so one bad supply cannot abort the run. Read the application log for
> `Failed to aggregate monthly consumption for supply ID` / `...yearly...`, and rely on the
> verification queries in step 7 rather than on the HTTP status.

### Coverage notes

- **Disabled supplies are included.** Nothing on the community path filters on the enabled flag, so a
  supply that has since been switched off is recomputed by the plain call above. Its energy was real
  while it was on, and members' payback figures depend on it.
- **Supplies without a distributor code are skipped**, silently on the community-scoped path (the
  daily jobs, which iterate every supply in the system, log a `Skipping supply with ID: ... because it
  does not have distributor code` warning instead). That is pre-existing behaviour; such supplies have
  no Datadis data to aggregate.
- **A single supply** can be redone on its own by adding `"supplyCode": "<CUPS>"` to either body. This
  works for disabled supplies as well.
- **Months with no data write nothing** and log `No hourly data found to aggregate`. Running a whole
  year for a partially covered year is safe.

---

## 7. Verify

All the queries below need the window bounds as **UTC instants of local midnight**. In a UTC+1 winter
zone such as Europe/Madrid:

| Local instant | UTC instant |
|---|---|
| `2023-01-01T00:00:00+01:00` | `2022-12-31T23:00:00Z` |
| `2024-01-01T00:00:00+01:00` | `2023-12-31T23:00:00Z` |

Substitute your own zone's offset. The examples verify 2023.

### 7.1 No duplicates, and no month missing

A point is keyed by measurement + tag set + timestamp, so a literal duplicate cannot exist; what this
checks is that the count per supply is the expected one and that nothing was dropped.

```sql
-- At most 12 per CUPS for a fully covered year; fewer only where data genuinely stops.
SELECT COUNT("consumption_kwh") FROM "datadis_consumption_kwh_month"
WHERE time >= '2022-12-31T23:00:00Z' AND time < '2023-12-31T23:00:00Z'
GROUP BY "cups"

-- Exactly one yearly point per CUPS per year.
SELECT COUNT("consumption_kwh") FROM "datadis_consumption_kwh_year"
WHERE time >= '2022-12-31T23:00:00Z' AND time < '2023-12-31T23:00:00Z'
GROUP BY "cups"
```

### 7.2 January is back

The single clearest sign the yearly defect is gone: a monthly point must exist at local midnight on
January 1st, and the yearly point must sit at that same instant.

```sql
SELECT "consumption_kwh" FROM "datadis_consumption_kwh_month"
WHERE time = '2022-12-31T23:00:00Z' GROUP BY "cups"

SELECT "consumption_kwh" FROM "datadis_consumption_kwh_year"
WHERE time = '2022-12-31T23:00:00Z' GROUP BY "cups"
```

### 7.3 The yearly total equals the sum of its months

```sql
SELECT SUM("consumption_kwh") FROM "datadis_consumption_kwh_month"
WHERE time >= '2022-12-31T23:00:00Z' AND time < '2023-12-31T23:00:00Z'
GROUP BY "cups"
```

Compare, per CUPS, against the yearly value from 7.2. They must match. If the yearly value is short by
roughly one month, the yearly aggregation ran **before** the monthly one for that year — rerun the
yearly call.

### 7.4 Spot-check against the API

For one supply and one month, the daily totals must add up to the monthly total:

```bash
curl -sS -H "Authorization: Bearer $TOKEN" \
  "$CONLUZ_URL/api/v1/supplies/$SUPPLY_ID/consumption/daily?startDate=2023-01-01T00:00:00%2B01:00&endDate=2023-01-31T23:59:59%2B01:00" \
  | jq '[.[].consumptionKWh] | add'

curl -sS -H "Authorization: Bearer $TOKEN" \
  "$CONLUZ_URL/api/v1/supplies/$SUPPLY_ID/consumption/monthly?startDate=2023-01-01T00:00:00%2B01:00&endDate=2023-01-31T23:59:59%2B01:00" \
  | jq '.[].consumptionKWh'
```

Pass both bounds **with the zone's offset** (`%2B` is the URL-encoded `+`). Bounds expressed in UTC
land mid-day locally and return partial first and last buckets, which is correct behaviour but makes
the comparison fail.

---

## 8. If something goes wrong

The procedure is **idempotent**: rerunning a community and year overwrites the same points again, so
the normal recovery is simply to rerun the affected calls, monthly before yearly.

Restore from the step 3 backup only if the source hourly measurement
(`datadis_consumption_kwh`) itself was damaged — the pre-aggregates are entirely derived from it and
can always be rebuilt.

---

## 9. Checklist

- [ ] Fixed release deployed and running
- [ ] InfluxDB backup taken and confirmed
- [ ] Time of day outside 02:00–07:00 server local time
- [ ] Year range determined from `FIRST`/`LAST` on `datadis_consumption_kwh`
- [ ] Every community listed
- [ ] For each community, for each year: monthly, then yearly
- [ ] Application log checked for per-supply aggregation failures
- [ ] Verification 7.1 (counts), 7.2 (January present), 7.3 (yearly == sum of months) pass
- [ ] Spot-check 7.4 passes for at least one supply

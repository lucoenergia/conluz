# Upgrading a database created by 1.0.76 or lower

Releases 1.0.77 onwards made the Datadis, Huawei and Shelly integrations configurable
(`base_url` + `enabled` columns, and a `shelly_config` table). The changesets that introduced
that model did not preserve the behaviour of databases that already existed: every integration
had been unconditionally active before 1.0.77, and the new columns defaulted pre-existing rows
to `enabled = false` pointing at a local mock URL. An upgraded installation started healthy, ran
all its scheduled jobs, and silently ingested nothing — the only trace being `INFO` lines such as
`No enabled Huawei config found.` and `No Shelly config found.`

This release fixes those three changesets so that an upgrade preserves the previous behaviour with
no manual post-upgrade configuration. Which section below applies to you depends on which release
last migrated your database.

| Your database was last migrated by | Go to |
|---|---|
| 1.0.76 or lower                                            | [Upgrading from 1.0.76 or lower](#upgrading-from-1076-or-lower) |
| 1.0.77 up to the release before this one                   | [Databases already migrated by 1.0.77+](#databases-already-migrated-by-1077) |
| Nothing — this is a new installation                       | [New installations](#new-installations) |

## What the fixed changesets do

Each backfill runs inside the same changeset that adds the columns (or creates the table), so on
PostgreSQL the DDL and the backfill commit atomically.

| Changeset | Backfill applied to a pre-1.0.77 database |
|---|---|
| `add_base_url_and_enabled_to_datadis_config` | Every existing row: `base_url = 'https://datadis.es'`, `enabled = true` |
| `add_base_url_and_enabled_to_huawei_config`  | Every existing row: `base_url = 'https://eu5.fusionsolar.huawei.com/thirdData'`, `enabled = true` |
| `create_shelly_config`                       | Inserts one enabled row **only if** some supply already carries a Shelly identifier (`shelly_mac`, `shelly_id` or `shelly_mqtt_prefix`) |

Notes:

- `base_url` is domain-only for Datadis and includes `/thirdData` for Huawei. This matches how the
  HTTP clients build their URLs: `DatadisAuthorizer` appends `/nikola-auth/tokens/login` and the
  Datadis repositories append `/api-private/api`, whereas the Huawei clients append only their leaf
  endpoint path.
- The **column defaults are unchanged** — they still point at the local mocks. They govern rows
  created after the upgrade, so new installations are unaffected.
- No migration reads or writes `username` or `password` in `datadis_config` or `huawei_config`.
  Existing credentials are left byte-identical.
- The Shelly row is inserted without a community; `add_community_id_to_shelly_config` later attaches
  it to the default community, like every other legacy row.

## Upgrading from 1.0.76 or lower

Nothing to do. Start the new version and Liquibase applies the whole chain, backfills included.

Afterwards, Datadis and Huawei are enabled against their real provider URLs, and Shelly is enabled
if any supply carried a Shelly identifier. See [Verifying the result](#verifying-the-result).

## Databases already migrated by 1.0.77+

The three changesets are already recorded as applied in `databasechangelog`, and their content has
now changed, so Liquibase refuses to start:

```
Validation Failed:
     3 changesets check sum
          ...add_base_url_and_enabled_to_huawei_config... was: 9:21f1b1... but is now: 9:253f3d...
```

This affects every release from 1.0.77 up to and including the last one published before this fix.
No known deployment is in this state; local development databases are the likely case. Recreating a
disposable development database is simpler than the remedy below.

### Remedy

Clear the stored checksums so Liquibase recomputes them on the next start. This does **not**
re-execute anything:

```sql
UPDATE databasechangelog SET md5sum = NULL
WHERE id IN ('add_base_url_and_enabled_to_datadis_config',
             'add_base_url_and_enabled_to_huawei_config',
             'create_shelly_config');
```

### The remedy does not apply the backfill

Nulling the checksum only satisfies Liquibase's integrity check. The changesets do not re-run, so
the integrations on such a database stay exactly as they are — disabled, pointing at the mock URLs.
Configure them through the existing endpoints, which handle community and plant scoping correctly:

| Integration | Endpoint | Body | Required |
|---|---|---|---|
| Datadis | `PUT /api/v1/communities/{communityId}/config/datadis` | `username`, `password`, `baseUrl`, `enabled` | Community admin |
| Huawei  | `PUT /api/v1/plants/{plantId}/production/huawei/config` | `username`, `password`, `baseUrl`, `enabled` | Community admin (of the plant's community) |
| Shelly  | `PUT /api/v1/communities/{communityId}/config/shelly`   | `enabled`                                     | Community admin |

Prefer the endpoints over raw SQL. `shelly_config` in particular has no unique constraint on
`community_id` and its `community_id` is `NOT NULL` with a foreign key, so a hand-written `INSERT`
can either fail or leave duplicate rows; the endpoint updates the community's existing row or
creates one correctly.

If you do choose SQL for Datadis and Huawei, note that these statements assume the single-community,
single-plant shape of a legacy installation and will touch every row:

```sql
UPDATE datadis_config SET base_url = 'https://datadis.es', enabled = true;
UPDATE huawei_config  SET base_url = 'https://eu5.fusionsolar.huawei.com/thirdData', enabled = true;
```

## New installations

Nothing to do, and nothing changes. The backfills match zero rows on an empty database: the three
configuration tables start empty, and the integrations are configured through the endpoints above.

## Verifying the result

```sql
SELECT base_url, enabled FROM datadis_config;
SELECT base_url, enabled, plant_id FROM huawei_config;
SELECT enabled, community_id FROM shelly_config;
```

On an upgraded pre-1.0.77 database, Datadis and Huawei should report the provider URLs above with
`enabled = true`. `shelly_config` holds one enabled row attached to the default community if any
supply carried a Shelly identifier, and no row otherwise — an absent row means the Shelly jobs stay
disabled, which the endpoint above can correct.

Credentials are unchanged by the upgrade; if Datadis or Huawei authentication now fails, the stored
`username`/`password` were already wrong or absent before the upgrade.

## Data lost during the gap

For an installation that ran 1.0.77 or later with the integrations silently disabled:

- **Shelly**: instant consumption is lost for the whole gap. The processor only looks back 5 minutes
  and is not re-run automatically. The raw MQTT messages remain in InfluxDB, but nothing reprocesses
  them.
- **Huawei**: real-time production for the gap is lost. Hourly history is recovered by the next
  `SyncPreviousDaysHuaweiHourlyProductionJob`.
- **Datadis**: nothing is permanently lost — history can be re-fetched through the sync endpoints
  under `/api/v1/communities/{communityId}/consumption/datadis/sync`.

## Regression coverage

`LiquibaseUpgradePathIntegrationTest` applies the changelog up to
`add_address_ref_to_supplies_20250903T2347` (the last changeset before configurability), seeds a
legacy fixture over JDBC, applies the rest of the chain and asserts the outcome. It covers an
upgrade with and without Shelly-identified supplies, an upgrade with no supplies at all, a fresh
install, and the rollback of the three changesets.

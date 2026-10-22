# Erasing a person's account by hand

The manual procedure an operator follows to handle a request to delete a person's account and data, while no
endpoint allows it.

> This document contains **no environment-specific values**. Hostnames, credentials, database names and backup
> locations belong in the private `conluz-infra` repository and in gitignored `.env` files on the host. Every query
> below takes the account's id as the placeholder `:user_id`, and a supply's or a plant's id as `:supply_id` and
> `:plant_id`.

---

## 1. When it applies

- **What it is for:** a person asks for their account and its data to be deleted.
- **Why it is manual:** no API caller can delete, disable or edit another person's account (ACC-008, ACC-020).
  A community admin can only suspend a membership (MEM-027): that removes the person's access to the community, but
  not the account or its data.
- **How long it applies:** this procedure is temporary. It is retired once account deletion has an owner (#325).
  Until then, it is the only way to honour such a request.
- **Who decides:** each community the person belongs to is the controller of the member record it holds
  (ADR-0003 §1). Agree the erasure with the admins of every community listed in step 2 before going further.

Every step below either is done by a community admin through the API, or is checked with a read-only query.
The one write done by hand is step 6, and the foreign-key rules decide most of what it touches.

---

## 2. Identify the account and its communities

```sql
-- The account
SELECT id, personal_id, email, enabled, is_platform_admin FROM users WHERE id = :user_id;

-- Its memberships, in every community
SELECT m.community_id, c.name, m.role, m.enabled, m.accepted_at
FROM community_memberships m JOIN communities c ON c.id = m.community_id
WHERE m.user_id = :user_id;
```

Keep the list of communities: their admins carry out steps 4 and 5.

---

## 3. A platform admin keeps the platform administrable

If `is_platform_admin` is true, the deletion is refused while the account is an **enabled** platform admin and no
more than one platform admin is enabled (ACC-009, ACC-013).

```sql
SELECT count(*) FROM users WHERE is_platform_admin AND enabled;
```

If the account is the only enabled platform admin, appoint another first: invite one (ACC-014) and wait until they
accept. Otherwise, another platform admin revokes the role (`POST /api/v1/users/{userId}/revoke-platform-admin`).
Check that `is_platform_admin` is now false.

---

## 4. Supplies, and what refuses their deletion

An account that owns a supply, in any community and enabled or not, is not deleted (ACC-009).
`supplies_user_id_fk` has no `ON DELETE` rule, so the database refuses it as well.

```sql
SELECT id, code, community_id, enabled FROM supplies WHERE user_id = :user_id;
```

A community admin deletes each supply with `DELETE /api/v1/supplies/{supplyId}`. SUP-001 refuses the deletion when
something still uses the supply. Check each one first:

```sql
-- A plant attached to the supply: SUPPLY_HAS_PLANTS
SELECT id, name FROM plants WHERE supply_id = :supply_id;

-- A partition coefficient for the supply, in any sharing agreement and any status: SUPPLY_HAS_PARTITION_COEFFICIENTS
SELECT id, sharing_agreement_id, plant_id FROM supply_partition_coefficient WHERE supply_id = :supply_id;
```

- **A plant on the supply:** it is deleted with `DELETE /api/v1/plants/{plantId}` (PLT-001), together with its
  Huawei configuration.
- **What refuses a plant's deletion:** PLT-001 refuses it while a sharing agreement, in any status, or a partition
  coefficient references the plant:

  ```sql
  SELECT id, status FROM sharing_agreement WHERE plant_id = :plant_id;
  SELECT id FROM supply_partition_coefficient WHERE plant_id = :plant_id;
  ```

- **A plant that holds sharing agreements:** it is the community's record of how energy was shared. Whether to remove
  it is the community's decision, not part of erasing one person. Agree it with the community admins, and if the
  record must stay, the supply stays too. In that case, stop here and record why the request cannot be completed.
- **A supply's own details:** deleting a supply also deletes its contract, distributor and Shelly details
  (`supplies_contract`, `supplies_distributor` and `supplies_shelly`, which cascade).

After step 4, this must return 0:

```sql
SELECT count(*) FROM supplies WHERE user_id = :user_id;
```

---

## 5. Memberships

A community admin can delete each membership with
`DELETE /api/v1/communities/{communityId}/memberships/{userId}`. MEM-029 refuses it while the person owns a supply
in that community, which step 4 has cleared. Deleting a membership deletes its member record, which is a set of
columns on the membership, and its tokens (`one_time_token_membership_id_fk` cascades).

This step is optional: step 6 deletes the remaining memberships in the same transaction as the account, as ACC-009
does. Either way, `community_memberships_user_id_fk` has no `ON DELETE` rule. The account can only be removed once
no membership references it.

```sql
SELECT count(*) FROM community_memberships WHERE user_id = :user_id;
```

---

## 6. The account, in one transaction

This is the one write done by hand. It mirrors ACC-009 and is done in **one transaction**, in this order:

1. Lock the account row, as ACC-009 does. Supply creation takes the same lock (SUP-002), so a supply created at the
   same time either makes the deletion stop or finds the account gone.
2. Check again, under the lock, that the account owns no supply (step 4). If it does, roll back.
3. Delete the account's remaining rows in `community_memberships` (ACC-009). Their tokens go with them through
   `one_time_token_membership_id_fk`.
4. Delete the account's row in `users` (ACC-009).

The existing foreign keys then do the rest:

| Data | Rule | Effect |
| --- | --- | --- |
| The account's tokens (`one_time_token.user_id`) | `one_time_token_user_id_fk`, cascade | deleted |
| A platform-admin invitation of the account (`platform_admin_invitation.user_id`), and its tokens | `platform_admin_invitation_user_id_fk`, cascade | deleted |
| Memberships the account invited (`community_memberships.invited_by`) | `community_memberships_invited_by_fk`, `SET NULL` | kept, with no inviter (MEM-003) |
| Platform-admin invitations the account sent (`platform_admin_invitation.invited_by`) | `platform_admin_invitation_invited_by_fk`, `SET NULL` | kept, with no inviter |
| Sharing agreements the account created or edited (`sharing_agreement.created_by`, `updated_by`) | `sharing_agreement_created_by_fk`, `sharing_agreement_updated_by_fk`, `SET NULL` | kept, with no creator or editor (ACC-009) |
| Distributor files the account uploaded (`sharing_agreement_file.uploaded_by`) | `sharing_agreement_file_uploaded_by_fk`, `SET NULL` | kept, with no uploader (ACC-009) |

Before committing, and again after, check that nothing still references the account:

```sql
SELECT
  (SELECT count(*) FROM users WHERE id = :user_id)                                AS account,
  (SELECT count(*) FROM community_memberships WHERE user_id = :user_id)           AS memberships,
  (SELECT count(*) FROM supplies WHERE user_id = :user_id)                        AS supplies,
  (SELECT count(*) FROM one_time_token WHERE user_id = :user_id)                  AS tokens,
  (SELECT count(*) FROM platform_admin_invitation WHERE user_id = :user_id)       AS invitations,
  (SELECT count(*) FROM community_memberships WHERE invited_by = :user_id)        AS invited,
  (SELECT count(*) FROM sharing_agreement WHERE created_by = :user_id OR updated_by = :user_id) AS agreements,
  (SELECT count(*) FROM sharing_agreement_file WHERE uploaded_by = :user_id)      AS files;
```

Before the deletion, `account` is 1 and `supplies` is 0. After the commit, every column is 0.

---

## 7. What this procedure does not reach

- **The time series in InfluxDB.** Consumption is stored by supply code, under the `cups` tag of
  `datadis_consumption_kwh` and of its monthly and yearly pre-aggregates
  (`docs/db/timeseries/influxdb/influxdb_schema.md`). Deleting the supply in PostgreSQL leaves that data in place.
  Note the CUPS codes from step 4 before the supplies are deleted, and remove their series with the InfluxDB
  tooling of `conluz-infra`.
- **Backups.** Database and InfluxDB backups taken before the erasure still hold the account and its data until
  they expire under the retention of `conluz-infra`. Record the date of the erasure, so that a restore from an older
  backup is followed by repeating this procedure.
- **Emails already sent** to the person, and anything outside this system.

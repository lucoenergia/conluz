-- anonymize.sql
--
-- Pseudonymizes member-identity PII so it never leaves the production host inside the
-- shippable bundle.
--
-- SAFETY: this script is run by create-snapshot.sh ONLY against the EPHEMERAL Postgres
-- container, never against the production database. Do run it on the ephemeral clone
-- -> do not run it against prod.
--
-- Design:
--   * Values are derived deterministically from the row primary key (users.id, a UUID),
--     so they are unique (respecting any UNIQUE constraints), per-member grouping survives,
--     and re-runs produce identical output for clean diffs across snapshots.
--   * The human-readable "number" column is reused where it makes the output friendlier.
--   * Consumption magnitudes and timestamps are kept intact; nothing is nulled out.
--   * The CUPS (supplies.code) is the join key to InfluxDB telemetry tags and is NEVER
--     touched. Only supplies.address is pseudonymized.
--   * Idempotent: safe to run twice (UPDATEs are pure functions of immutable keys).

BEGIN;

-- Users: the five PII columns confirmed against the Liquibase changelog
-- (personal_id, full_name, address, phone_number, email).
-- personal_id is a free-form string in the schema and is NOT checksum-validated by the
-- application (domain/shared/UserPersonalId is a plain wrapper), so a deterministic
-- synthetic string is sufficient.
--
-- SECRET SCRUB: password is a bcrypt hash. Exporting the real hashes enables offline
-- cracking and password-reuse pivoting, so every account is reset to a single known
-- bcrypt hash of the literal string "password". The hash below was generated with the
-- $2a$/cost-10 scheme used by the app's BCryptPasswordEncoder and verified to encode
-- "password". This keeps restored UAT/local environments loggable with a documented
-- throwaway credential instead of shipping production hashes.
UPDATE users SET
    full_name    = 'Member ' || number,
    email        = 'member-' || id || '@example.invalid',
    phone_number = '+34' || lpad((number)::text, 9, '0'),
    address      = 'Test address ' || number,
    personal_id  = 'ANON-' || id,
    password     = '$2a$10$KwbiT39JAHdpOn9BFnt5fexF3ud4pHZjBg5NbBgVMICDWWDCvSc0G';

-- Supplies: pseudonymize the address but KEEP the CUPS (code) intact.
-- Do overwrite supplies.address -> do not touch supplies.code.
UPDATE supplies SET
    address = 'Supply address ' || id;

-- SECRET SCRUB: third-party integration credentials are stored WITHOUT encryption at
-- rest, so the dumped username/password are working logins to the Datadis portal and the
-- Huawei FusionSolar API. Overwrite them with inert placeholders so no usable production
-- credential leaves the host. Restored environments must be reconfigured with their own
-- credentials before these integrations work.
UPDATE datadis_config SET
    username = 'anon-datadis',
    password = 'anonymized';

UPDATE huawei_config SET
    username = 'anon-huawei',
    password = 'anonymized';

-- DISABLE INTEGRATIONS: production may have these integrations enabled, but a restored
-- local/UAT environment must never call the third-party services (Datadis, Huawei
-- FusionSolar, Shelly). Force every integration off so restored stacks start inert;
-- operators re-enable explicitly after configuring their own credentials.
--
-- The snapshot reproduces production's schema, which may predate the changesets that added
-- the `enabled` column (or the config table). Guard each UPDATE on column existence so a
-- pre-`enabled` snapshot does not abort anonymization (psql runs with ON_ERROR_STOP=1). When
-- the column is absent the integration is off regardless: Liquibase creates `enabled` with a
-- DEFAULT false when the restored branch migrates on startup.
DO $$
DECLARE t text;
BEGIN
  FOREACH t IN ARRAY ARRAY['datadis_config','huawei_config','shelly_config'] LOOP
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = 'public' AND table_name = t AND column_name = 'enabled') THEN
      EXECUTE format('UPDATE %I SET enabled = false', t);
    END IF;
  END LOOP;
END $$;

COMMIT;

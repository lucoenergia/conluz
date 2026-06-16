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
UPDATE users SET
    full_name    = 'Member ' || number,
    email        = 'member-' || id || '@example.invalid',
    phone_number = '+34' || lpad((number)::text, 9, '0'),
    address      = 'Test address ' || number,
    personal_id  = 'ANON-' || id;

-- Supplies: pseudonymize the address but KEEP the CUPS (code) intact.
-- Do overwrite supplies.address -> do not touch supplies.code.
UPDATE supplies SET
    address = 'Supply address ' || id;

COMMIT;

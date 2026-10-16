package org.lucoenergia.conluz.infrastructure.shared.db.liquibase;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lucoenergia.conluz.infrastructure.shared.db.liquibase.LiquibaseTestSupport.applyRest;
import static org.lucoenergia.conluz.infrastructure.shared.db.liquibase.LiquibaseTestSupport.applyUpToChangeSet;
import static org.lucoenergia.conluz.infrastructure.shared.db.liquibase.LiquibaseTestSupport.freshDatabase;
import static org.lucoenergia.conluz.infrastructure.shared.db.liquibase.LiquibaseTestSupport.rollbackToChangeSet;

/**
 * The changeset that copies each user's data into their member record again, for users with exactly one membership
 * that still holds a copy (#396).
 */
class MemberRecordResyncMigrationIntegrationTest {

    /** The last changeset before the one under test. */
    private static final String BOUNDARY_CHANGESET_ID = "add_unique_constraint_to_datadis_config_community_id";

    private static final Instant ACCEPTED = Instant.parse("2026-01-01T00:00:00Z");

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withUsername("luz")
            .withPassword("blank");

    @BeforeAll
    static void startContainer() {
        POSTGRES.start();
    }

    @Test
    void aSingleMembershipWhoseRecordDrifted_isCopiedFromItsUserAgain() throws Exception {
        try (Connection connection = freshDatabase(POSTGRES, "drifted")) {
            applyUpToChangeSet(connection, BOUNDARY_CHANGESET_ID);
            UUID community = insertCommunity(connection, "A");
            UUID edited = insertUser(connection, "11111111A", 7, "Edited Name", "edited@example.org",
                    "Calle Nueva 1", "600000001");
            UUID blankEmail = insertUser(connection, "22222222B", 8, "Blank Email", " \t\n ", null, null);
            UUID editedInA = insertMembership(connection, edited, community,
                    record("Old Name", 1, "old@example.org", "Calle Vieja 1", "699999999"), ACCEPTED, null, null);
            UUID blankEmailInA = insertMembership(connection, blankEmail, community,
                    record("Blank Email", 8, "previous@example.org", "Calle Vieja 2", "699999998"), ACCEPTED,
                    null, null);

            applyRest(connection);

            assertRecord(connection, editedInA,
                    record("Edited Name", 7, "edited@example.org", "Calle Nueva 1", "600000001"));
            assertRecord(connection, blankEmailInA, record("Blank Email", 8, null, null, null));
        }
    }

    @Test
    void aUserWithTwoMemberships_keepsBothRecordsUnchanged() throws Exception {
        try (Connection connection = freshDatabase(POSTGRES, "two")) {
            applyUpToChangeSet(connection, BOUNDARY_CHANGESET_ID);
            UUID communityA = insertCommunity(connection, "A");
            UUID communityB = insertCommunity(connection, "B");
            UUID user = insertUser(connection, "11111111A", 7, "Account Name", "account@example.org",
                    "Calle Cuenta 1", "600000001");
            Map<String, Object> recordA = record("Name in A", 1, "a@example.org", "Calle A 1", "611111111");
            Map<String, Object> recordB = record("Name in B", 2, "b@example.org", "Calle B 1", "622222222");
            UUID inA = insertMembership(connection, user, communityA, recordA, ACCEPTED, null, null);
            UUID inB = insertMembership(connection, user, communityB, recordB, ACCEPTED, null, null);

            applyRest(connection);

            assertRecord(connection, inA, recordA);
            assertRecord(connection, inB, recordB);
        }
    }

    @Test
    void aSingleMembershipCreatedByRegistration_keepsItsRecordUnchanged() throws Exception {
        try (Connection connection = freshDatabase(POSTGRES, "registered")) {
            applyUpToChangeSet(connection, BOUNDARY_CHANGESET_ID);
            UUID community = insertCommunity(connection, "A");
            UUID registrar = insertUser(connection, "99999999Z", 99, "Registrar", "registrar@example.org", null,
                    null);
            insertMembership(connection, registrar, community,
                    record("Registrar", 99, "registrar@example.org", null, null), ACCEPTED, null, null);
            UUID pending = insertUser(connection, "11111111A", null, "Pending", "pending@example.org", null, null);
            UUID invited = insertUser(connection, "22222222B", null, "Invited", "invited@example.org", null, null);
            UUID orphaned = insertUser(connection, "33333333C", null, "Orphaned", "orphaned@example.org", null,
                    null);
            Map<String, Object> pendingRecord = record("Pending", 42, "pending@example.org", "Calle P 1",
                    "611111111");
            Map<String, Object> invitedRecord = record("Invited", 43, "invited@example.org", "Calle I 1",
                    "622222222");
            Map<String, Object> orphanedRecord = record("Orphaned", 44, "orphaned@example.org", "Calle O 1",
                    "633333333");
            // Awaiting acceptance, registered by a user who still exists
            UUID pendingInA = insertMembership(connection, pending, community, pendingRecord, null, registrar, null);
            // Accepted after an invitation
            UUID invitedInA = insertMembership(connection, invited, community, invitedRecord, ACCEPTED, registrar,
                    ACCEPTED);
            // Accepted after an invitation, its registrar since deleted
            UUID orphanedInA = insertMembership(connection, orphaned, community, orphanedRecord, ACCEPTED, null,
                    ACCEPTED);

            applyRest(connection);

            assertRecord(connection, pendingInA, pendingRecord);
            assertRecord(connection, invitedInA, invitedRecord);
            assertRecord(connection, orphanedInA, orphanedRecord);
        }
    }

    @Test
    void rollingBackRestoresNothing_andItCanBeAppliedAgain() throws Exception {
        try (Connection connection = freshDatabase(POSTGRES, "rollback")) {
            applyUpToChangeSet(connection, BOUNDARY_CHANGESET_ID);
            UUID community = insertCommunity(connection, "A");
            UUID user = insertUser(connection, "11111111A", 7, "Edited Name", "edited@example.org", null, null);
            UUID membership = insertMembership(connection, user, community,
                    record("Old Name", 1, "old@example.org", null, null), ACCEPTED, null, null);
            Map<String, Object> resynced = record("Edited Name", 7, "edited@example.org", null, null);
            applyRest(connection);

            rollbackToChangeSet(connection, BOUNDARY_CHANGESET_ID);

            assertRecord(connection, membership, resynced);

            applyRest(connection);

            assertRecord(connection, membership, resynced);
        }
    }

    // --- Helpers ---

    private static Map<String, Object> record(String fullName, Integer memberNumber, String contactEmail,
                                              String address, String phoneNumber) {
        Map<String, Object> record = new HashMap<>();
        record.put("full_name", fullName);
        record.put("member_number", memberNumber);
        record.put("contact_email", contactEmail);
        record.put("address", address);
        record.put("phone_number", phoneNumber);
        return record;
    }

    private static UUID insertCommunity(Connection connection, String code) throws SQLException {
        UUID id = UUID.randomUUID();
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO communities (id, name, code, enabled) VALUES (?, ?, ?, true)")) {
            statement.setObject(1, id);
            statement.setString(2, "Community " + code);
            statement.setString(3, "CODE-" + code);
            statement.executeUpdate();
        }
        return id;
    }

    private static UUID insertUser(Connection connection, String personalId, Integer number, String fullName,
                                   String email, String address, String phoneNumber) throws SQLException {
        UUID id = UUID.randomUUID();
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO users (id, personal_id, number, password, full_name, email, address, phone_number, "
                        + "enabled) VALUES (?, ?, ?, 'hash', ?, ?, ?, ?, true)")) {
            statement.setObject(1, id);
            statement.setString(2, personalId);
            statement.setObject(3, number);
            statement.setString(4, fullName);
            statement.setString(5, email);
            statement.setString(6, address);
            statement.setString(7, phoneNumber);
            statement.executeUpdate();
        }
        return id;
    }

    private static UUID insertMembership(Connection connection, UUID userId, UUID communityId,
                                         Map<String, Object> record, Instant acceptedAt, UUID invitedBy,
                                         Instant invitedAt) throws SQLException {
        UUID id = UUID.randomUUID();
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO community_memberships (id, user_id, community_id, role, enabled, full_name, "
                        + "member_number, contact_email, address, phone_number, accepted_at, invited_by, invited_at) "
                        + "VALUES (?, ?, ?, 'COMMUNITY_MEMBER', true, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            statement.setObject(1, id);
            statement.setObject(2, userId);
            statement.setObject(3, communityId);
            statement.setString(4, (String) record.get("full_name"));
            statement.setObject(5, record.get("member_number"));
            statement.setString(6, (String) record.get("contact_email"));
            statement.setString(7, (String) record.get("address"));
            statement.setString(8, (String) record.get("phone_number"));
            statement.setTimestamp(9, acceptedAt == null ? null : Timestamp.from(acceptedAt));
            statement.setObject(10, invitedBy);
            statement.setTimestamp(11, invitedAt == null ? null : Timestamp.from(invitedAt));
            statement.executeUpdate();
        }
        return id;
    }

    private static void assertRecord(Connection connection, UUID membership, Map<String, Object> expected)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT full_name, member_number, contact_email, address, phone_number FROM community_memberships "
                        + "WHERE id = ?")) {
            statement.setObject(1, membership);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next(), "The membership should still exist");
                Map<String, Object> actual = new HashMap<>();
                for (String column : expected.keySet()) {
                    actual.put(column, resultSet.getObject(column));
                }
                assertEquals(expected, actual);
            }
        }
    }
}

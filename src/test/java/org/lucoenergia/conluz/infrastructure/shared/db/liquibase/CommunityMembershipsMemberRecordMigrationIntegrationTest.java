package org.lucoenergia.conluz.infrastructure.shared.db.liquibase;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lucoenergia.conluz.infrastructure.shared.db.liquibase.LiquibaseTestSupport.applyRest;
import static org.lucoenergia.conluz.infrastructure.shared.db.liquibase.LiquibaseTestSupport.applyUpToChangeSet;
import static org.lucoenergia.conluz.infrastructure.shared.db.liquibase.LiquibaseTestSupport.freshDatabase;
import static org.lucoenergia.conluz.infrastructure.shared.db.liquibase.LiquibaseTestSupport.rollbackToChangeSet;
import static org.lucoenergia.conluz.infrastructure.shared.db.liquibase.LiquibaseTestSupport.seed;

/**
 * The changesets that add member records and invitation state to community_memberships (#388): the
 * columns, the backfill from users, and the NOT NULL full name.
 */
class CommunityMembershipsMemberRecordMigrationIntegrationTest {

    /** The last changeset before the ones under test. */
    private static final String BOUNDARY_CHANGESET_ID = "create_one_time_token_table";
    private static final String BACKFILL_CHANGESET_ID = "backfill_member_record_for_community_memberships";
    private static final String NOT_NULL_CHANGESET_ID = "make_community_memberships_full_name_not_null";

    private static final Set<String> COLUMNS_BEFORE = Set.of("id", "user_id", "community_id", "role", "enabled",
            "investment_eur");
    private static final Set<String> NEW_COLUMNS = Set.of("full_name", "member_number", "contact_email", "address",
            "phone_number", "accepted_at", "invited_by", "invited_at");

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withUsername("luz")
            .withPassword("blank");

    @BeforeAll
    static void startContainer() {
        POSTGRES.start();
    }

    @Test
    void backfill_copiesEachMembersRecordFromItsUser_andMarksItAccepted() throws Exception {
        try (Connection connection = freshDatabase(POSTGRES, "backfill")) {
            applyUpToChangeSet(connection, BOUNDARY_CHANGESET_ID);
            UUID communityA = insertCommunity(connection, "A");
            UUID communityB = insertCommunity(connection, "B");
            UUID full = insertUser(connection, "11111111A", 1, "Full User", "full@example.org",
                    "Calle Mayor 1", "600000001");
            UUID emptyEmail = insertUser(connection, "22222222B", 2, "Empty Email", "",
                    "Calle Mayor 2", "600000002");
            UUID blankEmail = insertUser(connection, "33333333C", 3, "Blank Email", " \t\n ",
                    "Calle Mayor 3", "600000003");
            UUID noAddressNorPhone = insertUser(connection, "44444444D", 4, "No Address", "noaddress@example.org",
                    null, null);
            UUID fullInA = insertMembership(connection, full, communityA);
            UUID fullInB = insertMembership(connection, full, communityB);
            UUID emptyEmailInA = insertMembership(connection, emptyEmail, communityA);
            UUID blankEmailInA = insertMembership(connection, blankEmail, communityA);
            UUID noAddressInB = insertMembership(connection, noAddressNorPhone, communityB);

            applyRest(connection);

            assertRecord(connection, fullInA, "Full User", 1, "full@example.org", "Calle Mayor 1", "600000001");
            assertRecord(connection, fullInB, "Full User", 1, "full@example.org", "Calle Mayor 1", "600000001");
            assertRecord(connection, emptyEmailInA, "Empty Email", 2, null, "Calle Mayor 2", "600000002");
            assertRecord(connection, blankEmailInA, "Blank Email", 3, null, "Calle Mayor 3", "600000003");
            assertRecord(connection, noAddressInB, "No Address", 4, "noaddress@example.org", null, null);

            Set<Timestamp> acceptedAts = new HashSet<>();
            for (UUID membership : Set.of(fullInA, fullInB, emptyEmailInA, blankEmailInA, noAddressInB)) {
                Map<String, Object> row = row(connection, membership);
                assertNotNull(row.get("accepted_at"), "Every existing membership is accepted");
                assertNull(row.get("invited_by"));
                assertNull(row.get("invited_at"));
                acceptedAts.add((Timestamp) row.get("accepted_at"));
            }
            assertEquals(1, acceptedAts.size(), "Every existing membership is accepted at the same migration instant");
        }
    }

    @Test
    void addsTheColumns_withOnlyTheFullNameRequired_andTheInviterSetToNullOnDelete() throws Exception {
        try (Connection connection = freshDatabase(POSTGRES, "schema")) {
            applyUpToChangeSet(connection, BOUNDARY_CHANGESET_ID);
            applyRest(connection);

            Set<String> expected = new HashSet<>(COLUMNS_BEFORE);
            expected.addAll(NEW_COLUMNS);
            assertEquals(expected, columns(connection));
            Set<String> nullableNewColumns = new HashSet<>(NEW_COLUMNS);
            nullableNewColumns.remove("full_name");
            assertTrue(nullableColumns(connection).containsAll(nullableNewColumns));
            assertFalse(nullableColumns(connection).contains("full_name"));
            assertEquals("SET NULL", inviterForeignKeyDeleteRule(connection));
        }
    }

    @Test
    void deletingTheInvitingUser_keepsTheMembership_withNoInviter() throws Exception {
        try (Connection connection = freshDatabase(POSTGRES, "inviter")) {
            applyUpToChangeSet(connection, BOUNDARY_CHANGESET_ID);
            applyRest(connection);
            UUID community = insertCommunity(connection, "A");
            UUID member = insertUser(connection, "11111111A", 1, "Member", "member@example.org", null, null);
            UUID inviter = insertUser(connection, "22222222B", 2, "Inviter", "inviter@example.org", null, null);
            UUID membership = UUID.randomUUID();
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO community_memberships (id, user_id, community_id, role, enabled, full_name, "
                            + "invited_by, invited_at) VALUES (?, ?, ?, 'COMMUNITY_MEMBER', true, 'Member', ?, now())")) {
                statement.setObject(1, membership);
                statement.setObject(2, member);
                statement.setObject(3, community);
                statement.setObject(4, inviter);
                statement.executeUpdate();
            }

            seed(connection, "DELETE FROM users WHERE id = '" + inviter + "'");

            Map<String, Object> row = row(connection, membership);
            assertNull(row.get("invited_by"));
            assertNotNull(row.get("invited_at"));
            assertEquals("Member", row.get("full_name"));
        }
    }

    @Test
    void notNullStepHalts_whenAMembershipHasNoFullName() throws Exception {
        try (Connection connection = freshDatabase(POSTGRES, "halts")) {
            applyUpToChangeSet(connection, BACKFILL_CHANGESET_ID);
            UUID community = insertCommunity(connection, "A");
            UUID user = insertUser(connection, "11111111A", 1, "Member", "member@example.org", null, null);
            insertMembership(connection, user, community);
            if (!connection.getAutoCommit()) {
                connection.commit();
            }

            Exception failure = assertThrows(Exception.class, () -> applyRest(connection));

            if (!connection.getAutoCommit()) {
                connection.rollback();
            }
            assertTrue(messageChainContains(failure, "some memberships have no full name"),
                    "The failure should carry the precondition's message");
            assertFalse(changesetApplied(connection, NOT_NULL_CHANGESET_ID));
            assertTrue(nullableColumns(connection).contains("full_name"));
        }
    }

    @Test
    void rollingBackRestoresTheSchemaAsBefore_andItCanBeAppliedAgain() throws Exception {
        try (Connection connection = freshDatabase(POSTGRES, "rollback")) {
            applyUpToChangeSet(connection, BOUNDARY_CHANGESET_ID);
            UUID community = insertCommunity(connection, "A");
            UUID user = insertUser(connection, "11111111A", 1, "Member", "", "Calle Mayor 1", null);
            UUID membership = insertMembership(connection, user, community);
            applyRest(connection);

            rollbackToChangeSet(connection, BOUNDARY_CHANGESET_ID);

            assertEquals(COLUMNS_BEFORE, columns(connection));
            assertNull(inviterForeignKeyDeleteRule(connection));

            applyRest(connection);

            assertRecord(connection, membership, "Member", 1, null, "Calle Mayor 1", null);
            assertNotNull(row(connection, membership).get("accepted_at"));
            assertFalse(nullableColumns(connection).contains("full_name"));
        }
    }

    // --- Helpers ---

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

    private static UUID insertUser(Connection connection, String personalId, int number, String fullName,
                                   String email, String address, String phoneNumber) throws SQLException {
        UUID id = UUID.randomUUID();
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO users (id, personal_id, number, password, full_name, email, address, phone_number, "
                        + "enabled) VALUES (?, ?, ?, 'hash', ?, ?, ?, ?, true)")) {
            statement.setObject(1, id);
            statement.setString(2, personalId);
            statement.setInt(3, number);
            statement.setString(4, fullName);
            statement.setString(5, email);
            statement.setString(6, address);
            statement.setString(7, phoneNumber);
            statement.executeUpdate();
        }
        return id;
    }

    private static UUID insertMembership(Connection connection, UUID userId, UUID communityId) throws SQLException {
        UUID id = UUID.randomUUID();
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO community_memberships (id, user_id, community_id, role, enabled) "
                        + "VALUES (?, ?, ?, 'COMMUNITY_MEMBER', true)")) {
            statement.setObject(1, id);
            statement.setObject(2, userId);
            statement.setObject(3, communityId);
            statement.executeUpdate();
        }
        return id;
    }

    private static void assertRecord(Connection connection, UUID membership, String fullName, Integer memberNumber,
                                     String contactEmail, String address, String phoneNumber) throws SQLException {
        Map<String, Object> row = row(connection, membership);
        assertEquals(fullName, row.get("full_name"));
        assertEquals(memberNumber, row.get("member_number"));
        assertEquals(contactEmail, row.get("contact_email"));
        assertEquals(address, row.get("address"));
        assertEquals(phoneNumber, row.get("phone_number"));
    }

    private static Map<String, Object> row(Connection connection, UUID membership) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT * FROM community_memberships WHERE id = ?")) {
            statement.setObject(1, membership);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next(), "The membership should still exist");
                Map<String, Object> row = new HashMap<>();
                for (int i = 1; i <= resultSet.getMetaData().getColumnCount(); i++) {
                    row.put(resultSet.getMetaData().getColumnName(i), resultSet.getObject(i));
                }
                return row;
            }
        }
    }

    private static String inviterForeignKeyDeleteRule(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT delete_rule FROM information_schema.referential_constraints "
                        + "WHERE constraint_name = 'community_memberships_invited_by_fk'");
             ResultSet resultSet = statement.executeQuery()) {
            return resultSet.next() ? resultSet.getString(1) : null;
        }
    }

    private static Set<String> columns(Connection connection) throws SQLException {
        return strings(connection, "SELECT column_name FROM information_schema.columns "
                + "WHERE table_name = 'community_memberships'");
    }

    private static Set<String> nullableColumns(Connection connection) throws SQLException {
        return strings(connection, "SELECT column_name FROM information_schema.columns "
                + "WHERE table_name = 'community_memberships' AND is_nullable = 'YES'");
    }

    private static Set<String> strings(Connection connection, String sql) throws SQLException {
        Set<String> values = new HashSet<>();
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                values.add(resultSet.getString(1));
            }
        }
        return values;
    }

    private static boolean changesetApplied(Connection connection, String changesetId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT count(*) FROM databasechangelog WHERE id = ?")) {
            statement.setString(1, changesetId);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1) > 0;
            }
        }
    }

    private static boolean messageChainContains(Throwable throwable, String text) {
        for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
            if (cause.getMessage() != null && cause.getMessage().contains(text)) {
                return true;
            }
        }
        return false;
    }
}

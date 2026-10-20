package org.lucoenergia.conluz.infrastructure.shared.db.liquibase;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lucoenergia.conluz.infrastructure.shared.db.liquibase.LiquibaseTestSupport.applyRest;
import static org.lucoenergia.conluz.infrastructure.shared.db.liquibase.LiquibaseTestSupport.applyUpToChangeSet;
import static org.lucoenergia.conluz.infrastructure.shared.db.liquibase.LiquibaseTestSupport.continueUpToChangeSet;
import static org.lucoenergia.conluz.infrastructure.shared.db.liquibase.LiquibaseTestSupport.freshDatabase;
import static org.lucoenergia.conluz.infrastructure.shared.db.liquibase.LiquibaseTestSupport.rollbackToChangeSet;
import static org.lucoenergia.conluz.infrastructure.shared.db.liquibase.LiquibaseTestSupport.seed;

/**
 * The changesets that bind one-time tokens to memberships (#391): add_membership_id_to_one_time_token and
 * bind_one_time_token_active_index_to_membership.
 */
class OneTimeTokenMembershipMigrationIntegrationTest {

    /** The last changeset before the ones under test. */
    private static final String BOUNDARY_CHANGESET_ID = "make_users_password_nullable";
    private static final String COLUMN_CHANGESET_ID = "add_membership_id_to_one_time_token";
    private static final String INDEX_CHANGESET_ID = "bind_one_time_token_active_index_to_membership";

    private static final String ORIGINAL_ACTIVE_INDEX = "CREATE UNIQUE INDEX one_time_token_one_active_uq "
            + "ON public.one_time_token USING btree (user_id, purpose) "
            + "WHERE ((used_at IS NULL) AND (revoked_at IS NULL))";
    private static final String NARROWED_ACTIVE_INDEX = "CREATE UNIQUE INDEX one_time_token_one_active_uq "
            + "ON public.one_time_token USING btree (user_id, purpose) "
            + "WHERE ((membership_id IS NULL) AND (used_at IS NULL) AND (revoked_at IS NULL))";
    private static final String MEMBERSHIP_ACTIVE_INDEX = "CREATE UNIQUE INDEX one_time_token_membership_one_active_uq "
            + "ON public.one_time_token USING btree (membership_id) "
            + "WHERE ((membership_id IS NOT NULL) AND (used_at IS NULL) AND (revoked_at IS NULL))";
    private static final String MEMBERSHIP_CREATED_AT_INDEX = "CREATE INDEX one_time_token_membership_created_at_idx "
            + "ON public.one_time_token USING btree (membership_id, created_at)";

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withUsername("luz")
            .withPassword("blank");

    @BeforeAll
    static void startContainer() {
        POSTGRES.start();
    }

    @Test
    void existingTokensKeepNoMembership_andTheIndexesHaveTheirExactConditions() throws Exception {
        try (Connection connection = freshDatabase(POSTGRES, "applied")) {
            applyUpToChangeSet(connection, BOUNDARY_CHANGESET_ID);
            UUID user = insertUser(connection, "12345678Z", 1);
            UUID revoked = insertToken(connection, user, null, "PASSWORD_RESET", "a".repeat(64));
            seed(connection, "UPDATE one_time_token SET revoked_at = now() WHERE id = '" + revoked + "'");
            UUID active = insertToken(connection, user, null, "PASSWORD_RESET", "b".repeat(64));

            continueUpToChangeSet(connection, INDEX_CHANGESET_ID);

            assertNull(membershipOf(connection, revoked));
            assertNull(membershipOf(connection, active));
            assertEquals("YES", scalar(connection, "SELECT is_nullable FROM information_schema.columns "
                    + "WHERE table_name = 'one_time_token' AND column_name = 'membership_id'"));
            assertEquals("CASCADE", scalar(connection, "SELECT delete_rule FROM information_schema.referential_constraints "
                    + "WHERE constraint_name = 'one_time_token_membership_id_fk'"));
            Map<String, String> indexes = indexDefinitions(connection);
            assertEquals(NARROWED_ACTIVE_INDEX, indexes.get("one_time_token_one_active_uq"));
            assertEquals(MEMBERSHIP_ACTIVE_INDEX, indexes.get("one_time_token_membership_one_active_uq"));
            assertEquals(MEMBERSHIP_CREATED_AT_INDEX, indexes.get("one_time_token_membership_created_at_idx"));
        }
    }

    @Test
    void allowsActiveInvitationTokensOfOneUserInTwoMemberships_butOneActivePerMembership() throws Exception {
        try (Connection connection = freshDatabase(POSTGRES, "per_membership")) {
            applyUpToChangeSet(connection, BOUNDARY_CHANGESET_ID);
            applyRest(connection);
            UUID user = insertUser(connection, "12345678Z", 1);
            UUID first = insertMembership(connection, user, insertCommunity(connection, "A"));
            UUID second = insertMembership(connection, user, insertCommunity(connection, "B"));

            insertToken(connection, user, first, "INVITATION", "a".repeat(64));
            insertToken(connection, user, second, "INVITATION", "b".repeat(64));

            assertThrows(SQLException.class, () -> insertToken(connection, user, first, "INVITATION", "c".repeat(64)));
        }
    }

    @Test
    void stillAllowsOneActivePasswordResetTokenPerUser_alongsideAnInvitationToken() throws Exception {
        try (Connection connection = freshDatabase(POSTGRES, "per_user")) {
            applyUpToChangeSet(connection, BOUNDARY_CHANGESET_ID);
            applyRest(connection);
            UUID user = insertUser(connection, "12345678Z", 1);
            UUID membership = insertMembership(connection, user, insertCommunity(connection, "A"));

            insertToken(connection, user, membership, "INVITATION", "a".repeat(64));
            insertToken(connection, user, null, "PASSWORD_RESET", "b".repeat(64));

            assertThrows(SQLException.class, () -> insertToken(connection, user, null, "PASSWORD_RESET", "c".repeat(64)));
        }
    }

    @Test
    void deletingAMembership_deletesItsTokensOnly() throws Exception {
        try (Connection connection = freshDatabase(POSTGRES, "cascade")) {
            applyUpToChangeSet(connection, BOUNDARY_CHANGESET_ID);
            applyRest(connection);
            UUID user = insertUser(connection, "12345678Z", 1);
            UUID deleted = insertMembership(connection, user, insertCommunity(connection, "A"));
            UUID kept = insertMembership(connection, user, insertCommunity(connection, "B"));
            UUID deletedToken = insertToken(connection, user, deleted, "INVITATION", "a".repeat(64));
            UUID keptToken = insertToken(connection, user, kept, "INVITATION", "b".repeat(64));
            UUID resetToken = insertToken(connection, user, null, "PASSWORD_RESET", "c".repeat(64));

            seed(connection, "DELETE FROM community_memberships WHERE id = '" + deleted + "'");

            assertFalse(tokenExists(connection, deletedToken));
            assertTrue(tokenExists(connection, keptToken));
            assertTrue(tokenExists(connection, resetToken));
        }
    }

    @Test
    void halts_whenTheColumnAlreadyExists_andAppliesNothing() throws Exception {
        try (Connection connection = freshDatabase(POSTGRES, "halts_column")) {
            applyUpToChangeSet(connection, BOUNDARY_CHANGESET_ID);
            seed(connection, "ALTER TABLE one_time_token ADD COLUMN membership_id UUID");
            commit(connection);

            Exception failure = assertThrows(Exception.class, () -> applyRest(connection));

            rollback(connection);
            assertTrue(messageChainContains(failure, "Cannot add membership_id to one_time_token"));
            assertFalse(changesetApplied(connection, COLUMN_CHANGESET_ID));
            assertFalse(changesetApplied(connection, INDEX_CHANGESET_ID));
            assertEquals(ORIGINAL_ACTIVE_INDEX, indexDefinitions(connection).get("one_time_token_one_active_uq"));
        }
    }

    @Test
    void halts_whenTheActiveIndexIsNotTheOriginal_andAppliesNothing() throws Exception {
        try (Connection connection = freshDatabase(POSTGRES, "halts_index")) {
            applyUpToChangeSet(connection, COLUMN_CHANGESET_ID);
            String altered = "CREATE UNIQUE INDEX one_time_token_one_active_uq ON public.one_time_token USING btree "
                    + "(user_id, purpose) WHERE (used_at IS NULL)";
            seed(connection, "DROP INDEX one_time_token_one_active_uq");
            seed(connection, altered);
            commit(connection);

            Exception failure = assertThrows(Exception.class, () -> applyRest(connection));

            rollback(connection);
            assertTrue(messageChainContains(failure, "Cannot bind the active-token index of one_time_token"));
            assertFalse(changesetApplied(connection, INDEX_CHANGESET_ID));
            Map<String, String> indexes = indexDefinitions(connection);
            assertEquals(altered, indexes.get("one_time_token_one_active_uq"));
            assertFalse(indexes.containsKey("one_time_token_membership_one_active_uq"));
        }
    }

    @Test
    void halts_whenTheMembershipIndexAlreadyExists_andAppliesNothing() throws Exception {
        try (Connection connection = freshDatabase(POSTGRES, "halts_membership_index")) {
            applyUpToChangeSet(connection, COLUMN_CHANGESET_ID);
            seed(connection, "CREATE INDEX one_time_token_membership_one_active_uq ON one_time_token (membership_id)");
            commit(connection);

            Exception failure = assertThrows(Exception.class, () -> applyRest(connection));

            rollback(connection);
            assertTrue(messageChainContains(failure, "Cannot bind the active-token index of one_time_token"));
            assertFalse(changesetApplied(connection, INDEX_CHANGESET_ID));
            assertEquals(ORIGINAL_ACTIVE_INDEX, indexDefinitions(connection).get("one_time_token_one_active_uq"));
        }
    }

    @Test
    void rollingBack_deletesTheInvitationTokens_restoresTheOriginalIndex_andCanBeAppliedAgain() throws Exception {
        try (Connection connection = freshDatabase(POSTGRES, "rollback")) {
            applyUpToChangeSet(connection, BOUNDARY_CHANGESET_ID);
            applyRest(connection);
            UUID user = insertUser(connection, "12345678Z", 1);
            UUID first = insertMembership(connection, user, insertCommunity(connection, "A"));
            UUID second = insertMembership(connection, user, insertCommunity(connection, "B"));
            UUID firstInvitation = insertToken(connection, user, first, "INVITATION", "a".repeat(64));
            UUID secondInvitation = insertToken(connection, user, second, "INVITATION", "b".repeat(64));
            UUID reset = insertToken(connection, user, null, "PASSWORD_RESET", "c".repeat(64));
            commit(connection);

            rollbackToChangeSet(connection, BOUNDARY_CHANGESET_ID);

            assertFalse(tokenExists(connection, firstInvitation));
            assertFalse(tokenExists(connection, secondInvitation));
            assertTrue(tokenExists(connection, reset));
            assertEquals("0", scalar(connection, "SELECT count(*) FROM information_schema.columns "
                    + "WHERE table_name = 'one_time_token' AND column_name = 'membership_id'"));
            Map<String, String> indexes = indexDefinitions(connection);
            assertEquals(ORIGINAL_ACTIVE_INDEX, indexes.get("one_time_token_one_active_uq"));
            assertFalse(indexes.containsKey("one_time_token_membership_one_active_uq"));
            assertFalse(indexes.containsKey("one_time_token_membership_created_at_idx"));

            continueUpToChangeSet(connection, INDEX_CHANGESET_ID);

            assertTrue(changesetApplied(connection, INDEX_CHANGESET_ID));
            assertNull(membershipOf(connection, reset));
            indexes = indexDefinitions(connection);
            assertEquals(NARROWED_ACTIVE_INDEX, indexes.get("one_time_token_one_active_uq"));
            assertEquals(MEMBERSHIP_ACTIVE_INDEX, indexes.get("one_time_token_membership_one_active_uq"));
            assertEquals(MEMBERSHIP_CREATED_AT_INDEX, indexes.get("one_time_token_membership_created_at_idx"));
        }
    }

    // --- Helpers ---

    private static UUID insertUser(Connection connection, String personalId, int number) throws SQLException {
        UUID id = UUID.randomUUID();
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO users (id, personal_id, number, password, full_name, email, enabled) "
                        + "VALUES (?, ?, ?, 'hash', 'Seeded user', ?, true)")) {
            statement.setObject(1, id);
            statement.setString(2, personalId);
            statement.setInt(3, number);
            statement.setString(4, "seeded" + number + "@example.org");
            statement.executeUpdate();
        }
        return id;
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

    private static UUID insertMembership(Connection connection, UUID userId, UUID communityId) throws SQLException {
        UUID id = UUID.randomUUID();
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO community_memberships (id, user_id, community_id, role, enabled, full_name) "
                        + "VALUES (?, ?, ?, 'COMMUNITY_MEMBER', true, 'Member')")) {
            statement.setObject(1, id);
            statement.setObject(2, userId);
            statement.setObject(3, communityId);
            statement.executeUpdate();
        }
        return id;
    }

    private static UUID insertToken(Connection connection, UUID userId, UUID membershipId, String purpose, String hash)
            throws SQLException {
        UUID id = UUID.randomUUID();
        String sql = membershipId == null
                ? "INSERT INTO one_time_token (id, user_id, purpose, token_hash, created_at, expires_at) "
                + "VALUES (?, ?, ?, ?, now(), now() + interval '1 day')"
                : "INSERT INTO one_time_token (id, user_id, purpose, token_hash, created_at, expires_at, membership_id) "
                + "VALUES (?, ?, ?, ?, now(), now() + interval '7 days', ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, id);
            statement.setObject(2, userId);
            statement.setString(3, purpose);
            statement.setString(4, hash);
            if (membershipId != null) {
                statement.setObject(5, membershipId);
            }
            statement.executeUpdate();
        }
        return id;
    }

    private static UUID membershipOf(Connection connection, UUID tokenId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT membership_id FROM one_time_token WHERE id = ?")) {
            statement.setObject(1, tokenId);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next());
                return resultSet.getObject(1, UUID.class);
            }
        }
    }

    private static boolean tokenExists(Connection connection, UUID tokenId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT count(*) FROM one_time_token WHERE id = ?")) {
            statement.setObject(1, tokenId);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1) > 0;
            }
        }
    }

    private static Map<String, String> indexDefinitions(Connection connection) throws SQLException {
        Map<String, String> definitions = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT indexname, indexdef FROM pg_indexes WHERE tablename = 'one_time_token'");
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                definitions.put(resultSet.getString(1), resultSet.getString(2));
            }
        }
        return definitions;
    }

    private static String scalar(Connection connection, String sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            assertTrue(resultSet.next());
            return resultSet.getString(1);
        }
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

    private static void commit(Connection connection) throws SQLException {
        if (!connection.getAutoCommit()) {
            connection.commit();
        }
    }

    private static void rollback(Connection connection) throws SQLException {
        if (!connection.getAutoCommit()) {
            connection.rollback();
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

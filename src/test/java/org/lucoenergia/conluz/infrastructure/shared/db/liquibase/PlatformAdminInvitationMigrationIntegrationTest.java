package org.lucoenergia.conluz.infrastructure.shared.db.liquibase;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
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
 * The changesets of platform-admin invitations (#452): create_platform_admin_invitation_table,
 * add_platform_admin_invitation_id_to_one_time_token and
 * bind_one_time_token_active_index_to_platform_admin_invitation.
 */
class PlatformAdminInvitationMigrationIntegrationTest {

    /** The last changeset before the ones under test. */
    private static final String BOUNDARY_CHANGESET_ID = "set_null_sharing_agreement_file_uploaded_by_on_user_delete";
    private static final String TABLE_CHANGESET_ID = "create_platform_admin_invitation_table";
    private static final String COLUMN_CHANGESET_ID = "add_platform_admin_invitation_id_to_one_time_token";
    private static final String INDEX_CHANGESET_ID = "bind_one_time_token_active_index_to_platform_admin_invitation";

    private static final String MEMBERSHIP_NARROWED_ACTIVE_INDEX = "CREATE UNIQUE INDEX one_time_token_one_active_uq "
            + "ON public.one_time_token USING btree (user_id, purpose) "
            + "WHERE ((membership_id IS NULL) AND (used_at IS NULL) AND (revoked_at IS NULL))";
    private static final String NARROWED_ACTIVE_INDEX = "CREATE UNIQUE INDEX one_time_token_one_active_uq "
            + "ON public.one_time_token USING btree (user_id, purpose) "
            + "WHERE ((membership_id IS NULL) AND (platform_admin_invitation_id IS NULL) AND (used_at IS NULL) "
            + "AND (revoked_at IS NULL))";
    private static final String INVITATION_ACTIVE_INDEX = "CREATE UNIQUE INDEX "
            + "one_time_token_platform_admin_invitation_one_active_uq ON public.one_time_token USING btree "
            + "(platform_admin_invitation_id) WHERE ((platform_admin_invitation_id IS NOT NULL) "
            + "AND (used_at IS NULL) AND (revoked_at IS NULL))";
    private static final String INVITATION_CREATED_AT_INDEX = "CREATE INDEX "
            + "one_time_token_platform_admin_invitation_created_at_idx ON public.one_time_token USING btree "
            + "(platform_admin_invitation_id, created_at)";
    private static final String PENDING_INDEX = "CREATE UNIQUE INDEX platform_admin_invitation_user_id_pending_uq "
            + "ON public.platform_admin_invitation USING btree (user_id) WHERE (accepted_at IS NULL)";

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withUsername("luz")
            .withPassword("blank");

    @BeforeAll
    static void startContainer() {
        POSTGRES.start();
    }

    @Test
    @DisplayName("OTT-011 existing tokens keep no invitation, and the constraints and indexes have their exact conditions")
    void existingTokensKeepNoInvitation_andTheSchemaHasItsExactConditions() throws Exception {
        try (Connection connection = freshDatabase(POSTGRES, "applied")) {
            applyUpToChangeSet(connection, BOUNDARY_CHANGESET_ID);
            UUID user = insertUser(connection, "12345678Z", 1);
            UUID membership = insertMembership(connection, user, insertCommunity(connection, "A"));
            UUID reset = insertToken(connection, user, null, null, "PASSWORD_RESET", "a".repeat(64));
            UUID invitation = insertToken(connection, user, membership, null, "INVITATION", "b".repeat(64));

            applyRest(connection);

            assertNull(invitationOf(connection, reset));
            assertNull(invitationOf(connection, invitation));
            assertEquals("CASCADE", deleteRule(connection, "platform_admin_invitation_user_id_fk"));
            assertEquals("SET NULL", deleteRule(connection, "platform_admin_invitation_invited_by_fk"));
            assertEquals("CASCADE", deleteRule(connection, "one_time_token_platform_admin_invitation_id_fk"));
            assertEquals("CHECK ((num_nonnulls(membership_id, platform_admin_invitation_id) <= 1))",
                    checkDefinition(connection, "one_time_token_single_binding_check"));
            assertEquals("CHECK ((failed_code_attempts >= 0))",
                    checkDefinition(connection, "platform_admin_invitation_failed_code_attempts_check"));
            Map<String, String> tokenIndexes = indexDefinitions(connection, "one_time_token");
            assertEquals(NARROWED_ACTIVE_INDEX, tokenIndexes.get("one_time_token_one_active_uq"));
            assertEquals(INVITATION_ACTIVE_INDEX,
                    tokenIndexes.get("one_time_token_platform_admin_invitation_one_active_uq"));
            assertEquals(INVITATION_CREATED_AT_INDEX,
                    tokenIndexes.get("one_time_token_platform_admin_invitation_created_at_idx"));
            assertEquals(PENDING_INDEX, indexDefinitions(connection, "platform_admin_invitation")
                    .get("platform_admin_invitation_user_id_pending_uq"));
        }
    }

    @Test
    @DisplayName("OTT-011 a token cannot be bound to both a membership and a platform-admin invitation")
    void aTokenBoundToBothAMembershipAndAnInvitation_isRefused() throws Exception {
        try (Connection connection = freshDatabase(POSTGRES, "single_binding")) {
            applyUpToChangeSet(connection, BOUNDARY_CHANGESET_ID);
            applyRest(connection);
            UUID user = insertUser(connection, "12345678Z", 1);
            UUID membership = insertMembership(connection, user, insertCommunity(connection, "A"));
            UUID invitation = insertInvitation(connection, user, null);

            SQLException refused = assertThrows(SQLException.class, () -> insertToken(connection, user, membership,
                    invitation, "PLATFORM_ADMIN_INVITATION", "a".repeat(64)));

            assertTrue(refused.getMessage().contains("one_time_token_single_binding_check"));
        }
    }

    @Test
    @DisplayName("OTT-011 one active token per invitation, alongside a password-reset and a community invitation token")
    void oneActiveTokenPerInvitation_alongsideTheOtherScopes() throws Exception {
        try (Connection connection = freshDatabase(POSTGRES, "per_invitation")) {
            applyUpToChangeSet(connection, BOUNDARY_CHANGESET_ID);
            applyRest(connection);
            UUID user = insertUser(connection, "12345678Z", 1);
            UUID membership = insertMembership(connection, user, insertCommunity(connection, "A"));
            UUID invitation = insertInvitation(connection, user, null);

            insertToken(connection, user, null, invitation, "PLATFORM_ADMIN_INVITATION", "a".repeat(64));
            insertToken(connection, user, null, null, "PASSWORD_RESET", "b".repeat(64));
            insertToken(connection, user, membership, null, "INVITATION", "c".repeat(64));

            assertThrows(SQLException.class, () -> insertToken(connection, user, null, invitation,
                    "PLATFORM_ADMIN_INVITATION", "d".repeat(64)));
            assertThrows(SQLException.class, () -> insertToken(connection, user, null, null,
                    "PASSWORD_RESET", "e".repeat(64)));
        }
    }

    @Test
    void onePendingInvitationPerAccount_besidesAcceptedOnes() throws Exception {
        try (Connection connection = freshDatabase(POSTGRES, "pending")) {
            applyUpToChangeSet(connection, BOUNDARY_CHANGESET_ID);
            applyRest(connection);
            UUID user = insertUser(connection, "12345678Z", 1);
            UUID accepted = insertInvitation(connection, user, null);
            seed(connection, "UPDATE platform_admin_invitation SET accepted_at = now() WHERE id = '" + accepted + "'");

            insertInvitation(connection, user, null);

            assertThrows(SQLException.class, () -> insertInvitation(connection, user, null));
        }
    }

    @Test
    void aNegativeCountOfFailedCodeAttempts_isRefused() throws Exception {
        try (Connection connection = freshDatabase(POSTGRES, "negative_count")) {
            applyUpToChangeSet(connection, BOUNDARY_CHANGESET_ID);
            applyRest(connection);
            UUID invitation = insertInvitation(connection, insertUser(connection, "12345678Z", 1), null);

            assertThrows(SQLException.class, () -> seed(connection,
                    "UPDATE platform_admin_invitation SET failed_code_attempts = -1 WHERE id = '" + invitation + "'"));
        }
    }

    @Test
    @DisplayName("OTT-005 deleting an invitation deletes its tokens, deleting the account deletes the invitation, and "
            + "deleting the inviter keeps it")
    void deletingAnInvitationDeletesItsTokens_deletingTheAccountDeletesTheInvitation_andDeletingTheInviterKeepsIt()
            throws Exception {
        try (Connection connection = freshDatabase(POSTGRES, "cascade")) {
            applyUpToChangeSet(connection, BOUNDARY_CHANGESET_ID);
            applyRest(connection);
            UUID inviter = insertUser(connection, "00000000T", 1);
            UUID first = insertUser(connection, "12345678Z", 2);
            UUID second = insertUser(connection, "87654321X", 3);
            UUID deletedInvitation = insertInvitation(connection, first, inviter);
            UUID keptInvitation = insertInvitation(connection, second, inviter);
            UUID deletedToken = insertToken(connection, first, null, deletedInvitation, "PLATFORM_ADMIN_INVITATION",
                    "a".repeat(64));
            UUID keptToken = insertToken(connection, second, null, keptInvitation, "PLATFORM_ADMIN_INVITATION",
                    "b".repeat(64));

            seed(connection, "DELETE FROM platform_admin_invitation WHERE id = '" + deletedInvitation + "'");
            assertFalse(exists(connection, "one_time_token", deletedToken));
            assertTrue(exists(connection, "one_time_token", keptToken));

            seed(connection, "DELETE FROM users WHERE id = '" + inviter + "'");
            assertTrue(exists(connection, "platform_admin_invitation", keptInvitation));
            assertEquals("0", scalar(connection, "SELECT count(invited_by) FROM platform_admin_invitation"));

            seed(connection, "DELETE FROM users WHERE id = '" + second + "'");
            assertFalse(exists(connection, "platform_admin_invitation", keptInvitation));
            assertFalse(exists(connection, "one_time_token", keptToken));
        }
    }

    @Test
    void halts_whenTheTableAlreadyExists_andAppliesNothing() throws Exception {
        try (Connection connection = freshDatabase(POSTGRES, "halts_table")) {
            applyUpToChangeSet(connection, BOUNDARY_CHANGESET_ID);
            seed(connection, "CREATE TABLE platform_admin_invitation (id UUID PRIMARY KEY)");
            commit(connection);

            Exception failure = assertThrows(Exception.class, () -> applyRest(connection));

            rollback(connection);
            assertTrue(messageChainContains(failure, "Cannot create platform_admin_invitation"));
            assertFalse(changesetApplied(connection, TABLE_CHANGESET_ID));
            assertFalse(changesetApplied(connection, COLUMN_CHANGESET_ID));
            assertFalse(changesetApplied(connection, INDEX_CHANGESET_ID));
        }
    }

    @Test
    void halts_whenTheColumnAlreadyExists_andAppliesNothing() throws Exception {
        try (Connection connection = freshDatabase(POSTGRES, "halts_column")) {
            applyUpToChangeSet(connection, BOUNDARY_CHANGESET_ID);
            continueUpToChangeSet(connection, TABLE_CHANGESET_ID);
            seed(connection, "ALTER TABLE one_time_token ADD COLUMN platform_admin_invitation_id UUID");
            commit(connection);

            Exception failure = assertThrows(Exception.class, () -> applyRest(connection));

            rollback(connection);
            assertTrue(messageChainContains(failure, "Cannot add platform_admin_invitation_id to one_time_token"));
            assertFalse(changesetApplied(connection, COLUMN_CHANGESET_ID));
            assertFalse(changesetApplied(connection, INDEX_CHANGESET_ID));
            assertEquals(MEMBERSHIP_NARROWED_ACTIVE_INDEX,
                    indexDefinitions(connection, "one_time_token").get("one_time_token_one_active_uq"));
        }
    }

    @Test
    void halts_whenTheActiveIndexIsNotTheOneBoundToMemberships_andAppliesNothing() throws Exception {
        try (Connection connection = freshDatabase(POSTGRES, "halts_index")) {
            applyUpToChangeSet(connection, BOUNDARY_CHANGESET_ID);
            continueUpToChangeSet(connection, COLUMN_CHANGESET_ID);
            String altered = "CREATE UNIQUE INDEX one_time_token_one_active_uq ON public.one_time_token USING btree "
                    + "(user_id, purpose) WHERE (used_at IS NULL)";
            seed(connection, "DROP INDEX one_time_token_one_active_uq");
            seed(connection, altered);
            commit(connection);

            Exception failure = assertThrows(Exception.class, () -> applyRest(connection));

            rollback(connection);
            assertTrue(messageChainContains(failure, "Cannot bind the active-token index of one_time_token"));
            assertFalse(changesetApplied(connection, INDEX_CHANGESET_ID));
            Map<String, String> indexes = indexDefinitions(connection, "one_time_token");
            assertEquals(altered, indexes.get("one_time_token_one_active_uq"));
            assertFalse(indexes.containsKey("one_time_token_platform_admin_invitation_one_active_uq"));
        }
    }

    @Test
    void halts_whenTheInvitationIndexAlreadyExists_andAppliesNothing() throws Exception {
        try (Connection connection = freshDatabase(POSTGRES, "halts_invitation_index")) {
            applyUpToChangeSet(connection, BOUNDARY_CHANGESET_ID);
            continueUpToChangeSet(connection, COLUMN_CHANGESET_ID);
            seed(connection, "CREATE INDEX one_time_token_platform_admin_invitation_one_active_uq "
                    + "ON one_time_token (platform_admin_invitation_id)");
            commit(connection);

            Exception failure = assertThrows(Exception.class, () -> applyRest(connection));

            rollback(connection);
            assertTrue(messageChainContains(failure, "Cannot bind the active-token index of one_time_token"));
            assertFalse(changesetApplied(connection, INDEX_CHANGESET_ID));
            assertEquals(MEMBERSHIP_NARROWED_ACTIVE_INDEX,
                    indexDefinitions(connection, "one_time_token").get("one_time_token_one_active_uq"));
        }
    }

    @Test
    void rollingBack_deletesTheInvitationTokensAndTable_restoresTheIndex_andCanBeAppliedAgain() throws Exception {
        try (Connection connection = freshDatabase(POSTGRES, "rollback")) {
            applyUpToChangeSet(connection, BOUNDARY_CHANGESET_ID);
            applyRest(connection);
            UUID user = insertUser(connection, "12345678Z", 1);
            UUID membership = insertMembership(connection, user, insertCommunity(connection, "A"));
            UUID invitation = insertInvitation(connection, user, null);
            UUID invitationToken = insertToken(connection, user, null, invitation, "PLATFORM_ADMIN_INVITATION",
                    "a".repeat(64));
            UUID membershipToken = insertToken(connection, user, membership, null, "INVITATION", "b".repeat(64));
            UUID reset = insertToken(connection, user, null, null, "PASSWORD_RESET", "c".repeat(64));
            commit(connection);

            rollbackToChangeSet(connection, BOUNDARY_CHANGESET_ID);

            assertFalse(exists(connection, "one_time_token", invitationToken));
            assertTrue(exists(connection, "one_time_token", membershipToken));
            assertTrue(exists(connection, "one_time_token", reset));
            assertEquals("0", scalar(connection, "SELECT count(*) FROM information_schema.tables "
                    + "WHERE table_name = 'platform_admin_invitation'"));
            assertEquals("0", scalar(connection, "SELECT count(*) FROM information_schema.columns "
                    + "WHERE table_name = 'one_time_token' AND column_name = 'platform_admin_invitation_id'"));
            assertEquals("0", scalar(connection, "SELECT count(*) FROM pg_constraint "
                    + "WHERE conname = 'one_time_token_single_binding_check'"));
            Map<String, String> indexes = indexDefinitions(connection, "one_time_token");
            assertEquals(MEMBERSHIP_NARROWED_ACTIVE_INDEX, indexes.get("one_time_token_one_active_uq"));
            assertFalse(indexes.containsKey("one_time_token_platform_admin_invitation_one_active_uq"));
            assertFalse(indexes.containsKey("one_time_token_platform_admin_invitation_created_at_idx"));

            applyRest(connection);

            assertTrue(changesetApplied(connection, INDEX_CHANGESET_ID));
            indexes = indexDefinitions(connection, "one_time_token");
            assertEquals(NARROWED_ACTIVE_INDEX, indexes.get("one_time_token_one_active_uq"));
            assertEquals(INVITATION_ACTIVE_INDEX, indexes.get("one_time_token_platform_admin_invitation_one_active_uq"));
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

    private static UUID insertInvitation(Connection connection, UUID userId, UUID invitedBy) throws SQLException {
        UUID id = UUID.randomUUID();
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO platform_admin_invitation (id, user_id, email, full_name, invited_by, invited_at, "
                        + "confirmation_code_hash) VALUES (?, ?, 'invited@example.org', 'Invited', ?, now(), ?)")) {
            statement.setObject(1, id);
            statement.setObject(2, userId);
            statement.setObject(3, invitedBy);
            statement.setString(4, "f".repeat(64));
            statement.executeUpdate();
        }
        return id;
    }

    private static UUID insertToken(Connection connection, UUID userId, UUID membershipId, UUID invitationId,
                                    String purpose, String hash) throws SQLException {
        UUID id = UUID.randomUUID();
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO one_time_token (id, user_id, purpose, token_hash, created_at, expires_at, membership_id"
                        + (invitationId == null ? "" : ", platform_admin_invitation_id")
                        + ") VALUES (?, ?, ?, ?, now(), now() + interval '7 days', ?"
                        + (invitationId == null ? "" : ", ?") + ")")) {
            statement.setObject(1, id);
            statement.setObject(2, userId);
            statement.setString(3, purpose);
            statement.setString(4, hash);
            statement.setObject(5, membershipId);
            if (invitationId != null) {
                statement.setObject(6, invitationId);
            }
            statement.executeUpdate();
        }
        return id;
    }

    private static UUID invitationOf(Connection connection, UUID tokenId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT platform_admin_invitation_id FROM one_time_token WHERE id = ?")) {
            statement.setObject(1, tokenId);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next());
                return resultSet.getObject(1, UUID.class);
            }
        }
    }

    private static boolean exists(Connection connection, String table, UUID id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT count(*) FROM " + table + " WHERE id = ?")) {
            statement.setObject(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1) > 0;
            }
        }
    }

    private static String deleteRule(Connection connection, String constraint) throws SQLException {
        return scalar(connection, "SELECT delete_rule FROM information_schema.referential_constraints "
                + "WHERE constraint_name = '" + constraint + "'");
    }

    private static String checkDefinition(Connection connection, String constraint) throws SQLException {
        return scalar(connection, "SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname = '"
                + constraint + "'");
    }

    private static Map<String, String> indexDefinitions(Connection connection, String table) throws SQLException {
        Map<String, String> definitions = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT indexname, indexdef FROM pg_indexes WHERE tablename = ?")) {
            statement.setString(1, table);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    definitions.put(resultSet.getString(1), resultSet.getString(2));
                }
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

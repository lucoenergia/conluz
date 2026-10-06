package org.lucoenergia.conluz.infrastructure.shared.db.liquibase;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lucoenergia.conluz.infrastructure.shared.db.liquibase.LiquibaseTestSupport.applyRest;
import static org.lucoenergia.conluz.infrastructure.shared.db.liquibase.LiquibaseTestSupport.applyUpToChangeSet;
import static org.lucoenergia.conluz.infrastructure.shared.db.liquibase.LiquibaseTestSupport.freshDatabase;
import static org.lucoenergia.conluz.infrastructure.shared.db.liquibase.LiquibaseTestSupport.rollbackToChangeSet;
import static org.lucoenergia.conluz.infrastructure.shared.db.liquibase.LiquibaseTestSupport.seed;

/**
 * The changeset that creates the one_time_token table (#361).
 */
class OneTimeTokenTableMigrationIntegrationTest {

    /** The last changeset before the one under test. */
    private static final String BOUNDARY_CHANGESET_ID = "backfill_must_change_password_for_existing_users";

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withUsername("luz")
            .withPassword("blank");

    @BeforeAll
    static void startContainer() {
        POSTGRES.start();
    }

    @Test
    void createsTheTable_withItsConstraintsAndIndexes() throws Exception {
        try (Connection connection = freshDatabase(POSTGRES, "created")) {
            applyUpToChangeSet(connection, BOUNDARY_CHANGESET_ID);
            applyRest(connection);

            assertEquals(Set.of("id", "user_id", "purpose", "token_hash", "created_at", "expires_at", "used_at",
                    "revoked_at"), columns(connection));
            assertEquals(Set.of("used_at", "revoked_at"), nullableColumns(connection));
            assertEquals(Set.of("one_time_token_id_pk", "one_time_token_token_hash_uq",
                            "one_time_token_user_purpose_created_at_idx", "one_time_token_one_active_uq",
                            "one_time_token_finished_at_idx"),
                    indexes(connection));
        }
    }

    @Test
    void deletingAUser_deletesTheirTokens() throws Exception {
        try (Connection connection = freshDatabase(POSTGRES, "cascade")) {
            applyUpToChangeSet(connection, BOUNDARY_CHANGESET_ID);
            applyRest(connection);
            UUID user = insertUser(connection, "12345678Z", 1);
            UUID otherUser = insertUser(connection, "87654321X", 2);
            insertToken(connection, user, "a".repeat(64));
            insertToken(connection, otherUser, "b".repeat(64));

            seed(connection, "DELETE FROM users WHERE id = '" + user + "'");

            assertEquals(0, tokenCount(connection, user));
            assertEquals(1, tokenCount(connection, otherUser));
        }
    }

    @Test
    void allowsOneActiveTokenPerUserAndPurpose_andAnyNumberOfFinishedOnes() throws Exception {
        try (Connection connection = freshDatabase(POSTGRES, "one_active")) {
            applyUpToChangeSet(connection, BOUNDARY_CHANGESET_ID);
            applyRest(connection);
            UUID user = insertUser(connection, "12345678Z", 1);
            insertToken(connection, user, "a".repeat(64));
            seed(connection, "UPDATE one_time_token SET revoked_at = now() WHERE user_id = '" + user + "'");
            insertToken(connection, user, "b".repeat(64));

            assertThrows(SQLException.class, () -> insertToken(connection, user, "c".repeat(64)));
        }
    }

    @Test
    void halts_whenTheTableAlreadyExists() throws Exception {
        try (Connection connection = freshDatabase(POSTGRES, "halts")) {
            applyUpToChangeSet(connection, BOUNDARY_CHANGESET_ID);
            seed(connection, "CREATE TABLE one_time_token (id UUID PRIMARY KEY)");
            if (!connection.getAutoCommit()) {
                connection.commit();
            }

            assertThrows(Exception.class, () -> applyRest(connection));

            if (!connection.getAutoCommit()) {
                connection.rollback();
            }
            assertEquals(Set.of("id"), columns(connection));
        }
    }

    @Test
    void rollingBackDropsTheTable_andItCanBeAppliedAgain() throws Exception {
        try (Connection connection = freshDatabase(POSTGRES, "rollback")) {
            applyUpToChangeSet(connection, BOUNDARY_CHANGESET_ID);
            applyRest(connection);

            rollbackToChangeSet(connection, BOUNDARY_CHANGESET_ID);

            assertTrue(columns(connection).isEmpty());
            assertFalse(indexes(connection).contains("one_time_token_finished_at_idx"));

            applyRest(connection);

            assertFalse(columns(connection).isEmpty());
        }
    }

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

    private static void insertToken(Connection connection, UUID userId, String hash) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO one_time_token (id, user_id, purpose, token_hash, created_at, expires_at) "
                        + "VALUES (?, ?, 'PASSWORD_RESET', ?, now(), now() + interval '1 day')")) {
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, userId);
            statement.setString(3, hash);
            statement.executeUpdate();
        }
    }

    private static int tokenCount(Connection connection, UUID userId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT count(*) FROM one_time_token WHERE user_id = ?")) {
            statement.setObject(1, userId);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        }
    }

    private static Set<String> columns(Connection connection) throws SQLException {
        return strings(connection, "SELECT column_name FROM information_schema.columns "
                + "WHERE table_name = 'one_time_token'");
    }

    private static Set<String> nullableColumns(Connection connection) throws SQLException {
        return strings(connection, "SELECT column_name FROM information_schema.columns "
                + "WHERE table_name = 'one_time_token' AND is_nullable = 'YES'");
    }

    private static Set<String> indexes(Connection connection) throws SQLException {
        return strings(connection, "SELECT indexname FROM pg_indexes WHERE tablename = 'one_time_token'");
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
}

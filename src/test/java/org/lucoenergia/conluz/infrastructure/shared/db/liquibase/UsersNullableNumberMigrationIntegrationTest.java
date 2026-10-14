package org.lucoenergia.conluz.infrastructure.shared.db.liquibase;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lucoenergia.conluz.infrastructure.shared.db.liquibase.LiquibaseTestSupport.applyRest;
import static org.lucoenergia.conluz.infrastructure.shared.db.liquibase.LiquibaseTestSupport.applyUpToChangeSet;
import static org.lucoenergia.conluz.infrastructure.shared.db.liquibase.LiquibaseTestSupport.freshDatabase;
import static org.lucoenergia.conluz.infrastructure.shared.db.liquibase.LiquibaseTestSupport.rollbackToChangeSet;

/**
 * The changeset that makes users.number nullable (#392).
 */
class UsersNullableNumberMigrationIntegrationTest {

    /** The last changeset before the one under test. */
    private static final String BOUNDARY_CHANGESET_ID = "bind_one_time_token_active_index_to_membership";
    private static final String CHANGESET_ID = "make_users_number_nullable";

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withUsername("luz")
            .withPassword("blank");

    @BeforeAll
    static void startContainer() {
        POSTGRES.start();
    }

    @Test
    void theColumnBecomesNullable_andExistingNumbersAreUnchanged() throws Exception {
        try (Connection connection = freshDatabase(POSTGRES, "nullable")) {
            applyUpToChangeSet(connection, BOUNDARY_CHANGESET_ID);
            assertFalse(numberIsNullable(connection));
            UUID existing = insertUser(connection, "11111111A", 7);

            applyRest(connection);

            assertTrue(numberIsNullable(connection));
            assertEquals(7, number(connection, existing));
            UUID numberless = insertUser(connection, "22222222B", null);
            assertNull(number(connection, numberless));
        }
    }

    @Test
    void rollingBack_restoresNotNull_whenEveryAccountHasANumber_andItCanBeAppliedAgain() throws Exception {
        try (Connection connection = freshDatabase(POSTGRES, "rollback")) {
            applyUpToChangeSet(connection, BOUNDARY_CHANGESET_ID);
            UUID existing = insertUser(connection, "11111111A", 7);
            applyRest(connection);
            commit(connection);

            rollbackToChangeSet(connection, BOUNDARY_CHANGESET_ID);

            assertFalse(changesetApplied(connection));
            assertFalse(numberIsNullable(connection));
            assertEquals(7, number(connection, existing));

            applyRest(connection);

            assertTrue(numberIsNullable(connection));
            assertEquals(7, number(connection, existing));
        }
    }

    @Test
    void rollingBackHalts_listingTheAccountsWithoutANumber_andRollsNothingBack() throws Exception {
        try (Connection connection = freshDatabase(POSTGRES, "halts")) {
            applyUpToChangeSet(connection, BOUNDARY_CHANGESET_ID);
            applyRest(connection);
            UUID numberless = insertUser(connection, "11111111A", null);
            insertUser(connection, "22222222B", 2);
            commit(connection);

            Exception failure = assertThrows(Exception.class,
                    () -> rollbackToChangeSet(connection, BOUNDARY_CHANGESET_ID));

            if (!connection.getAutoCommit()) {
                connection.rollback();
            }
            assertTrue(messageChainContains(failure, "some accounts have no number"),
                    "The failure should carry the rollback's message");
            assertTrue(messageChainContains(failure, numberless.toString()),
                    "The failure should list the accounts without a number");
            assertTrue(changesetApplied(connection));
            assertTrue(numberIsNullable(connection));
            assertNull(number(connection, numberless));
        }
    }

    // --- Helpers ---

    private static UUID insertUser(Connection connection, String personalId, Integer number) throws SQLException {
        UUID id = UUID.randomUUID();
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO users (id, personal_id, number, password, full_name, enabled) "
                        + "VALUES (?, ?, ?, 'hash', 'Member', true)")) {
            statement.setObject(1, id);
            statement.setString(2, personalId);
            if (number == null) {
                statement.setNull(3, Types.INTEGER);
            } else {
                statement.setInt(3, number);
            }
            statement.executeUpdate();
        }
        return id;
    }

    private static Integer number(Connection connection, UUID user) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT number FROM users WHERE id = ?")) {
            statement.setObject(1, user);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next(), "The user should still exist");
                int value = resultSet.getInt(1);
                return resultSet.wasNull() ? null : value;
            }
        }
    }

    private static boolean numberIsNullable(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT is_nullable FROM information_schema.columns "
                        + "WHERE table_name = 'users' AND column_name = 'number'");
             ResultSet resultSet = statement.executeQuery()) {
            assertTrue(resultSet.next(), "users.number should exist");
            return "YES".equals(resultSet.getString(1));
        }
    }

    private static boolean changesetApplied(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT count(*) FROM databasechangelog WHERE id = ?")) {
            statement.setString(1, CHANGESET_ID);
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

    private static boolean messageChainContains(Throwable throwable, String text) {
        for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
            if (cause.getMessage() != null && cause.getMessage().contains(text)) {
                return true;
            }
        }
        return false;
    }
}

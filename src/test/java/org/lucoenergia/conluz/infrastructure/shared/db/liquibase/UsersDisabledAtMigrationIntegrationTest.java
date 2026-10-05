package org.lucoenergia.conluz.infrastructure.shared.db.liquibase;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lucoenergia.conluz.infrastructure.shared.db.liquibase.LiquibaseTestSupport.applyRest;
import static org.lucoenergia.conluz.infrastructure.shared.db.liquibase.LiquibaseTestSupport.applyUpToChangeSet;
import static org.lucoenergia.conluz.infrastructure.shared.db.liquibase.LiquibaseTestSupport.freshDatabase;
import static org.lucoenergia.conluz.infrastructure.shared.db.liquibase.LiquibaseTestSupport.rollbackToChangeSet;

/**
 * The changeset that adds users.disabled_at (#346), run against users that existed before it.
 */
class UsersDisabledAtMigrationIntegrationTest {

    /** The last changeset before the one under test. */
    private static final String BOUNDARY_CHANGESET_ID = "add_password_changed_at_to_users";

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withUsername("luz")
            .withPassword("blank");

    @BeforeAll
    static void startContainer() {
        POSTGRES.start();
    }

    @Test
    void existingUsers_haveNoDisableRecorded_enabledOrNot() throws Exception {
        try (Connection connection = freshDatabase(POSTGRES, "existing_users")) {
            applyUpToChangeSet(connection, BOUNDARY_CHANGESET_ID);
            UUID enabled = insertUser(connection, "12345678Z", 1, true);
            UUID disabled = insertUser(connection, "87654321X", 2, false);

            applyRest(connection);

            assertNull(disabledAtOf(connection, enabled));
            assertNull(disabledAtOf(connection, disabled));
            assertEquals("YES", isNullable(connection));
        }
    }

    @Test
    void rollingBackDropsTheColumn_andItCanBeAppliedAgain() throws Exception {
        try (Connection connection = freshDatabase(POSTGRES, "rollback")) {
            applyUpToChangeSet(connection, BOUNDARY_CHANGESET_ID);
            insertUser(connection, "12345678Z", 1, true);
            applyRest(connection);

            rollbackToChangeSet(connection, BOUNDARY_CHANGESET_ID);

            assertFalse(columnExists(connection));

            applyRest(connection);

            assertTrue(columnExists(connection));
        }
    }

    private static UUID insertUser(Connection connection, String personalId, int number, boolean enabled)
            throws SQLException {
        UUID id = UUID.randomUUID();
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO users (id, personal_id, number, password, full_name, email, enabled) "
                        + "VALUES (?, ?, ?, 'hash', 'Seeded user', ?, ?)")) {
            statement.setObject(1, id);
            statement.setString(2, personalId);
            statement.setInt(3, number);
            statement.setString(4, "seeded" + number + "@example.org");
            statement.setBoolean(5, enabled);
            statement.executeUpdate();
        }
        return id;
    }

    private static Object disabledAtOf(Connection connection, UUID id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT disabled_at FROM users WHERE id = ?")) {
            statement.setObject(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next());
                return resultSet.getObject("disabled_at");
            }
        }
    }

    private static boolean columnExists(Connection connection) throws SQLException {
        return isNullable(connection) != null;
    }

    private static String isNullable(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT is_nullable FROM information_schema.columns "
                        + "WHERE table_name = 'users' AND column_name = 'disabled_at'")) {
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getString(1) : null;
            }
        }
    }
}

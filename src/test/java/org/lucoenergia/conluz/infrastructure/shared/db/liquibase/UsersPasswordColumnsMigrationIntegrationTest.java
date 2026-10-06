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
import static org.lucoenergia.conluz.infrastructure.shared.db.liquibase.LiquibaseTestSupport.continueUpToChangeSet;
import static org.lucoenergia.conluz.infrastructure.shared.db.liquibase.LiquibaseTestSupport.freshDatabase;
import static org.lucoenergia.conluz.infrastructure.shared.db.liquibase.LiquibaseTestSupport.rollbackToChangeSet;

/**
 * The changesets that add users.must_change_password and users.password_changed_at (#330), run against a user
 * that existed before them.
 */
class UsersPasswordColumnsMigrationIntegrationTest {

    /** The last changeset before the two under test. */
    private static final String BOUNDARY_CHANGESET_ID = "add_unique_constraint_to_users_personal_id";
    /** The last of the two under test. Later changesets flag every existing user (#342). */
    private static final String LAST_CHANGESET_UNDER_TEST_ID = "add_password_changed_at_to_users";

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withUsername("luz")
            .withPassword("blank");

    @BeforeAll
    static void startContainer() {
        POSTGRES.start();
    }

    @Test
    void existingUsersAreNotFlaggedAndHaveNoPasswordChangeRecorded() throws Exception {
        try (Connection connection = freshDatabase(POSTGRES, "existing_users")) {
            applyUpToChangeSet(connection, BOUNDARY_CHANGESET_ID);
            UUID existing = insertUser(connection);

            continueUpToChangeSet(connection, LAST_CHANGESET_UNDER_TEST_ID);

            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT must_change_password, password_changed_at FROM users WHERE id = ?")) {
                statement.setObject(1, existing);
                try (ResultSet resultSet = statement.executeQuery()) {
                    assertTrue(resultSet.next());
                    assertFalse(resultSet.getBoolean("must_change_password"));
                    assertFalse(resultSet.wasNull());
                    assertNull(resultSet.getObject("password_changed_at"));
                }
            }
            assertEquals("NO", isNullable(connection, "must_change_password"));
            assertEquals("YES", isNullable(connection, "password_changed_at"));
        }
    }

    @Test
    void rollingBackBothChangesetsDropsTheColumns_andTheyCanBeAppliedAgain() throws Exception {
        try (Connection connection = freshDatabase(POSTGRES, "rollback")) {
            applyUpToChangeSet(connection, BOUNDARY_CHANGESET_ID);
            insertUser(connection);
            applyRest(connection);

            rollbackToChangeSet(connection, BOUNDARY_CHANGESET_ID);

            assertFalse(columnExists(connection, "must_change_password"));
            assertFalse(columnExists(connection, "password_changed_at"));

            applyRest(connection);

            assertTrue(columnExists(connection, "must_change_password"));
            assertTrue(columnExists(connection, "password_changed_at"));
        }
    }

    private static UUID insertUser(Connection connection) throws SQLException {
        UUID id = UUID.randomUUID();
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO users (id, personal_id, number, password, full_name, email, enabled) "
                        + "VALUES (?, '12345678Z', 1, 'hash', 'Seeded user', 'seeded@example.org', true)")) {
            statement.setObject(1, id);
            statement.executeUpdate();
        }
        return id;
    }

    private static boolean columnExists(Connection connection, String column) throws SQLException {
        return isNullable(connection, column) != null;
    }

    private static String isNullable(Connection connection, String column) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT is_nullable FROM information_schema.columns WHERE table_name = 'users' AND column_name = ?")) {
            statement.setString(1, column);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getString(1) : null;
            }
        }
    }
}

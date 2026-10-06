package org.lucoenergia.conluz.infrastructure.shared.db.liquibase;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lucoenergia.conluz.infrastructure.shared.db.liquibase.LiquibaseTestSupport.applyRest;
import static org.lucoenergia.conluz.infrastructure.shared.db.liquibase.LiquibaseTestSupport.applyUpToChangeSet;
import static org.lucoenergia.conluz.infrastructure.shared.db.liquibase.LiquibaseTestSupport.freshDatabase;
import static org.lucoenergia.conluz.infrastructure.shared.db.liquibase.LiquibaseTestSupport.rollbackToChangeSet;

/**
 * The changeset that flags every existing user as having to change their password (#342), run against users that
 * existed before it.
 */
class UsersMustChangePasswordBackfillMigrationIntegrationTest {

    /** The last changeset before the one under test. */
    private static final String BOUNDARY_CHANGESET_ID = "add_disabled_at_to_users";

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withUsername("luz")
            .withPassword("blank");

    @BeforeAll
    static void startContainer() {
        POSTGRES.start();
    }

    @Test
    void everyExistingUser_isFlagged_platformAdminsAndDisabledUsersIncluded() throws Exception {
        try (Connection connection = freshDatabase(POSTGRES, "existing_users")) {
            applyUpToChangeSet(connection, BOUNDARY_CHANGESET_ID);
            List<UUID> existing = insertUsers(connection);

            applyRest(connection);

            for (UUID id : existing) {
                assertTrue(mustChangePasswordOf(connection, id));
            }
        }
    }

    @Test
    void rollingBackClearsTheFlagOnEveryUser_andItCanBeAppliedAgain() throws Exception {
        try (Connection connection = freshDatabase(POSTGRES, "rollback")) {
            applyUpToChangeSet(connection, BOUNDARY_CHANGESET_ID);
            List<UUID> existing = insertUsers(connection);
            applyRest(connection);

            rollbackToChangeSet(connection, BOUNDARY_CHANGESET_ID);

            // Including the user that was already flagged before the backfill: the rollback cannot tell them apart
            for (UUID id : existing) {
                assertFalse(mustChangePasswordOf(connection, id));
            }

            applyRest(connection);

            for (UUID id : existing) {
                assertTrue(mustChangePasswordOf(connection, id));
            }
        }
    }

    /**
     * An enabled user, a disabled user, a platform admin and a user already flagged at creation.
     */
    private static List<UUID> insertUsers(Connection connection) throws SQLException {
        return List.of(
                insertUser(connection, "12345678Z", 1, true, false, false),
                insertUser(connection, "87654321X", 2, false, false, false),
                insertUser(connection, "11111111H", 3, true, true, false),
                insertUser(connection, "22222222J", 4, true, false, true));
    }

    private static UUID insertUser(Connection connection, String personalId, int number, boolean enabled,
                                   boolean platformAdmin, boolean mustChangePassword) throws SQLException {
        UUID id = UUID.randomUUID();
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO users (id, personal_id, number, password, full_name, email, enabled, "
                        + "is_platform_admin, must_change_password) "
                        + "VALUES (?, ?, ?, 'hash', 'Seeded user', ?, ?, ?, ?)")) {
            statement.setObject(1, id);
            statement.setString(2, personalId);
            statement.setInt(3, number);
            statement.setString(4, "seeded" + number + "@example.org");
            statement.setBoolean(5, enabled);
            statement.setBoolean(6, platformAdmin);
            statement.setBoolean(7, mustChangePassword);
            statement.executeUpdate();
        }
        return id;
    }

    private static boolean mustChangePasswordOf(Connection connection, UUID id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT must_change_password FROM users WHERE id = ?")) {
            statement.setObject(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next());
                return resultSet.getBoolean(1);
            }
        }
    }
}

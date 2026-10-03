package org.lucoenergia.conluz.infrastructure.shared.db.liquibase;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.shared.UserPersonalId;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lucoenergia.conluz.infrastructure.shared.db.liquibase.LiquibaseTestSupport.applyRest;
import static org.lucoenergia.conluz.infrastructure.shared.db.liquibase.LiquibaseTestSupport.applyUpToChangeSet;
import static org.lucoenergia.conluz.infrastructure.shared.db.liquibase.LiquibaseTestSupport.freshDatabase;

/**
 * The changesets that normalise users.personal_id and make it unique (#331), run against rows seeded
 * as they existed before them.
 */
class UsersPersonalIdMigrationIntegrationTest {

    /** The last changeset before the two under test. */
    private static final String BOUNDARY_CHANGESET_ID = "add_investment_to_community_memberships";
    private static final String NORMALIZE_CHANGESET_ID = "normalize_users_personal_id";
    private static final String UNIQUE_CHANGESET_ID = "add_unique_constraint_to_users_personal_id";
    private static final String CONSTRAINT_NAME = "users_personal_id_uq";

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withUsername("luz")
            .withPassword("blank");

    @BeforeAll
    static void startContainer() {
        POSTGRES.start();
    }

    /**
     * The SQL in the changeset and {@link UserPersonalId#normalize} must remove exactly the same
     * characters, or an existing user would be stored in a form no lookup can reach. Every example
     * normalises to a distinct value, so the collision precondition lets them all through.
     */
    @Test
    void migrationNormalisesExistingRowsExactlyLikeTheJavaFunction() throws Exception {
        String[] rawValues = {
                "x1234567-l",
                " 1 2 3\t4 5678b ",
                "12.345.678-c",
                "\n00000001r\r",
                "ab\u000Bcd\fef",
                " 99999999z ",
                "b-12.345.678",
                "ALREADY1A",
        };
        try (Connection connection = freshDatabase(POSTGRES, "equivalence")) {
            applyUpToChangeSet(connection, BOUNDARY_CHANGESET_ID);
            Map<UUID, String> seeded = new LinkedHashMap<>();
            for (int i = 0; i < rawValues.length; i++) {
                seeded.put(insertUser(connection, rawValues[i], i + 1), rawValues[i]);
            }

            applyRest(connection);

            for (Map.Entry<UUID, String> row : seeded.entrySet()) {
                assertEquals(UserPersonalId.normalize(row.getValue()), personalIdOf(connection, row.getKey()),
                        "Java and SQL normalisation differ for '" + row.getValue() + "'");
            }
            assertTrue(constraintExists(connection));
        }
    }

    @Test
    void migrationHaltsAndChangesNothingWhenNormalisedValuesCollide() throws Exception {
        try (Connection connection = freshDatabase(POSTGRES, "collision")) {
            applyUpToChangeSet(connection, BOUNDARY_CHANGESET_ID);
            UUID first = insertUser(connection, "12345678A", 1);
            UUID second = insertUser(connection, "12345678-a", 2);
            UUID unrelated = insertUser(connection, "x1234567-l", 3);

            Exception failure = assertThrows(Exception.class, () -> applyRest(connection));

            assertTrue(messageChainContains(failure, "collide once normalised"),
                    "The failure should carry the precondition's message");
            assertEquals("12345678A", personalIdOf(connection, first));
            assertEquals("12345678-a", personalIdOf(connection, second));
            assertEquals("x1234567-l", personalIdOf(connection, unrelated));
            assertFalse(changesetApplied(connection, NORMALIZE_CHANGESET_ID));
            assertFalse(changesetApplied(connection, UNIQUE_CHANGESET_ID));
            assertFalse(constraintExists(connection));
        }
    }

    // --- Helpers ---

    private static UUID insertUser(Connection connection, String personalId, int number) throws SQLException {
        UUID id = UUID.randomUUID();
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO users (id, personal_id, number, password, full_name, email, enabled) "
                        + "VALUES (?, ?, ?, 'hash', 'Seeded user', 'seeded@example.org', true)")) {
            statement.setObject(1, id);
            statement.setString(2, personalId);
            statement.setInt(3, number);
            statement.executeUpdate();
        }
        return id;
    }

    private static String personalIdOf(Connection connection, UUID id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT personal_id FROM users WHERE id = ?")) {
            statement.setObject(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next());
                return resultSet.getString(1);
            }
        }
    }

    private static boolean constraintExists(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT count(*) FROM pg_constraint WHERE conname = '" + CONSTRAINT_NAME + "'")) {
            resultSet.next();
            return resultSet.getInt(1) == 1;
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

    private static boolean messageChainContains(Throwable throwable, String text) {
        for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
            if (cause.getMessage() != null && cause.getMessage().contains(text)) {
                return true;
            }
        }
        return false;
    }
}

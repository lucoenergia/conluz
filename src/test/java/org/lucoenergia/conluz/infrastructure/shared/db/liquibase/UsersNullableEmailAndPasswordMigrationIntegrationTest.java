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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lucoenergia.conluz.infrastructure.shared.db.liquibase.LiquibaseTestSupport.applyRest;
import static org.lucoenergia.conluz.infrastructure.shared.db.liquibase.LiquibaseTestSupport.applyUpToChangeSet;
import static org.lucoenergia.conluz.infrastructure.shared.db.liquibase.LiquibaseTestSupport.freshDatabase;
import static org.lucoenergia.conluz.infrastructure.shared.db.liquibase.LiquibaseTestSupport.rollbackToChangeSet;

/**
 * The changesets that make users.email and users.password nullable and turn empty or blank emails into NULL
 * (#389).
 */
class UsersNullableEmailAndPasswordMigrationIntegrationTest {

    /** The last changeset before the ones under test. */
    private static final String BOUNDARY_CHANGESET_ID = "make_community_memberships_full_name_not_null";
    private static final String EMAIL_NULLABLE_CHANGESET_ID = "make_users_email_nullable";
    private static final String NORMALIZE_CHANGESET_ID = "normalize_users_blank_email_to_null";
    private static final String PASSWORD_NULLABLE_CHANGESET_ID = "make_users_password_nullable";

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withUsername("luz")
            .withPassword("blank");

    @BeforeAll
    static void startContainer() {
        POSTGRES.start();
    }

    @Test
    void emptyAndBlankEmailsBecomeNull_andOtherEmailsAreUnchanged() throws Exception {
        try (Connection connection = freshDatabase(POSTGRES, "normalize")) {
            applyUpToChangeSet(connection, BOUNDARY_CHANGESET_ID);
            UUID empty = insertUser(connection, "11111111A", 1, "", "hash");
            UUID blank = insertUser(connection, "22222222B", 2, " \t ", "hash");
            UUID everyBlankCharacter = insertUser(connection, "33333333C", 3, " \t\n\r\f\u000B", "hash");
            UUID normal = insertUser(connection, "44444444D", 4, "member@example.org", "hash");

            applyRest(connection);

            assertNull(email(connection, empty));
            assertNull(email(connection, blank));
            assertNull(email(connection, everyBlankCharacter));
            assertEquals("member@example.org", email(connection, normal));
        }
    }

    @Test
    void bothColumnsBecomeNullable() throws Exception {
        try (Connection connection = freshDatabase(POSTGRES, "nullable")) {
            applyUpToChangeSet(connection, BOUNDARY_CHANGESET_ID);
            assertFalse(nullableColumns(connection).contains("email"));
            assertFalse(nullableColumns(connection).contains("password"));

            applyRest(connection);

            assertTrue(nullableColumns(connection).containsAll(Set.of("email", "password")));
            UUID passwordless = insertUser(connection, "11111111A", 1, null, null);
            assertNull(password(connection, passwordless));
            assertNull(email(connection, passwordless));
        }
    }

    @Test
    void rollingBack_restoresNotNull_withNoEmailAsTheEmptyString_andItCanBeAppliedAgain() throws Exception {
        try (Connection connection = freshDatabase(POSTGRES, "rollback")) {
            applyUpToChangeSet(connection, BOUNDARY_CHANGESET_ID);
            UUID empty = insertUser(connection, "11111111A", 1, "", "hash");
            UUID blank = insertUser(connection, "22222222B", 2, " \t ", "hash");
            UUID normal = insertUser(connection, "33333333C", 3, "member@example.org", "hash");
            applyRest(connection);
            UUID withoutEmailSinceTheMigration = insertUser(connection, "44444444D", 4, null, "hash");
            if (!connection.getAutoCommit()) {
                connection.commit();
            }

            rollbackToChangeSet(connection, BOUNDARY_CHANGESET_ID);

            assertFalse(nullableColumns(connection).contains("email"));
            assertFalse(nullableColumns(connection).contains("password"));
            assertEquals("", email(connection, empty));
            assertEquals("", email(connection, blank), "A blank email comes back as the empty string");
            assertEquals("member@example.org", email(connection, normal));
            assertEquals("", email(connection, withoutEmailSinceTheMigration));

            applyRest(connection);

            assertTrue(nullableColumns(connection).containsAll(Set.of("email", "password")));
            assertNull(email(connection, empty));
            assertNull(email(connection, blank));
            assertNull(email(connection, withoutEmailSinceTheMigration));
            assertEquals("member@example.org", email(connection, normal));
        }
    }

    @Test
    void rollingBackHalts_listingThePasswordlessAccounts_andRollsNothingBack() throws Exception {
        try (Connection connection = freshDatabase(POSTGRES, "halts")) {
            applyUpToChangeSet(connection, BOUNDARY_CHANGESET_ID);
            applyRest(connection);
            UUID passwordless = insertUser(connection, "11111111A", 1, null, null);
            insertUser(connection, "22222222B", 2, "member@example.org", "hash");
            if (!connection.getAutoCommit()) {
                connection.commit();
            }

            Exception failure = assertThrows(Exception.class,
                    () -> rollbackToChangeSet(connection, BOUNDARY_CHANGESET_ID));

            if (!connection.getAutoCommit()) {
                connection.rollback();
            }
            assertTrue(messageChainContains(failure, "some accounts have no password"),
                    "The failure should carry the rollback's message");
            assertTrue(messageChainContains(failure, passwordless.toString()),
                    "The failure should list the passwordless accounts");
            assertTrue(changesetApplied(connection, EMAIL_NULLABLE_CHANGESET_ID));
            assertTrue(changesetApplied(connection, NORMALIZE_CHANGESET_ID));
            assertTrue(changesetApplied(connection, PASSWORD_NULLABLE_CHANGESET_ID));
            assertTrue(nullableColumns(connection).containsAll(Set.of("email", "password")));
            assertNull(email(connection, passwordless));
            assertNull(password(connection, passwordless));
        }
    }

    @Test
    void rollingBackSucceeds_onceNoAccountIsWithoutAPassword() throws Exception {
        try (Connection connection = freshDatabase(POSTGRES, "succeeds")) {
            applyUpToChangeSet(connection, BOUNDARY_CHANGESET_ID);
            applyRest(connection);
            UUID passwordless = insertUser(connection, "11111111A", 1, null, null);
            if (!connection.getAutoCommit()) {
                connection.commit();
            }
            assertThrows(Exception.class, () -> rollbackToChangeSet(connection, BOUNDARY_CHANGESET_ID));
            if (!connection.getAutoCommit()) {
                connection.rollback();
            }

            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE users SET password = 'hash' WHERE id = ?")) {
                statement.setObject(1, passwordless);
                statement.executeUpdate();
            }
            if (!connection.getAutoCommit()) {
                connection.commit();
            }
            rollbackToChangeSet(connection, BOUNDARY_CHANGESET_ID);

            assertFalse(changesetApplied(connection, PASSWORD_NULLABLE_CHANGESET_ID));
            assertFalse(nullableColumns(connection).contains("password"));
            assertFalse(nullableColumns(connection).contains("email"));
            assertEquals("", email(connection, passwordless));
        }
    }

    // --- Helpers ---

    private static UUID insertUser(Connection connection, String personalId, int number, String email,
                                   String password) throws SQLException {
        UUID id = UUID.randomUUID();
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO users (id, personal_id, number, password, full_name, email, enabled) "
                        + "VALUES (?, ?, ?, ?, 'Member', ?, true)")) {
            statement.setObject(1, id);
            statement.setString(2, personalId);
            statement.setInt(3, number);
            statement.setString(4, password);
            statement.setString(5, email);
            statement.executeUpdate();
        }
        return id;
    }

    private static String email(Connection connection, UUID user) throws SQLException {
        return column(connection, user, "email");
    }

    private static String password(Connection connection, UUID user) throws SQLException {
        return column(connection, user, "password");
    }

    private static String column(Connection connection, UUID user, String column) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT " + column + " FROM users WHERE id = ?")) {
            statement.setObject(1, user);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next(), "The user should still exist");
                return resultSet.getString(1);
            }
        }
    }

    private static Set<String> nullableColumns(Connection connection) throws SQLException {
        Set<String> values = new HashSet<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT column_name FROM information_schema.columns "
                        + "WHERE table_name = 'users' AND is_nullable = 'YES'");
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

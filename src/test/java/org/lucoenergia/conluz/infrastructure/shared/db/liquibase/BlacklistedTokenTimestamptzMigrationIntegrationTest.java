package org.lucoenergia.conluz.infrastructure.shared.db.liquibase;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;

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
 * The changeset that turns blacklisted_token.expiration and revoked_at into TIMESTAMPTZ, keeping the instant every
 * existing value stands for (#481).
 */
class BlacklistedTokenTimestamptzMigrationIntegrationTest {

    /** The last changeset before the one under test. */
    private static final String BOUNDARY_CHANGESET_ID = "create_community_contact_channels_table";
    private static final String CHANGESET_ID = "convert_blacklisted_token_timestamps_to_timestamptz";

    /** The application writes an Instant into the TIMESTAMP columns as its UTC wall clock, whatever its zone. */
    private static final String SUMMER_WALL_CLOCK = "2026-07-01 10:00:00";
    private static final Instant SUMMER_INSTANT = Instant.parse("2026-07-01T10:00:00Z");
    private static final String WINTER_WALL_CLOCK = "2026-01-15 11:00:00";
    private static final Instant WINTER_INSTANT = Instant.parse("2026-01-15T11:00:00Z");

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withUsername("luz")
            .withPassword("blank");

    @BeforeAll
    static void startContainer() {
        POSTGRES.start();
    }

    @Test
    void theColumnsBecomeTimestamptz_andEveryStoredInstantIsKept_inSummerAndInWinter() throws Exception {
        try (Connection connection = freshDatabase(POSTGRES, "applies")) {
            applyUpToChangeSet(connection, BOUNDARY_CHANGESET_ID);
            // A session zone other than UTC, with a different offset in summer and in winter, so that reading the
            // values in the session's zone instead of UTC would shift both of them.
            seed(connection, "SET TIME ZONE 'Europe/Madrid'");
            insertToken(connection, "summer", SUMMER_WALL_CLOCK, WINTER_WALL_CLOCK);
            insertToken(connection, "winter", WINTER_WALL_CLOCK, SUMMER_WALL_CLOCK);

            applyRest(connection);

            assertEquals("timestamp with time zone", dataType(connection, "expiration"));
            assertEquals("timestamp with time zone", dataType(connection, "revoked_at"));
            assertEquals(SUMMER_INSTANT, instant(connection, "expiration", "summer"));
            assertEquals(WINTER_INSTANT, instant(connection, "revoked_at", "summer"));
            assertEquals(WINTER_INSTANT, instant(connection, "expiration", "winter"));
            assertEquals(SUMMER_INSTANT, instant(connection, "revoked_at", "winter"));
        }
    }

    @Test
    void halts_whenAColumnIsAlreadyTimestamptz_andAppliesNothing() throws Exception {
        try (Connection connection = freshDatabase(POSTGRES, "halts")) {
            applyUpToChangeSet(connection, BOUNDARY_CHANGESET_ID);
            seed(connection, "ALTER TABLE blacklisted_token ALTER COLUMN revoked_at TYPE TIMESTAMPTZ "
                    + "USING revoked_at AT TIME ZONE 'UTC'");
            commit(connection);

            Exception failure = assertThrows(Exception.class, () -> applyRest(connection));

            if (!connection.getAutoCommit()) {
                connection.rollback();
            }
            assertTrue(messageChainContains(failure, "Cannot convert blacklisted_token.expiration and revoked_at"),
                    "The failure should carry the precondition's message");
            assertFalse(changesetApplied(connection));
            assertEquals("timestamp without time zone", dataType(connection, "expiration"));
        }
    }

    @Test
    void rollingBack_restoresTheUtcWallClocks_andItCanBeAppliedAgain() throws Exception {
        try (Connection connection = freshDatabase(POSTGRES, "rollback")) {
            applyUpToChangeSet(connection, BOUNDARY_CHANGESET_ID);
            insertToken(connection, "summer", SUMMER_WALL_CLOCK, WINTER_WALL_CLOCK);
            applyRest(connection);
            commit(connection);

            seed(connection, "SET TIME ZONE 'Europe/Madrid'");
            rollbackToChangeSet(connection, BOUNDARY_CHANGESET_ID);

            assertFalse(changesetApplied(connection));
            assertEquals("timestamp without time zone", dataType(connection, "expiration"));
            assertEquals("timestamp without time zone", dataType(connection, "revoked_at"));
            assertEquals(SUMMER_WALL_CLOCK, text(connection, "expiration", "summer"));
            assertEquals(WINTER_WALL_CLOCK, text(connection, "revoked_at", "summer"));

            applyRest(connection);

            assertTrue(changesetApplied(connection));
            assertEquals(SUMMER_INSTANT, instant(connection, "expiration", "summer"));
            assertEquals(WINTER_INSTANT, instant(connection, "revoked_at", "summer"));
        }
    }

    // --- Helpers ---

    private static void insertToken(Connection connection, String jti, String expiration, String revokedAt)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO blacklisted_token (jti, expiration, revoked_at) "
                        + "VALUES (?, CAST(? AS TIMESTAMP), CAST(? AS TIMESTAMP))")) {
            statement.setString(1, jti);
            statement.setString(2, expiration);
            statement.setString(3, revokedAt);
            statement.executeUpdate();
        }
    }

    private static Instant instant(Connection connection, String column, String jti) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT " + column + " FROM blacklisted_token WHERE jti = ?")) {
            statement.setString(1, jti);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next(), "The token should still exist");
                return resultSet.getObject(1, OffsetDateTime.class).toInstant();
            }
        }
    }

    private static String text(Connection connection, String column, String jti) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT " + column + "::TEXT FROM blacklisted_token WHERE jti = ?")) {
            statement.setString(1, jti);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next(), "The token should still exist");
                return resultSet.getString(1);
            }
        }
    }

    private static String dataType(Connection connection, String column) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT data_type FROM information_schema.columns "
                        + "WHERE table_name = 'blacklisted_token' AND column_name = ?")) {
            statement.setString(1, column);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next(), "blacklisted_token." + column + " should exist");
                return resultSet.getString(1);
            }
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

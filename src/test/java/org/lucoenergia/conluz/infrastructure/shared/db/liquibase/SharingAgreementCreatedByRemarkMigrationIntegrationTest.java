package org.lucoenergia.conluz.infrastructure.shared.db.liquibase;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

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
 * The changeset that rewords the remark on sharing_agreement.created_by (#481).
 */
class SharingAgreementCreatedByRemarkMigrationIntegrationTest {

    /** The last changeset before the one under test. */
    private static final String BOUNDARY_CHANGESET_ID = "convert_blacklisted_token_timestamps_to_timestamptz";
    private static final String CHANGESET_ID = "reword_sharing_agreement_created_by_remark";

    private static final String REMARK = "The user who created the agreement. NULL means the agreement was created by "
            + "the system (a migration), not by a person, or that the creator's account has been deleted. Not filled "
            + "with a synthetic 'system' user: such a row could not authenticate and would appear in member listings.";

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withUsername("luz")
            .withPassword("blank");

    @BeforeAll
    static void startContainer() {
        POSTGRES.start();
    }

    @Test
    void theRemarkIsReworded() throws Exception {
        try (Connection connection = freshDatabase(POSTGRES, "applies")) {
            applyUpToChangeSet(connection, BOUNDARY_CHANGESET_ID);
            String before = remark(connection);

            applyRest(connection);

            assertTrue(before.contains("Phase"), "The remark replaced should be the one that cites the plan");
            assertEquals(REMARK, remark(connection));
        }
    }

    @Test
    void halts_whenTheColumnDoesNotExist_andAppliesNothing() throws Exception {
        try (Connection connection = freshDatabase(POSTGRES, "halts")) {
            applyUpToChangeSet(connection, BOUNDARY_CHANGESET_ID);
            seed(connection, "ALTER TABLE sharing_agreement DROP COLUMN created_by");
            commit(connection);

            Exception failure = assertThrows(Exception.class, () -> applyRest(connection));

            if (!connection.getAutoCommit()) {
                connection.rollback();
            }
            assertTrue(messageChainContains(failure, "Cannot reword the remark on sharing_agreement.created_by"),
                    "The failure should carry the precondition's message");
            assertFalse(changesetApplied(connection));
        }
    }

    @Test
    void rollingBack_keepsTheNewRemark_andItCanBeAppliedAgain() throws Exception {
        try (Connection connection = freshDatabase(POSTGRES, "rollback")) {
            applyUpToChangeSet(connection, BOUNDARY_CHANGESET_ID);
            applyRest(connection);
            commit(connection);

            rollbackToChangeSet(connection, BOUNDARY_CHANGESET_ID);

            assertFalse(changesetApplied(connection));
            assertEquals(REMARK, remark(connection));

            applyRest(connection);

            assertTrue(changesetApplied(connection));
            assertEquals(REMARK, remark(connection));
        }
    }

    // --- Helpers ---

    private static String remark(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT col_description('sharing_agreement'::regclass, a.attnum) FROM pg_attribute a "
                        + "WHERE a.attrelid = 'sharing_agreement'::regclass AND a.attname = 'created_by'");
             ResultSet resultSet = statement.executeQuery()) {
            assertTrue(resultSet.next(), "sharing_agreement.created_by should exist");
            return resultSet.getString(1);
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

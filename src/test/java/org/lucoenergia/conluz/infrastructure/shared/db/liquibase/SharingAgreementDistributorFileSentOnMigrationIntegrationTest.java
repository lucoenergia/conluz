package org.lucoenergia.conluz.infrastructure.shared.db.liquibase;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
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
 * The changeset that adds the day a sharing agreement's distributor file was sent (#580):
 * add_distributor_file_sent_on_to_sharing_agreement.
 */
class SharingAgreementDistributorFileSentOnMigrationIntegrationTest {

    /** The last changeset before the one under test. */
    private static final String BOUNDARY_CHANGESET_ID = "create_supply_ownership_transfers_table";
    private static final String CHANGESET_ID = "add_distributor_file_sent_on_to_sharing_agreement";
    private static final String COLUMN = "distributor_file_sent_on";
    private static final String REMARKS = "The local date the agreement's distributor file was sent to the distributor.";

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withUsername("luz")
            .withPassword("blank");

    @BeforeAll
    static void startContainer() {
        POSTGRES.start();
    }

    @Test
    void everyExistingAgreementHasNoSentDate_andTheColumnIsANullableDateWithRemarks() throws Exception {
        try (Connection connection = freshDatabase(POSTGRES, "applied")) {
            applyUpToChangeSet(connection, BOUNDARY_CHANGESET_ID);
            UUID plant = insertPlant(connection);
            UUID draft = insertAgreement(connection, plant, "DRAFT");
            UUID published = insertAgreement(connection, plant, "PUBLISHED");

            applyRest(connection);
            commit(connection);

            assertNull(sentOn(connection, draft));
            assertNull(sentOn(connection, published));
            assertEquals("date", dataType(connection));
            assertTrue(isNullable(connection));
            assertEquals(REMARKS, remarks(connection));
            execute(connection, "UPDATE sharing_agreement SET " + COLUMN + " = '2025-11-03' WHERE id = '" + draft + "'");
            assertEquals(LocalDate.of(2025, 11, 3), sentOn(connection, draft));
        }
    }

    @Test
    void halts_whenTheColumnAlreadyExists_andAppliesNothing() throws Exception {
        try (Connection connection = freshDatabase(POSTGRES, "halts")) {
            applyUpToChangeSet(connection, BOUNDARY_CHANGESET_ID);
            execute(connection, "ALTER TABLE sharing_agreement ADD COLUMN " + COLUMN + " text");
            commit(connection);

            Exception failure = assertThrows(Exception.class, () -> applyRest(connection));

            rollback(connection);
            assertTrue(messageChainContains(failure, "Cannot add sharing_agreement.distributor_file_sent_on"));
            assertFalse(changesetApplied(connection));
            assertEquals("text", dataType(connection));
        }
    }

    @Test
    void rollingBack_dropsTheColumn_andItCanBeAppliedAgain() throws Exception {
        try (Connection connection = freshDatabase(POSTGRES, "rollback")) {
            applyUpToChangeSet(connection, BOUNDARY_CHANGESET_ID);
            UUID agreement = insertAgreement(connection, insertPlant(connection), "DRAFT");
            applyRest(connection);
            commit(connection);

            rollbackToChangeSet(connection, BOUNDARY_CHANGESET_ID);

            assertFalse(columnExists(connection));
            assertFalse(changesetApplied(connection));

            applyRest(connection);

            assertTrue(changesetApplied(connection));
            assertNull(sentOn(connection, agreement));
        }
    }

    // --- Helpers ---

    private static UUID insertPlant(Connection connection) throws SQLException {
        UUID community = UUID.randomUUID();
        UUID supply = UUID.randomUUID();
        UUID plant = UUID.randomUUID();
        execute(connection, "INSERT INTO communities (id, name, code, enabled) VALUES ('" + community
                + "', 'Community', '" + community + "', true)");
        execute(connection, "INSERT INTO supplies (id, code, address, enabled, community_id) VALUES ('" + supply
                + "', 'ES0000000000000000001AB', 'Address', true, '" + community + "')");
        execute(connection, "INSERT INTO plants (id, name, provider_code, address, inverter_provider, supply_id, "
                + "peak_power_kwp, nominal_power_kw) VALUES ('" + plant + "', 'Plant', 'PLANT-1', 'Address', 'HUAWEI', '"
                + supply + "', 60, 60)");
        return plant;
    }

    private static UUID insertAgreement(Connection connection, UUID plant, String status) throws SQLException {
        UUID id = UUID.randomUUID();
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO sharing_agreement (id, plant_id, name, status, nominal_power_kw, created_at) "
                        + "VALUES (?, ?, 'Agreement', ?, 60, now())")) {
            statement.setObject(1, id);
            statement.setObject(2, plant);
            statement.setString(3, status);
            statement.executeUpdate();
        }
        return id;
    }

    private static LocalDate sentOn(Connection connection, UUID agreement) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT " + COLUMN + " FROM sharing_agreement WHERE id = ?")) {
            statement.setObject(1, agreement);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next());
                Date date = resultSet.getDate(1);
                return date == null ? null : date.toLocalDate();
            }
        }
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static boolean columnExists(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT count(*) FROM information_schema.columns "
                + "WHERE table_name = 'sharing_agreement' AND column_name = ?")) {
            statement.setString(1, COLUMN);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1) > 0;
            }
        }
    }

    private static String dataType(Connection connection) throws SQLException {
        return columnAttribute(connection, "data_type");
    }

    private static boolean isNullable(Connection connection) throws SQLException {
        return "YES".equals(columnAttribute(connection, "is_nullable"));
    }

    private static String columnAttribute(Connection connection, String attribute) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT " + attribute
                + " FROM information_schema.columns WHERE table_name = 'sharing_agreement' AND column_name = ?")) {
            statement.setString(1, COLUMN);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next());
                return resultSet.getString(1);
            }
        }
    }

    private static String remarks(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT col_description(c.oid, a.attnum) FROM pg_class c "
                        + "JOIN pg_attribute a ON a.attrelid = c.oid "
                        + "WHERE c.relname = 'sharing_agreement' AND a.attname = ?")) {
            statement.setString(1, COLUMN);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next());
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

    private static void rollback(Connection connection) throws SQLException {
        if (!connection.getAutoCommit()) {
            connection.rollback();
        }
    }

    private static boolean messageChainContains(Throwable failure, String text) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause.getMessage() != null && cause.getMessage().contains(text)) {
                return true;
            }
        }
        return false;
    }
}

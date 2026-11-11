package org.lucoenergia.conluz.infrastructure.shared.db.liquibase;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
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
 * The changeset that renames a sharing agreement's power to its nominal power (#567):
 * rename_sharing_agreement_installed_power_kw_to_nominal_power_kw.
 */
class SharingAgreementNominalPowerMigrationIntegrationTest {

    /** The last changeset before the one under test. */
    private static final String BOUNDARY_CHANGESET_ID = "add_not_null_constraint_to_plants_nominal_power_kw";
    private static final String CHANGESET_ID = "rename_sharing_agreement_installed_power_kw_to_nominal_power_kw";
    private static final String OLD_REMARKS = "A deliberate snapshot of the plant's installed power at authoring time, "
            + "not a join to plants.total_power: the plant's installed power today is not the power the agreement "
            + "was authored against.";
    private static final String NEW_REMARKS = "A snapshot of the plant's nominal power at authoring time, in kW, "
            + "recorded with the agreement and not read from the plant.";

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withUsername("luz")
            .withPassword("blank");

    @BeforeAll
    static void startContainer() {
        POSTGRES.start();
    }

    @Test
    void everyAgreementKeepsItsPower_asItsNominalPower() throws Exception {
        try (Connection connection = freshDatabase(POSTGRES, "applied")) {
            applyUpToChangeSet(connection, BOUNDARY_CHANGESET_ID);
            UUID plant = insertPlant(connection, 60.0);
            UUID withPower = insertAgreement(connection, plant, "80.000");
            UUID otherPower = insertAgreement(connection, plant, "12.5");
            UUID withoutPower = insertAgreement(connection, plant, null);

            applyRest(connection);
            commit(connection);

            assertFalse(columnExists(connection, "installed_power_kw"));
            assertEquals(new BigDecimal("80.000"), power(connection, withPower));
            assertEquals(new BigDecimal("12.5"), power(connection, otherPower));
            assertNull(power(connection, withoutPower));
            assertEquals(NEW_REMARKS, remarks(connection, "nominal_power_kw"));
        }
    }

    @Test
    void halts_whenTheNominalPowerColumnAlreadyExists_andAppliesNothing() throws Exception {
        try (Connection connection = freshDatabase(POSTGRES, "halts")) {
            applyUpToChangeSet(connection, BOUNDARY_CHANGESET_ID);
            execute(connection, "ALTER TABLE sharing_agreement ADD COLUMN nominal_power_kw numeric");
            commit(connection);

            Exception failure = assertThrows(Exception.class, () -> applyRest(connection));

            rollback(connection);
            assertTrue(messageChainContains(failure,
                    "Cannot rename sharing_agreement.installed_power_kw to nominal_power_kw"));
            assertFalse(changesetApplied(connection));
            assertTrue(columnExists(connection, "installed_power_kw"));
        }
    }

    @Test
    void rollingBack_restoresTheColumnAndItsRemarks_withTheValues_andCanBeAppliedAgain() throws Exception {
        try (Connection connection = freshDatabase(POSTGRES, "rollback")) {
            applyUpToChangeSet(connection, BOUNDARY_CHANGESET_ID);
            UUID agreement = insertAgreement(connection, insertPlant(connection, 60.0), "80");
            applyRest(connection);
            commit(connection);

            rollbackToChangeSet(connection, BOUNDARY_CHANGESET_ID);

            assertFalse(columnExists(connection, "nominal_power_kw"));
            assertEquals(OLD_REMARKS, remarks(connection, "installed_power_kw"));
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT installed_power_kw FROM sharing_agreement WHERE id = ?")) {
                statement.setObject(1, agreement);
                try (ResultSet resultSet = statement.executeQuery()) {
                    assertTrue(resultSet.next());
                    assertEquals(new BigDecimal("80"), resultSet.getBigDecimal(1));
                }
            }

            applyRest(connection);

            assertTrue(changesetApplied(connection));
            assertEquals(new BigDecimal("80"), power(connection, agreement));
        }
    }

    // --- Helpers ---

    private static UUID insertPlant(Connection connection, double power) throws SQLException {
        UUID community = UUID.randomUUID();
        UUID supply = UUID.randomUUID();
        UUID plant = UUID.randomUUID();
        execute(connection, "INSERT INTO communities (id, name, code, enabled) VALUES ('" + community
                + "', 'Community', '" + community + "', true)");
        execute(connection, "INSERT INTO supplies (id, code, address, enabled, community_id) VALUES ('" + supply
                + "', 'ES0000000000000000001AB', 'Address', true, '" + community + "')");
        execute(connection, "INSERT INTO plants (id, name, provider_code, address, inverter_provider, supply_id, "
                + "peak_power_kwp, nominal_power_kw) VALUES ('" + plant + "', 'Plant', 'PLANT-1', 'Address', 'HUAWEI', '"
                + supply + "', " + power + ", " + power + ")");
        return plant;
    }

    private static UUID insertAgreement(Connection connection, UUID plant, String power) throws SQLException {
        UUID id = UUID.randomUUID();
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO sharing_agreement (id, plant_id, name, status, installed_power_kw, created_at) "
                        + "VALUES (?, ?, 'Agreement', 'DRAFT', ?, now())")) {
            statement.setObject(1, id);
            statement.setObject(2, plant);
            statement.setBigDecimal(3, power == null ? null : new BigDecimal(power));
            statement.executeUpdate();
        }
        return id;
    }

    private static BigDecimal power(Connection connection, UUID agreement) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT nominal_power_kw FROM sharing_agreement WHERE id = ?")) {
            statement.setObject(1, agreement);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next());
                return resultSet.getBigDecimal(1);
            }
        }
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static boolean columnExists(Connection connection, String column) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT count(*) FROM information_schema.columns "
                + "WHERE table_name = 'sharing_agreement' AND column_name = ?")) {
            statement.setString(1, column);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1) > 0;
            }
        }
    }

    private static String remarks(Connection connection, String column) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT col_description(c.oid, a.attnum) FROM pg_class c "
                        + "JOIN pg_attribute a ON a.attrelid = c.oid "
                        + "WHERE c.relname = 'sharing_agreement' AND a.attname = ?")) {
            statement.setString(1, column);
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

    private static boolean messageChainContains(Throwable throwable, String text) {
        for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
            if (cause.getMessage() != null && cause.getMessage().contains(text)) {
                return true;
            }
        }
        return false;
    }
}

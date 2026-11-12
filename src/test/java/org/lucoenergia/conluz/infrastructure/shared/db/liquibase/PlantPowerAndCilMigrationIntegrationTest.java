package org.lucoenergia.conluz.infrastructure.shared.db.liquibase;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

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
 * The changesets that split a plant's power into peak and nominal power and add its CIL (#567):
 * rename_plants_total_power_to_peak_power_kwp, add_nominal_power_kw_to_plants, backfill_plants_nominal_power_kw,
 * add_not_null_constraint_to_plants_nominal_power_kw and add_cil_to_plants.
 */
class PlantPowerAndCilMigrationIntegrationTest {

    /** The last changeset before the ones under test. */
    private static final String BOUNDARY_CHANGESET_ID = "add_unique_constraint_to_plants_supply_id";
    private static final String RENAME = "rename_plants_total_power_to_peak_power_kwp";
    private static final String ADD_NOMINAL = "add_nominal_power_kw_to_plants";
    private static final String BACKFILL = "backfill_plants_nominal_power_kw";
    private static final String NOT_NULL = "add_not_null_constraint_to_plants_nominal_power_kw";
    /** The last changeset before add_cil_to_plants. */
    private static final String BEFORE_CIL = "rename_sharing_agreement_installed_power_kw_to_nominal_power_kw";
    private static final String ADD_CIL = "add_cil_to_plants";
    private static final String CIL = "ES0031406912345678AB1F001";

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withUsername("luz")
            .withPassword("blank");

    @BeforeAll
    static void startContainer() {
        POSTGRES.start();
    }

    @Test
    void existingPlantsGetTheirPowerAsPeakAndNominalPower_noCil_andBothPowersNotNull() throws Exception {
        try (Connection connection = freshDatabase(POSTGRES, "applied")) {
            applyUpToChangeSet(connection, BOUNDARY_CHANGESET_ID);
            UUID community = insertCommunity(connection);
            UUID luco = insertPlantWithTotalPower(connection, insertSupply(connection, community, "1"), "1", 80.0);
            UUID other = insertPlantWithTotalPower(connection, insertSupply(connection, community, "2"), "2", 20.5);

            applyRest(connection);
            commit(connection);
            connection.setAutoCommit(true);

            assertFalse(columnExists(connection, "plants", "total_power"));
            assertEquals(80.0, number(connection, "SELECT peak_power_kwp FROM plants WHERE id = ?", luco));
            assertEquals(80.0, number(connection, "SELECT nominal_power_kw FROM plants WHERE id = ?", luco));
            assertEquals(20.5, number(connection, "SELECT peak_power_kwp FROM plants WHERE id = ?", other));
            assertEquals(20.5, number(connection, "SELECT nominal_power_kw FROM plants WHERE id = ?", other));
            assertEquals(2, count(connection, "SELECT count(*) FROM plants WHERE cil IS NULL"));
            assertFalse(isNullable(connection, "plants", "peak_power_kwp"));
            assertFalse(isNullable(connection, "plants", "nominal_power_kw"));
            assertTrue(isNullable(connection, "plants", "cil"));
            assertEquals("Peak power of the plant's modules, in kWp.",
                    remarks(connection, "plants", "peak_power_kwp"));
            assertTrue(constraintExists(connection, "plants_cil_uq"));
            execute(connection, "UPDATE plants SET cil = '" + CIL + "' WHERE id = '" + luco + "'");
            assertThrows(SQLException.class,
                    () -> execute(connection, "UPDATE plants SET cil = '" + CIL + "' WHERE id = '" + other + "'"));
        }
    }

    @Test
    void renameHalts_whenPeakPowerAlreadyExists_andAppliesNothing() throws Exception {
        try (Connection connection = freshDatabase(POSTGRES, "halts_rename")) {
            applyUpToChangeSet(connection, BOUNDARY_CHANGESET_ID);
            execute(connection, "ALTER TABLE plants ADD COLUMN peak_power_kwp double precision");
            commit(connection);

            Exception failure = assertThrows(Exception.class, () -> applyRest(connection));

            rollback(connection);
            assertTrue(messageChainContains(failure, "Cannot rename plants.total_power to peak_power_kwp"));
            assertFalse(changesetApplied(connection, RENAME));
            assertTrue(columnExists(connection, "plants", "total_power"));
        }
    }

    @Test
    void addNominalHalts_whenTheColumnAlreadyExists_andAppliesNothing() throws Exception {
        try (Connection connection = freshDatabase(POSTGRES, "halts_add_nominal")) {
            applyUpToChangeSet(connection, RENAME);
            execute(connection, "ALTER TABLE plants ADD COLUMN nominal_power_kw double precision");
            commit(connection);

            Exception failure = assertThrows(Exception.class, () -> applyRest(connection));

            rollback(connection);
            assertTrue(messageChainContains(failure, "Cannot add plants.nominal_power_kw"));
            assertFalse(changesetApplied(connection, ADD_NOMINAL));
        }
    }

    @Test
    void backfillHalts_whenAPlantAlreadyHasANominalPower_andOverwritesNothing() throws Exception {
        try (Connection connection = freshDatabase(POSTGRES, "halts_backfill")) {
            applyUpToChangeSet(connection, ADD_NOMINAL);
            UUID plant = insertPlant(connection, insertSupply(connection, insertCommunity(connection), "1"), "1",
                    "peak_power_kwp, nominal_power_kw", "80.0, 60.0");
            commit(connection);

            Exception failure = assertThrows(Exception.class, () -> applyRest(connection));

            rollback(connection);
            assertTrue(messageChainContains(failure, "Cannot backfill plants.nominal_power_kw"));
            assertTrue(messageChainContains(failure,
                    "SELECT id, nominal_power_kw FROM plants WHERE nominal_power_kw IS NOT NULL"));
            assertFalse(changesetApplied(connection, BACKFILL));
            assertEquals(60.0, number(connection, "SELECT nominal_power_kw FROM plants WHERE id = ?", plant));
        }
    }

    @Test
    void notNullHalts_whenAPlantHasNoNominalPower_andListsThem() throws Exception {
        try (Connection connection = freshDatabase(POSTGRES, "halts_not_null")) {
            applyUpToChangeSet(connection, BACKFILL);
            insertPlant(connection, insertSupply(connection, insertCommunity(connection), "1"), "1",
                    "peak_power_kwp", "80.0");
            commit(connection);

            Exception failure = assertThrows(Exception.class, () -> applyRest(connection));

            rollback(connection);
            assertTrue(messageChainContains(failure, "Cannot make plants.nominal_power_kw NOT NULL"));
            assertTrue(messageChainContains(failure,
                    "SELECT id, peak_power_kwp FROM plants WHERE nominal_power_kw IS NULL"));
            assertFalse(changesetApplied(connection, NOT_NULL));
            assertTrue(isNullable(connection, "plants", "nominal_power_kw"));
        }
    }

    @Test
    void addCilHalts_whenTheColumnAlreadyExists_andAppliesNothing() throws Exception {
        try (Connection connection = freshDatabase(POSTGRES, "halts_cil")) {
            applyUpToChangeSet(connection, BEFORE_CIL);
            execute(connection, "ALTER TABLE plants ADD COLUMN cil varchar(25)");
            commit(connection);

            Exception failure = assertThrows(Exception.class, () -> applyRest(connection));

            rollback(connection);
            assertTrue(messageChainContains(failure, "Cannot add plants.cil"));
            assertFalse(changesetApplied(connection, ADD_CIL));
            assertFalse(constraintExists(connection, "plants_cil_uq"));
        }
    }

    @Test
    void rollingBack_restoresTotalPowerWithThePeakPower_andCanBeAppliedAgain() throws Exception {
        try (Connection connection = freshDatabase(POSTGRES, "rollback")) {
            applyUpToChangeSet(connection, BOUNDARY_CHANGESET_ID);
            applyRest(connection);
            UUID plant = insertPlant(connection, insertSupply(connection, insertCommunity(connection), "1"), "1",
                    "peak_power_kwp, nominal_power_kw, cil", "80.0, 60.0, '" + CIL + "'");
            commit(connection);

            rollbackToChangeSet(connection, BOUNDARY_CHANGESET_ID);

            assertEquals(80.0, number(connection, "SELECT total_power FROM plants WHERE id = ?", plant));
            assertFalse(columnExists(connection, "plants", "peak_power_kwp"));
            assertFalse(columnExists(connection, "plants", "nominal_power_kw"));
            assertFalse(columnExists(connection, "plants", "cil"));
            assertFalse(constraintExists(connection, "plants_cil_uq"));
            assertNull(remarks(connection, "plants", "total_power"));

            applyRest(connection);

            assertTrue(changesetApplied(connection, ADD_CIL));
            assertEquals(80.0, number(connection, "SELECT nominal_power_kw FROM plants WHERE id = ?", plant));
            assertFalse(isNullable(connection, "plants", "nominal_power_kw"));
        }
    }

    // --- Helpers ---

    private static UUID insertCommunity(Connection connection) throws SQLException {
        UUID id = UUID.randomUUID();
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO communities (id, name, code, enabled) VALUES (?, 'Community', ?, true)")) {
            statement.setObject(1, id);
            statement.setString(2, id.toString());
            statement.executeUpdate();
        }
        return id;
    }

    private static UUID insertSupply(Connection connection, UUID communityId, String code) throws SQLException {
        UUID supplyId = UUID.randomUUID();
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO supplies (id, code, address, enabled, community_id) VALUES (?, ?, 'Address', true, ?)")) {
            statement.setObject(1, supplyId);
            statement.setString(2, "ES00000000000000000" + code + "AB");
            statement.setObject(3, communityId);
            statement.executeUpdate();
        }
        return supplyId;
    }

    private static UUID insertPlantWithTotalPower(Connection connection, UUID supplyId, String code, double power)
            throws SQLException {
        return insertPlant(connection, supplyId, code, "total_power", String.valueOf(power));
    }

    private static UUID insertPlant(Connection connection, UUID supplyId, String code, String powerColumns,
                                    String powerValues) throws SQLException {
        UUID id = UUID.randomUUID();
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO plants (id, name, provider_code, address, inverter_provider, supply_id, " + powerColumns
                        + ") VALUES (?, 'Plant', ?, 'Address', 'HUAWEI', ?, " + powerValues + ")")) {
            statement.setObject(1, id);
            statement.setString(2, "PLANT-" + code);
            statement.setObject(3, supplyId);
            statement.executeUpdate();
        }
        return id;
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static double number(Connection connection, String sql, UUID id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next());
                return resultSet.getDouble(1);
            }
        }
    }

    private static int count(Connection connection, String sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            assertTrue(resultSet.next());
            return resultSet.getInt(1);
        }
    }

    private static boolean columnExists(Connection connection, String table, String column) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT count(*) FROM information_schema.columns WHERE table_name = ? AND column_name = ?")) {
            statement.setString(1, table);
            statement.setString(2, column);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1) > 0;
            }
        }
    }

    private static boolean isNullable(Connection connection, String table, String column) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT is_nullable FROM information_schema.columns WHERE table_name = ? AND column_name = ?")) {
            statement.setString(1, table);
            statement.setString(2, column);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next());
                return "YES".equals(resultSet.getString(1));
            }
        }
    }

    private static String remarks(Connection connection, String table, String column) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT col_description(c.oid, a.attnum) FROM pg_class c "
                        + "JOIN pg_attribute a ON a.attrelid = c.oid WHERE c.relname = ? AND a.attname = ?")) {
            statement.setString(1, table);
            statement.setString(2, column);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next());
                return resultSet.getString(1);
            }
        }
    }

    private static boolean constraintExists(Connection connection, String name) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT count(*) FROM pg_constraint WHERE conname = ? AND contype = 'u'")) {
            statement.setString(1, name);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1) > 0;
            }
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

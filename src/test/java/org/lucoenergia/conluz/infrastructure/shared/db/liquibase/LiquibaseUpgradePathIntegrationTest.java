package org.lucoenergia.conluz.infrastructure.shared.db.liquibase;

import liquibase.changelog.ChangeLogParameters;
import liquibase.changelog.ChangeSet;
import liquibase.changelog.DatabaseChangeLog;
import liquibase.command.CommandScope;
import liquibase.command.core.RollbackCountCommandStep;
import liquibase.command.core.UpdateCommandStep;
import liquibase.command.core.UpdateCountCommandStep;
import liquibase.command.core.helpers.DatabaseChangelogCommandStep;
import liquibase.command.core.helpers.DbUrlConnectionArgumentsCommandStep;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.parser.ChangeLogParser;
import liquibase.parser.ChangeLogParserFactory;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression test for conluz-281: upgrading a database from &lt;= 1.0.76 must not silently disable
 * Datadis, Huawei and Shelly integrations that were already active before those integrations became
 * configurable. Drives Liquibase directly via its command API (not Spring Boot autoconfiguration) so
 * each scenario can seed pre-existing rows at the exact point in the changelog chain where configurability
 * was introduced, then apply the rest of the chain and assert the backfill outcome.
 */
class LiquibaseUpgradePathIntegrationTest {

    private static final String CHANGELOG_PATH = "db/liquibase/db.changelog-main.xml";
    private static final String BOUNDARY_CHANGESET_ID = "add_address_ref_to_supplies_20250903T2347";
    private static final String SHELLY_TARGET_CHANGESET_ID = "create_shelly_config";

    // Test-local mirror of CreateSupplyRepositoryDatabase.DEFAULT_COMMUNITY_ID, deliberately not
    // imported so this test stays a pure black-box schema/data assertion.
    private static final String DEFAULT_COMMUNITY_ID = "f47ac10b-58cc-4372-a567-0e02b2c3d479";

    private static final String LEGACY_DATADIS_BASE_URL = "https://datadis.es";
    private static final String LEGACY_HUAWEI_BASE_URL = "https://eu5.fusionsolar.huawei.com/thirdData";

    private static final String PLANT_ID = "00000000-0000-0000-0000-0000000000c1";
    private static final String HOME_SUPPLY_ID = "00000000-0000-0000-0000-0000000000b2";
    private static final String SHELLY_MAC = "AA:BB:CC:DD:EE:01";

    private static final String SEEDED_DATADIS_USERNAME = "00000001R";
    private static final String SEEDED_DATADIS_PASSWORD = "legacy-datadis-secret";
    private static final String SEEDED_HUAWEI_USERNAME = "legacy-huawei-user";
    private static final String SEEDED_HUAWEI_PASSWORD = "legacy-huawei-secret";

    private static final String BASE_FIXTURE_SQL = """
            INSERT INTO users (id, personal_id, number, password, full_name, address, phone_number, email, role, enabled) VALUES
              ('00000000-0000-0000-0000-0000000000a1', '00000001R', 1, 'legacy-hash', 'Legacy Admin',   NULL, NULL, 'admin@example.org',   'ADMIN',   true),
              ('00000000-0000-0000-0000-0000000000a2', '00000002W', 2, 'legacy-hash', 'Legacy Partner', NULL, NULL, 'partner@example.org', 'PARTNER', true);
            INSERT INTO supplies (id, code, user_id, name, address, partition_coefficient, enabled) VALUES
              ('00000000-0000-0000-0000-0000000000b1', 'ES0000000000000001AA', '00000000-0000-0000-0000-0000000000a1', 'Plant supply', 'Address 1', 0.4, true),
              ('00000000-0000-0000-0000-0000000000b2', 'ES0000000000000002AA', '00000000-0000-0000-0000-0000000000a2', 'Home',         'Address 2', 0.6, true);
            INSERT INTO plants (id, name, code, address, description, inverter_provider, total_power, connection_date, supply_id) VALUES
              ('00000000-0000-0000-0000-0000000000c1', 'Legacy plant', 'NE=00000001', 'Address 1', NULL, 'HUAWEI', 100.0, '2024-05-23', '00000000-0000-0000-0000-0000000000b1');
            INSERT INTO datadis_config (id, username, password) VALUES
              ('00000000-0000-0000-0000-0000000000d1', '00000001R', 'legacy-datadis-secret');
            INSERT INTO huawei_config (id, username, password) VALUES
              ('00000000-0000-0000-0000-0000000000e1', 'legacy-huawei-user', 'legacy-huawei-secret');
            """;

    private static final String CONFIG_ONLY_FIXTURE_SQL = """
            INSERT INTO datadis_config (id, username, password) VALUES
              ('00000000-0000-0000-0000-0000000000d1', '00000001R', 'legacy-datadis-secret');
            INSERT INTO huawei_config (id, username, password) VALUES
              ('00000000-0000-0000-0000-0000000000e1', 'legacy-huawei-user', 'legacy-huawei-secret');
            """;

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withUsername("luz")
            .withPassword("blank");

    @BeforeAll
    static void startContainer() {
        POSTGRES.start();
    }

    @Test
    void t1_upgradeWithShellySupply_reenablesAllThreeIntegrations() throws Exception {
        try (Connection connection = freshDatabase("t1")) {
            applyUpToBoundary(connection);
            seed(connection, BASE_FIXTURE_SQL);
            seed(connection, "UPDATE supplies SET shelly_mac = '" + SHELLY_MAC + "' WHERE id = '" + HOME_SUPPLY_ID + "';");
            applyRest(connection);

            assertDatadisConfig(connection);
            assertHuaweiConfig(connection, PLANT_ID);
            assertShellyConfigRowCount(connection, 1);
            assertShellyConfigEnabledAndCommunity(connection);
        }
    }

    @Test
    void t1b_upgradeWithoutShellyIdentifiers_leavesShellyConfigEmpty() throws Exception {
        try (Connection connection = freshDatabase("t1b")) {
            applyUpToBoundary(connection);
            seed(connection, BASE_FIXTURE_SQL);
            applyRest(connection);

            assertDatadisConfig(connection);
            assertHuaweiConfig(connection, PLANT_ID);
            assertShellyConfigRowCount(connection, 0);
        }
    }

    @Test
    void t2_upgradeWithoutSupplies_reenablesDatadisAndHuaweiButNotShelly() throws Exception {
        try (Connection connection = freshDatabase("t2")) {
            applyUpToBoundary(connection);
            seed(connection, CONFIG_ONLY_FIXTURE_SQL);
            applyRest(connection);

            assertDatadisConfig(connection);
            assertHuaweiConfig(connection, null);
            assertShellyConfigRowCount(connection, 0);
        }
    }

    @Test
    void t3_freshInstall_leavesAllThreeIntegrationsUnconfigured() throws Exception {
        try (Connection connection = freshDatabase("t3")) {
            applyRest(connection);

            assertNoRows(connection, "datadis_config");
            assertNoRows(connection, "huawei_config");
            assertNoRows(connection, "shelly_config");
        }
    }

    @Test
    void t4_rollbackOfThreeChangesets_restoresPreConluz281Schema() throws Exception {
        try (Connection connection = freshDatabase("t4")) {
            int boundaryCount = oneBasedCountUpTo(BOUNDARY_CHANGESET_ID);
            applyUpToChangeSet(connection, SHELLY_TARGET_CHANGESET_ID);
            rollbackCount(connection, 3);

            assertColumnsAbsent(connection, "datadis_config", "base_url", "enabled");
            assertColumnsAbsent(connection, "huawei_config", "base_url", "enabled");
            assertTableAbsent(connection, "shelly_config");
            assertDatabaseChangeLogRowCount(connection, boundaryCount);
        }
    }

    // --- Liquibase driving helpers ---

    private static int oneBasedCountUpTo(String changesetId) throws Exception {
        ClassLoaderResourceAccessor resourceAccessor = new ClassLoaderResourceAccessor();
        ChangeLogParser parser = ChangeLogParserFactory.getInstance().getParser(CHANGELOG_PATH, resourceAccessor);
        DatabaseChangeLog changeLog = parser.parse(CHANGELOG_PATH, new ChangeLogParameters(), resourceAccessor);
        List<ChangeSet> changeSets = changeLog.getChangeSets();
        for (int i = 0; i < changeSets.size(); i++) {
            if (changeSets.get(i).getId().equals(changesetId)) {
                return i + 1;
            }
        }
        throw new IllegalStateException("Changeset id '" + changesetId + "' was not found in " + CHANGELOG_PATH
                + " -- this test's boundary constant is stale and must be updated to match the changelog.");
    }

    private static Database toLiquibaseDatabase(Connection connection) throws Exception {
        return DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
    }

    private static void applyUpToBoundary(Connection connection) throws Exception {
        applyUpToChangeSet(connection, BOUNDARY_CHANGESET_ID);
    }

    private static void applyUpToChangeSet(Connection connection, String changesetId) throws Exception {
        int count = oneBasedCountUpTo(changesetId);
        new CommandScope(UpdateCountCommandStep.COMMAND_NAME)
                .addArgumentValue(DbUrlConnectionArgumentsCommandStep.DATABASE_ARG, toLiquibaseDatabase(connection))
                .addArgumentValue(UpdateCountCommandStep.CHANGELOG_FILE_ARG, CHANGELOG_PATH)
                .addArgumentValue(UpdateCountCommandStep.COUNT_ARG, count)
                .execute();
    }

    private static void applyRest(Connection connection) throws Exception {
        new CommandScope(UpdateCommandStep.COMMAND_NAME)
                .addArgumentValue(DbUrlConnectionArgumentsCommandStep.DATABASE_ARG, toLiquibaseDatabase(connection))
                .addArgumentValue(UpdateCommandStep.CHANGELOG_FILE_ARG, CHANGELOG_PATH)
                .execute();
    }

    private static void rollbackCount(Connection connection, int count) throws Exception {
        new CommandScope(RollbackCountCommandStep.COMMAND_NAME)
                .addArgumentValue(DbUrlConnectionArgumentsCommandStep.DATABASE_ARG, toLiquibaseDatabase(connection))
                .addArgumentValue(DatabaseChangelogCommandStep.CHANGELOG_FILE_ARG, CHANGELOG_PATH)
                .addArgumentValue(RollbackCountCommandStep.COUNT_ARG, count)
                .execute();
    }

    private static void seed(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    // --- Per-scenario database isolation ---

    private static String jdbcUrl(String databaseName) {
        return "jdbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getMappedPort(5432) + "/" + databaseName;
    }

    private static Connection freshDatabase(String databaseName) throws SQLException {
        try (Connection admin = DriverManager.getConnection(jdbcUrl("postgres"), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = admin.createStatement()) {
            statement.execute("CREATE DATABASE " + databaseName);
        }
        return DriverManager.getConnection(jdbcUrl(databaseName), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    // --- Assertions ---

    private static void assertDatadisConfig(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT base_url, enabled, community_id, username, password FROM datadis_config")) {
            assertTrue(resultSet.next(), "Expected exactly one datadis_config row");
            assertEquals(LEGACY_DATADIS_BASE_URL, resultSet.getString("base_url"));
            assertTrue(resultSet.getBoolean("enabled"));
            assertEquals(DEFAULT_COMMUNITY_ID, resultSet.getString("community_id"));
            assertEquals(SEEDED_DATADIS_USERNAME, resultSet.getString("username"));
            assertEquals(SEEDED_DATADIS_PASSWORD, resultSet.getString("password"));
            assertFalse(resultSet.next(), "Expected exactly one datadis_config row");
        }
    }

    private static void assertHuaweiConfig(Connection connection, String expectedPlantId) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT base_url, enabled, plant_id, username, password FROM huawei_config")) {
            assertTrue(resultSet.next(), "Expected exactly one huawei_config row");
            assertEquals(LEGACY_HUAWEI_BASE_URL, resultSet.getString("base_url"));
            assertTrue(resultSet.getBoolean("enabled"));
            if (expectedPlantId == null) {
                assertNull(resultSet.getString("plant_id"));
            } else {
                assertEquals(expectedPlantId, resultSet.getString("plant_id"));
            }
            assertEquals(SEEDED_HUAWEI_USERNAME, resultSet.getString("username"));
            assertEquals(SEEDED_HUAWEI_PASSWORD, resultSet.getString("password"));
            assertFalse(resultSet.next(), "Expected exactly one huawei_config row");
        }
    }

    private static void assertShellyConfigRowCount(Connection connection, int expectedCount) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM shelly_config")) {
            resultSet.next();
            assertEquals(expectedCount, resultSet.getInt(1));
        }
    }

    private static void assertShellyConfigEnabledAndCommunity(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT enabled, community_id FROM shelly_config")) {
            assertTrue(resultSet.next());
            assertTrue(resultSet.getBoolean("enabled"));
            assertEquals(DEFAULT_COMMUNITY_ID, resultSet.getString("community_id"));
        }
    }

    private static void assertNoRows(Connection connection, String tableName) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM " + tableName)) {
            resultSet.next();
            assertEquals(0, resultSet.getInt(1), tableName + " should have no rows");
        }
    }

    private static void assertColumnsAbsent(Connection connection, String tableName, String... columnNames) throws SQLException {
        List<String> presentColumns = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT column_name FROM information_schema.columns WHERE table_name = '" + tableName + "'")) {
            while (resultSet.next()) {
                presentColumns.add(resultSet.getString("column_name"));
            }
        }
        for (String columnName : columnNames) {
            assertFalse(presentColumns.contains(columnName), "Column " + columnName + " should not exist on " + tableName);
        }
    }

    private static void assertTableAbsent(Connection connection, String tableName) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT to_regclass('public." + tableName + "')")) {
            resultSet.next();
            assertNull(resultSet.getString(1), tableName + " should not exist");
        }
    }

    private static void assertDatabaseChangeLogRowCount(Connection connection, int expectedCount) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM databasechangelog")) {
            resultSet.next();
            assertEquals(expectedCount, resultSet.getInt(1));
        }
    }
}

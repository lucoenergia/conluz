package org.lucoenergia.conluz.infrastructure.shared.db.liquibase;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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
 * The changesets that make a configuration's owner unique (#414): add_unique_constraint_to_huawei_config_plant_id
 * and add_unique_constraint_to_datadis_config_community_id.
 */
class ConfigurationOwnerUniqueMigrationIntegrationTest {

    /** The last changeset before the ones under test. */
    private static final String BOUNDARY_CHANGESET_ID = "bind_one_time_token_active_index_to_membership";
    private static final String HUAWEI_CHANGESET_ID = "add_unique_constraint_to_huawei_config_plant_id";
    private static final String DATADIS_CHANGESET_ID = "add_unique_constraint_to_datadis_config_community_id";

    private static final String HUAWEI_CONSTRAINT = "huawei_config_plant_id_uq";
    private static final String DATADIS_CONSTRAINT = "datadis_config_community_id_uq";

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withUsername("luz")
            .withPassword("blank");

    @BeforeAll
    static void startContainer() {
        POSTGRES.start();
    }

    @Test
    void existingConfigurationsAreKept_andASecondConfigurationForTheSameOwnerIsRejected() throws Exception {
        try (Connection connection = freshDatabase(POSTGRES, "applied")) {
            applyUpToChangeSet(connection, BOUNDARY_CHANGESET_ID);
            UUID community = insertCommunity(connection, "A");
            UUID otherCommunity = insertCommunity(connection, "B");
            UUID plant = insertPlant(connection, community, "1");
            UUID otherPlant = insertPlant(connection, community, "2");
            insertHuaweiConfig(connection, plant);
            insertHuaweiConfig(connection, otherPlant);
            insertHuaweiConfig(connection, null);
            insertHuaweiConfig(connection, null);
            insertDatadisConfig(connection, community);
            insertDatadisConfig(connection, otherCommunity);

            applyRest(connection);
            // Each rejected insert below must fail on its own, not because an earlier one aborted the transaction
            commit(connection);
            connection.setAutoCommit(true);

            assertEquals(4, count(connection, "SELECT count(*) FROM huawei_config"));
            assertEquals(2, count(connection, "SELECT count(*) FROM datadis_config"));
            assertTrue(constraintExists(connection, HUAWEI_CONSTRAINT));
            assertTrue(constraintExists(connection, DATADIS_CONSTRAINT));
            assertThrows(SQLException.class, () -> insertHuaweiConfig(connection, plant));
            assertThrows(SQLException.class, () -> insertDatadisConfig(connection, community));
            // NULL means "bound to no plant", and several such rows do not conflict
            assertDoesNotThrow(() -> insertHuaweiConfig(connection, null));
        }
    }

    @Test
    void halts_whenTwoHuaweiConfigurationsShareAPlant_andAppliesNothing() throws Exception {
        try (Connection connection = freshDatabase(POSTGRES, "halts_huawei_duplicates")) {
            applyUpToChangeSet(connection, BOUNDARY_CHANGESET_ID);
            UUID plant = insertPlant(connection, insertCommunity(connection, "A"), "1");
            insertHuaweiConfig(connection, plant);
            insertHuaweiConfig(connection, plant);
            commit(connection);

            Exception failure = assertThrows(Exception.class, () -> applyRest(connection));

            rollback(connection);
            assertTrue(messageChainContains(failure, "Cannot add " + HUAWEI_CONSTRAINT));
            assertTrue(messageChainContains(failure, "SELECT plant_id, count(*) FROM huawei_config"));
            assertFalse(changesetApplied(connection, HUAWEI_CHANGESET_ID));
            assertFalse(changesetApplied(connection, DATADIS_CHANGESET_ID));
            assertFalse(constraintExists(connection, HUAWEI_CONSTRAINT));
        }
    }

    @Test
    void halts_whenTheHuaweiConstraintAlreadyExists_andAppliesNothing() throws Exception {
        try (Connection connection = freshDatabase(POSTGRES, "halts_huawei_constraint")) {
            applyUpToChangeSet(connection, BOUNDARY_CHANGESET_ID);
            seed(connection, "ALTER TABLE huawei_config ADD CONSTRAINT " + HUAWEI_CONSTRAINT + " UNIQUE (id)");
            commit(connection);

            Exception failure = assertThrows(Exception.class, () -> applyRest(connection));

            rollback(connection);
            assertTrue(messageChainContains(failure, "Cannot add " + HUAWEI_CONSTRAINT));
            assertFalse(changesetApplied(connection, HUAWEI_CHANGESET_ID));
            assertFalse(changesetApplied(connection, DATADIS_CHANGESET_ID));
        }
    }

    @Test
    void halts_whenTwoDatadisConfigurationsShareACommunity_andAppliesNothing() throws Exception {
        try (Connection connection = freshDatabase(POSTGRES, "halts_datadis_duplicates")) {
            applyUpToChangeSet(connection, HUAWEI_CHANGESET_ID);
            UUID community = insertCommunity(connection, "A");
            insertDatadisConfig(connection, community);
            insertDatadisConfig(connection, community);
            commit(connection);

            Exception failure = assertThrows(Exception.class, () -> applyRest(connection));

            rollback(connection);
            assertTrue(messageChainContains(failure, "Cannot add " + DATADIS_CONSTRAINT));
            assertTrue(messageChainContains(failure, "SELECT community_id, count(*) FROM datadis_config"));
            assertFalse(changesetApplied(connection, DATADIS_CHANGESET_ID));
            assertFalse(constraintExists(connection, DATADIS_CONSTRAINT));
        }
    }

    @Test
    void halts_whenTheDatadisConstraintAlreadyExists_andAppliesNothing() throws Exception {
        try (Connection connection = freshDatabase(POSTGRES, "halts_datadis_constraint")) {
            applyUpToChangeSet(connection, HUAWEI_CHANGESET_ID);
            seed(connection, "ALTER TABLE datadis_config ADD CONSTRAINT " + DATADIS_CONSTRAINT + " UNIQUE (id)");
            commit(connection);

            Exception failure = assertThrows(Exception.class, () -> applyRest(connection));

            rollback(connection);
            assertTrue(messageChainContains(failure, "Cannot add " + DATADIS_CONSTRAINT));
            assertFalse(changesetApplied(connection, DATADIS_CHANGESET_ID));
        }
    }

    @Test
    void rollingBack_dropsBothConstraints_andCanBeAppliedAgain() throws Exception {
        try (Connection connection = freshDatabase(POSTGRES, "rollback")) {
            applyUpToChangeSet(connection, BOUNDARY_CHANGESET_ID);
            applyRest(connection);
            UUID community = insertCommunity(connection, "A");
            insertHuaweiConfig(connection, insertPlant(connection, community, "1"));
            insertDatadisConfig(connection, community);
            commit(connection);

            rollbackToChangeSet(connection, BOUNDARY_CHANGESET_ID);

            assertFalse(constraintExists(connection, HUAWEI_CONSTRAINT));
            assertFalse(constraintExists(connection, DATADIS_CONSTRAINT));
            assertEquals(1, count(connection, "SELECT count(*) FROM huawei_config"));
            assertEquals(1, count(connection, "SELECT count(*) FROM datadis_config"));

            applyRest(connection);

            assertTrue(changesetApplied(connection, DATADIS_CHANGESET_ID));
            assertTrue(constraintExists(connection, HUAWEI_CONSTRAINT));
            assertTrue(constraintExists(connection, DATADIS_CONSTRAINT));
        }
    }

    // --- Helpers ---

    private static UUID insertCommunity(Connection connection, String code) throws SQLException {
        UUID id = UUID.randomUUID();
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO communities (id, name, code, enabled) VALUES (?, ?, ?, true)")) {
            statement.setObject(1, id);
            statement.setString(2, "Community " + code);
            statement.setString(3, "CODE-" + code);
            statement.executeUpdate();
        }
        return id;
    }

    private static UUID insertPlant(Connection connection, UUID communityId, String code) throws SQLException {
        UUID supplyId = UUID.randomUUID();
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO supplies (id, code, address, enabled, community_id) VALUES (?, ?, 'Address', true, ?)")) {
            statement.setObject(1, supplyId);
            statement.setString(2, "ES00000000000000000" + code + "AB");
            statement.setObject(3, communityId);
            statement.executeUpdate();
        }
        UUID plantId = UUID.randomUUID();
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO plants (id, name, provider_code, address, inverter_provider, total_power, supply_id) "
                        + "VALUES (?, 'Plant', ?, 'Address', 'HUAWEI', 10.0, ?)")) {
            statement.setObject(1, plantId);
            statement.setString(2, "PLANT-" + code);
            statement.setObject(3, supplyId);
            statement.executeUpdate();
        }
        return plantId;
    }

    private static void insertHuaweiConfig(Connection connection, UUID plantId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO huawei_config (id, username, password, plant_id) VALUES (?, 'user', 'password', ?)")) {
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, plantId);
            statement.executeUpdate();
        }
    }

    private static void insertDatadisConfig(Connection connection, UUID communityId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO datadis_config (id, username, password, community_id) VALUES (?, 'user', 'password', ?)")) {
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, communityId);
            statement.executeUpdate();
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

    private static int count(Connection connection, String sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            assertTrue(resultSet.next());
            return resultSet.getInt(1);
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

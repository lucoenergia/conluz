package org.lucoenergia.conluz.infrastructure.shared.db.liquibase;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

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
 * The changeset that makes a plant's supply unique (#411): add_unique_constraint_to_plants_supply_id.
 */
class PlantsSupplyIdUniqueMigrationIntegrationTest {

    /** The last changeset before the one under test. */
    private static final String BOUNDARY_CHANGESET_ID = "reword_sharing_agreement_created_by_remark";
    private static final String CHANGESET_ID = "add_unique_constraint_to_plants_supply_id";
    private static final String CONSTRAINT = "plants_supply_id_uq";

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withUsername("luz")
            .withPassword("blank");

    @BeforeAll
    static void startContainer() {
        POSTGRES.start();
    }

    @Test
    void existingPlantsAreKept_andASecondPlantOnTheSameSupplyIsRejected() throws Exception {
        try (Connection connection = freshDatabase(POSTGRES, "applied")) {
            applyUpToChangeSet(connection, BOUNDARY_CHANGESET_ID);
            UUID community = insertCommunity(connection, "A");
            UUID supply = insertSupply(connection, community, "1");
            UUID otherSupply = insertSupply(connection, community, "2");
            insertPlant(connection, supply, "1");
            insertPlant(connection, otherSupply, "2");

            applyRest(connection);
            // The rejected insert below must fail on its own, not because an earlier statement aborted the transaction
            commit(connection);
            connection.setAutoCommit(true);

            assertEquals(2, count(connection, "SELECT count(*) FROM plants"));
            assertTrue(constraintExists(connection, CONSTRAINT));
            assertThrows(SQLException.class, () -> insertPlant(connection, supply, "3"));
        }
    }

    @Test
    void halts_whenTwoPlantsShareASupply_andAppliesNothing() throws Exception {
        try (Connection connection = freshDatabase(POSTGRES, "halts_duplicates")) {
            applyUpToChangeSet(connection, BOUNDARY_CHANGESET_ID);
            UUID supply = insertSupply(connection, insertCommunity(connection, "A"), "1");
            insertPlant(connection, supply, "1");
            insertPlant(connection, supply, "2");
            commit(connection);

            Exception failure = assertThrows(Exception.class, () -> applyRest(connection));

            rollback(connection);
            assertTrue(messageChainContains(failure, "Cannot add " + CONSTRAINT));
            assertTrue(messageChainContains(failure, "SELECT supply_id, count(*) FROM plants"));
            assertFalse(changesetApplied(connection, CHANGESET_ID));
            assertFalse(constraintExists(connection, CONSTRAINT));
            assertEquals(2, count(connection, "SELECT count(*) FROM plants"));
        }
    }

    @Test
    void halts_whenTheConstraintAlreadyExists_andAppliesNothing() throws Exception {
        try (Connection connection = freshDatabase(POSTGRES, "halts_constraint")) {
            applyUpToChangeSet(connection, BOUNDARY_CHANGESET_ID);
            seed(connection, "ALTER TABLE plants ADD CONSTRAINT " + CONSTRAINT + " UNIQUE (id)");
            commit(connection);

            Exception failure = assertThrows(Exception.class, () -> applyRest(connection));

            rollback(connection);
            assertTrue(messageChainContains(failure, "Cannot add " + CONSTRAINT));
            assertFalse(changesetApplied(connection, CHANGESET_ID));
        }
    }

    @Test
    void rollingBack_dropsTheConstraint_andCanBeAppliedAgain() throws Exception {
        try (Connection connection = freshDatabase(POSTGRES, "rollback")) {
            applyUpToChangeSet(connection, BOUNDARY_CHANGESET_ID);
            applyRest(connection);
            insertPlant(connection, insertSupply(connection, insertCommunity(connection, "A"), "1"), "1");
            commit(connection);

            rollbackToChangeSet(connection, BOUNDARY_CHANGESET_ID);

            assertFalse(constraintExists(connection, CONSTRAINT));
            assertEquals(1, count(connection, "SELECT count(*) FROM plants"));

            applyRest(connection);

            assertTrue(changesetApplied(connection, CHANGESET_ID));
            assertTrue(constraintExists(connection, CONSTRAINT));
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

    private static void insertPlant(Connection connection, UUID supplyId, String code) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO plants (id, name, provider_code, address, inverter_provider, total_power, supply_id) "
                        + "VALUES (?, 'Plant', ?, 'Address', 'HUAWEI', 10.0, ?)")) {
            statement.setObject(1, UUID.randomUUID());
            statement.setString(2, "PLANT-" + code);
            statement.setObject(3, supplyId);
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

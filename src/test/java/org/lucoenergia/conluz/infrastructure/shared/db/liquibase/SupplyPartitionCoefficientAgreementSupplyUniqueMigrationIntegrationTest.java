package org.lucoenergia.conluz.infrastructure.shared.db.liquibase;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
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
 * The changeset that makes a supply hold at most one coefficient per sharing agreement (#601):
 * add_unique_constraint_to_supply_partition_coefficient_sharing_agreement_id_supply_id.
 */
class SupplyPartitionCoefficientAgreementSupplyUniqueMigrationIntegrationTest {

    /** The last changeset before the one under test. */
    private static final String BOUNDARY_CHANGESET_ID = "add_distributor_file_sent_on_to_sharing_agreement";
    private static final String CHANGESET_ID =
            "add_unique_constraint_to_supply_partition_coefficient_sharing_agreement_id_supply_id";
    private static final String CONSTRAINT = "supply_partition_coefficient_sharing_agreement_id_supply_id_uq";
    private static final String LISTING_QUERY = "SELECT sharing_agreement_id, supply_id, count(*) "
            + "FROM supply_partition_coefficient GROUP BY sharing_agreement_id, supply_id HAVING count(*) > 1";

    private static final Instant T0 = Instant.parse("2024-01-01T00:00:00Z");
    private static final Instant T1 = Instant.parse("2025-01-01T00:00:00Z");

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withUsername("luz")
            .withPassword("blank");

    @BeforeAll
    static void startContainer() {
        POSTGRES.start();
    }

    @Test
    @DisplayName("SHA-018 with no supply holding two coefficients in one agreement, the constraint is added and a second one is refused")
    void existingCoefficientsAreKept_andASecondCoefficientOfASupplyInTheSameAgreementIsRejected() throws Exception {
        try (Connection connection = freshDatabase(POSTGRES, "applied")) {
            applyUpToChangeSet(connection, BOUNDARY_CHANGESET_ID);
            UUID community = insertCommunity(connection);
            UUID supply = insertSupply(connection, community, "1");
            UUID otherSupply = insertSupply(connection, community, "2");
            UUID plant = insertPlant(connection, insertSupply(connection, community, "3"));
            UUID earlier = insertAgreement(connection, plant, "SUPERSEDED");
            UUID later = insertAgreement(connection, plant, "PUBLISHED");
            // The same supply in two agreements, and two supplies in one agreement: neither is a duplicate
            insertCoefficient(connection, supply, plant, earlier, T0, T1);
            insertCoefficient(connection, supply, plant, later, T1, null);
            insertCoefficient(connection, otherSupply, plant, earlier, T0, null);

            applyRest(connection);
            // The rejected insert below must fail on its own, not because an earlier statement aborted the transaction
            commit(connection);
            connection.setAutoCommit(true);

            assertEquals(3, count(connection, "SELECT count(*) FROM supply_partition_coefficient"));
            assertTrue(constraintExists(connection, CONSTRAINT));
            // A pending row, so only the new constraint can refuse it: the exclusion constraint skips pending rows
            SQLException refused = assertThrows(SQLException.class,
                    () -> insertCoefficient(connection, supply, plant, later, null, null));
            assertTrue(messageChainContains(refused, CONSTRAINT));
        }
    }

    @Test
    @DisplayName("SHA-018 a supply holding two coefficients in one agreement halts the changeset with the listing query, and changes nothing")
    void halts_whenASupplyHoldsTwoCoefficientsInOneAgreement_andAppliesNothing() throws Exception {
        try (Connection connection = freshDatabase(POSTGRES, "halts_duplicates")) {
            applyUpToChangeSet(connection, BOUNDARY_CHANGESET_ID);
            UUID community = insertCommunity(connection);
            UUID supply = insertSupply(connection, community, "1");
            UUID plant = insertPlant(connection, insertSupply(connection, community, "2"));
            UUID agreement = insertAgreement(connection, plant, "PUBLISHED");
            // Two consecutive periods, so no other constraint refuses them
            insertCoefficient(connection, supply, plant, agreement, T0, T1);
            insertCoefficient(connection, supply, plant, agreement, T1, null);
            commit(connection);

            Exception failure = assertThrows(Exception.class, () -> applyRest(connection));

            rollback(connection);
            assertTrue(messageChainContains(failure, "Cannot add " + CONSTRAINT));
            assertTrue(messageChainContains(failure, LISTING_QUERY));
            assertFalse(changesetApplied(connection, CHANGESET_ID));
            assertFalse(constraintExists(connection, CONSTRAINT));
            assertEquals(2, count(connection, "SELECT count(*) FROM supply_partition_coefficient"));
        }
    }

    @Test
    void halts_whenTheConstraintAlreadyExists_andAppliesNothing() throws Exception {
        try (Connection connection = freshDatabase(POSTGRES, "halts_constraint")) {
            applyUpToChangeSet(connection, BOUNDARY_CHANGESET_ID);
            seed(connection, "ALTER TABLE supply_partition_coefficient ADD CONSTRAINT " + CONSTRAINT + " UNIQUE (id)");
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
            UUID community = insertCommunity(connection);
            UUID plant = insertPlant(connection, insertSupply(connection, community, "2"));
            insertCoefficient(connection, insertSupply(connection, community, "1"), plant,
                    insertAgreement(connection, plant, "PUBLISHED"), T0, null);
            commit(connection);

            rollbackToChangeSet(connection, BOUNDARY_CHANGESET_ID);

            assertFalse(constraintExists(connection, CONSTRAINT));
            assertEquals(1, count(connection, "SELECT count(*) FROM supply_partition_coefficient"));

            applyRest(connection);

            assertTrue(changesetApplied(connection, CHANGESET_ID));
            assertTrue(constraintExists(connection, CONSTRAINT));
        }
    }

    // --- Helpers ---

    private static UUID insertCommunity(Connection connection) throws SQLException {
        UUID id = UUID.randomUUID();
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO communities (id, name, code, enabled) VALUES (?, 'Community', 'CODE', true)")) {
            statement.setObject(1, id);
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

    private static UUID insertPlant(Connection connection, UUID supplyId) throws SQLException {
        UUID plantId = UUID.randomUUID();
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO plants (id, name, provider_code, address, inverter_provider, peak_power_kwp, "
                        + "nominal_power_kw, supply_id) VALUES (?, 'Plant', ?, 'Address', 'HUAWEI', 10.0, 10.0, ?)")) {
            statement.setObject(1, plantId);
            statement.setString(2, "PLANT-" + plantId);
            statement.setObject(3, supplyId);
            statement.executeUpdate();
        }
        return plantId;
    }

    private static UUID insertAgreement(Connection connection, UUID plantId, String status) throws SQLException {
        UUID agreementId = UUID.randomUUID();
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO sharing_agreement (id, plant_id, name, status, created_at) VALUES (?, ?, ?, ?, now())")) {
            statement.setObject(1, agreementId);
            statement.setObject(2, plantId);
            statement.setString(3, "Agreement " + agreementId);
            statement.setString(4, status);
            statement.executeUpdate();
        }
        return agreementId;
    }

    private static void insertCoefficient(Connection connection, UUID supplyId, UUID plantId, UUID agreementId,
                                          Instant validFrom, Instant validTo) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO supply_partition_coefficient (id, supply_id, coefficient, valid_from, valid_to, plant_id, "
                        + "sharing_agreement_id) VALUES (?, ?, 0.5, ?, ?, ?, ?)")) {
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, supplyId);
            statement.setTimestamp(3, validFrom == null ? null : Timestamp.from(validFrom));
            statement.setTimestamp(4, validTo == null ? null : Timestamp.from(validTo));
            statement.setObject(5, plantId);
            statement.setObject(6, agreementId);
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

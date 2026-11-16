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
 * The changeset of supply ownership transfers (#585): create_supply_ownership_transfers_table.
 */
class SupplyOwnershipTransfersMigrationIntegrationTest {

    /** The last changeset before the one under test. */
    private static final String BOUNDARY_CHANGESET_ID = "add_cil_to_plants";
    private static final String TABLE_CHANGESET_ID = "create_supply_ownership_transfers_table";

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withUsername("luz")
            .withPassword("blank");

    @BeforeAll
    static void startContainer() {
        POSTGRES.start();
    }

    @Test
    void theConstraintsHaveTheirExactDefinitions() throws Exception {
        try (Connection connection = freshDatabase(POSTGRES, "applied")) {
            applyUpToChangeSet(connection, BOUNDARY_CHANGESET_ID);
            applyRest(connection);

            assertEquals("CASCADE", deleteRule(connection, "supply_ownership_transfers_supply_id_fk"));
            assertEquals("NO ACTION", deleteRule(connection, "supply_ownership_transfers_previous_user_id_fk"));
            assertEquals("NO ACTION", deleteRule(connection, "supply_ownership_transfers_new_user_id_fk"));
            assertEquals("NO ACTION", deleteRule(connection, "supply_ownership_transfers_transferred_by_fk"));
            assertEquals("FOREIGN KEY (supply_id) REFERENCES supplies(id) ON DELETE CASCADE",
                    constraintDefinition(connection, "supply_ownership_transfers_supply_id_fk"));
            assertEquals("PRIMARY KEY (id)", constraintDefinition(connection, "supply_ownership_transfers_id_pk"));
            assertEquals("CREATE INDEX supply_ownership_transfers_supply_id_idx ON public.supply_ownership_transfers "
                    + "USING btree (supply_id)", scalar(connection, "SELECT indexdef FROM pg_indexes "
                    + "WHERE indexname = 'supply_ownership_transfers_supply_id_idx'"));
            assertEquals("YES", nullable(connection, "previous_user_id"));
            for (String column : new String[]{"supply_id", "new_user_id", "effective_date", "history_included",
                    "transferred_by", "transferred_at"}) {
                assertEquals("NO", nullable(connection, column), column);
            }
            assertEquals("timestamp with time zone", scalar(connection, "SELECT data_type FROM "
                    + "information_schema.columns WHERE table_name = 'supply_ownership_transfers' "
                    + "AND column_name = 'transferred_at'"));
            assertEquals("date", scalar(connection, "SELECT data_type FROM information_schema.columns "
                    + "WHERE table_name = 'supply_ownership_transfers' AND column_name = 'effective_date'"));
        }
    }

    @Test
    void deletingASupplyDeletesItsRecords_andOnlyThem() throws Exception {
        try (Connection connection = freshDatabase(POSTGRES, "cascade")) {
            applyUpToChangeSet(connection, BOUNDARY_CHANGESET_ID);
            applyRest(connection);
            UUID community = insertCommunity(connection);
            UUID user = insertUser(connection, "11111111H");
            UUID deleted = insertSupply(connection, community, user, "ES0000000000000000010F");
            UUID kept = insertSupply(connection, community, user, "ES0000000000000000020F");
            insertTransfer(connection, deleted, user, user);
            insertTransfer(connection, deleted, user, user);
            insertTransfer(connection, kept, user, user);

            seed(connection, "DELETE FROM supplies WHERE id = '" + deleted + "'");

            assertEquals("0", scalar(connection, "SELECT count(*) FROM supply_ownership_transfers "
                    + "WHERE supply_id = '" + deleted + "'"));
            assertEquals("1", scalar(connection, "SELECT count(*) FROM supply_ownership_transfers "
                    + "WHERE supply_id = '" + kept + "'"));
        }
    }

    @Test
    void aUserNamedInARecordCannotBeDeleted() throws Exception {
        try (Connection connection = freshDatabase(POSTGRES, "users_fk")) {
            applyUpToChangeSet(connection, BOUNDARY_CHANGESET_ID);
            applyRest(connection);
            UUID community = insertCommunity(connection);
            UUID owner = insertUser(connection, "11111111H");
            UUID previous = insertUser(connection, "22222222J");
            UUID supply = insertSupply(connection, community, owner, "ES0000000000000000010F");
            insertTransfer(connection, supply, previous, owner);

            SQLException refused = assertThrows(SQLException.class,
                    () -> seed(connection, "DELETE FROM users WHERE id = '" + previous + "'"));
            assertTrue(refused.getMessage().contains("supply_ownership_transfers_previous_user_id_fk"));
        }
    }

    @Test
    void halts_whenTheTableAlreadyExists_andAppliesNothing() throws Exception {
        try (Connection connection = freshDatabase(POSTGRES, "halts_table")) {
            applyUpToChangeSet(connection, BOUNDARY_CHANGESET_ID);
            seed(connection, "CREATE TABLE supply_ownership_transfers (id UUID PRIMARY KEY)");
            commit(connection);

            Exception failure = assertThrows(Exception.class, () -> applyRest(connection));

            rollback(connection);
            assertTrue(messageChainContains(failure, "Cannot create supply_ownership_transfers"));
            assertFalse(changesetApplied(connection, TABLE_CHANGESET_ID));
            assertEquals("1", scalar(connection, "SELECT count(*) FROM information_schema.columns "
                    + "WHERE table_name = 'supply_ownership_transfers'"));
        }
    }

    @Test
    void rollingBack_dropsTheTable_andCanBeAppliedAgain() throws Exception {
        try (Connection connection = freshDatabase(POSTGRES, "rollback")) {
            applyUpToChangeSet(connection, BOUNDARY_CHANGESET_ID);
            applyRest(connection);
            UUID user = insertUser(connection, "11111111H");
            insertTransfer(connection, insertSupply(connection, insertCommunity(connection), user,
                    "ES0000000000000000010F"), user, user);
            commit(connection);

            rollbackToChangeSet(connection, BOUNDARY_CHANGESET_ID);

            assertEquals("0", scalar(connection, "SELECT count(*) FROM information_schema.tables "
                    + "WHERE table_name = 'supply_ownership_transfers'"));
            assertFalse(changesetApplied(connection, TABLE_CHANGESET_ID));

            applyRest(connection);

            assertTrue(changesetApplied(connection, TABLE_CHANGESET_ID));
            assertEquals("0", scalar(connection, "SELECT count(*) FROM supply_ownership_transfers"));
        }
    }

    // --- Helpers ---

    private static UUID insertCommunity(Connection connection) throws SQLException {
        UUID id = UUID.randomUUID();
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO communities (id, name, code, enabled) VALUES (?, ?, ?, true)")) {
            statement.setObject(1, id);
            statement.setString(2, "Community " + id);
            statement.setString(3, "CODE-" + id);
            statement.executeUpdate();
        }
        return id;
    }

    private static UUID insertUser(Connection connection, String personalId) throws SQLException {
        UUID id = UUID.randomUUID();
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO users (id, personal_id, full_name, enabled) VALUES (?, ?, 'Name', true)")) {
            statement.setObject(1, id);
            statement.setString(2, personalId);
            statement.executeUpdate();
        }
        return id;
    }

    private static UUID insertSupply(Connection connection, UUID communityId, UUID userId, String code)
            throws SQLException {
        UUID id = UUID.randomUUID();
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO supplies (id, code, user_id, address, enabled, community_id) "
                        + "VALUES (?, ?, ?, 'Address', true, ?)")) {
            statement.setObject(1, id);
            statement.setString(2, code);
            statement.setObject(3, userId);
            statement.setObject(4, communityId);
            statement.executeUpdate();
        }
        return id;
    }

    private static void insertTransfer(Connection connection, UUID supplyId, UUID previousUserId, UUID newUserId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO supply_ownership_transfers (id, supply_id, previous_user_id, new_user_id, "
                        + "effective_date, history_included, transferred_by, transferred_at) "
                        + "VALUES (?, ?, ?, ?, DATE '2026-03-15', true, ?, now())")) {
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, supplyId);
            statement.setObject(3, previousUserId);
            statement.setObject(4, newUserId);
            statement.setObject(5, newUserId);
            statement.executeUpdate();
        }
    }

    private static String deleteRule(Connection connection, String constraint) throws SQLException {
        return scalar(connection, "SELECT delete_rule FROM information_schema.referential_constraints "
                + "WHERE constraint_name = '" + constraint + "'");
    }

    private static String nullable(Connection connection, String column) throws SQLException {
        return scalar(connection, "SELECT is_nullable FROM information_schema.columns "
                + "WHERE table_name = 'supply_ownership_transfers' AND column_name = '" + column + "'");
    }

    private static String constraintDefinition(Connection connection, String constraint) throws SQLException {
        return scalar(connection, "SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname = '"
                + constraint + "'");
    }

    private static String scalar(Connection connection, String sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            assertTrue(resultSet.next());
            return resultSet.getString(1);
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

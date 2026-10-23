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
 * The changeset of community contact channels (#462): create_community_contact_channels_table.
 */
class CommunityContactChannelsMigrationIntegrationTest {

    /** The last changeset before the one under test. */
    private static final String BOUNDARY_CHANGESET_ID = "bind_one_time_token_active_index_to_platform_admin_invitation";
    private static final String TABLE_CHANGESET_ID = "create_community_contact_channels_table";

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

            assertEquals("NO ACTION", scalar(connection, "SELECT delete_rule FROM "
                    + "information_schema.referential_constraints "
                    + "WHERE constraint_name = 'community_contact_channels_community_id_fk'"));
            assertEquals("CHECK (((type)::text = ANY ((ARRAY['PHONE'::character varying, 'EMAIL'::character varying, "
                            + "'ADDRESS'::character varying])::text[])))",
                    constraintDefinition(connection, "community_contact_channels_type_check"));
            assertEquals("UNIQUE (community_id, \"position\")",
                    constraintDefinition(connection, "community_contact_channels_community_id_position_uq"));
        }
    }

    @Test
    void eachTypeIsAccepted_andAnUnknownTypeIsRefused() throws Exception {
        try (Connection connection = freshDatabase(POSTGRES, "type")) {
            applyUpToChangeSet(connection, BOUNDARY_CHANGESET_ID);
            applyRest(connection);
            UUID community = insertCommunity(connection, "A");

            insertChannel(connection, community, "PHONE", 0);
            insertChannel(connection, community, "EMAIL", 1);
            insertChannel(connection, community, "ADDRESS", 2);

            SQLException refused = assertThrows(SQLException.class,
                    () -> insertChannel(connection, community, "FAX", 3));
            assertTrue(refused.getMessage().contains("community_contact_channels_type_check"));
        }
    }

    @Test
    void aPositionIsUniqueWithinACommunity_butNotAcrossCommunities() throws Exception {
        try (Connection connection = freshDatabase(POSTGRES, "position")) {
            applyUpToChangeSet(connection, BOUNDARY_CHANGESET_ID);
            applyRest(connection);
            UUID first = insertCommunity(connection, "A");
            UUID second = insertCommunity(connection, "B");

            insertChannel(connection, first, "PHONE", 0);
            insertChannel(connection, second, "PHONE", 0);

            SQLException refused = assertThrows(SQLException.class,
                    () -> insertChannel(connection, first, "EMAIL", 0));
            assertTrue(refused.getMessage().contains("community_contact_channels_community_id_position_uq"));
        }
    }

    @Test
    void aCommunityWithChannelsCannotBeDeleted() throws Exception {
        try (Connection connection = freshDatabase(POSTGRES, "fk")) {
            applyUpToChangeSet(connection, BOUNDARY_CHANGESET_ID);
            applyRest(connection);
            UUID community = insertCommunity(connection, "A");
            insertChannel(connection, community, "PHONE", 0);

            SQLException refused = assertThrows(SQLException.class,
                    () -> seed(connection, "DELETE FROM communities WHERE id = '" + community + "'"));
            assertTrue(refused.getMessage().contains("community_contact_channels_community_id_fk"));
        }
    }

    @Test
    void halts_whenTheTableAlreadyExists_andAppliesNothing() throws Exception {
        try (Connection connection = freshDatabase(POSTGRES, "halts_table")) {
            applyUpToChangeSet(connection, BOUNDARY_CHANGESET_ID);
            seed(connection, "CREATE TABLE community_contact_channels (id UUID PRIMARY KEY)");
            commit(connection);

            Exception failure = assertThrows(Exception.class, () -> applyRest(connection));

            rollback(connection);
            assertTrue(messageChainContains(failure, "Cannot create community_contact_channels"));
            assertFalse(changesetApplied(connection, TABLE_CHANGESET_ID));
            assertEquals("1", scalar(connection, "SELECT count(*) FROM information_schema.columns "
                    + "WHERE table_name = 'community_contact_channels'"));
        }
    }

    @Test
    void rollingBack_dropsTheTable_andCanBeAppliedAgain() throws Exception {
        try (Connection connection = freshDatabase(POSTGRES, "rollback")) {
            applyUpToChangeSet(connection, BOUNDARY_CHANGESET_ID);
            applyRest(connection);
            insertChannel(connection, insertCommunity(connection, "A"), "PHONE", 0);
            commit(connection);

            rollbackToChangeSet(connection, BOUNDARY_CHANGESET_ID);

            assertEquals("0", scalar(connection, "SELECT count(*) FROM information_schema.tables "
                    + "WHERE table_name = 'community_contact_channels'"));
            assertFalse(changesetApplied(connection, TABLE_CHANGESET_ID));

            applyRest(connection);

            assertTrue(changesetApplied(connection, TABLE_CHANGESET_ID));
            assertEquals("0", scalar(connection, "SELECT count(*) FROM community_contact_channels"));
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

    private static void insertChannel(Connection connection, UUID communityId, String type, int position)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO community_contact_channels (id, community_id, type, label, value, position) "
                        + "VALUES (?, ?, ?, 'Label', 'Value', ?)")) {
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, communityId);
            statement.setString(3, type);
            statement.setInt(4, position);
            statement.executeUpdate();
        }
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

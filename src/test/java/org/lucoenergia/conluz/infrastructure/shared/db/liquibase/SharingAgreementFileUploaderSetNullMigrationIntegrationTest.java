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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lucoenergia.conluz.infrastructure.shared.db.liquibase.LiquibaseTestSupport.applyRest;
import static org.lucoenergia.conluz.infrastructure.shared.db.liquibase.LiquibaseTestSupport.applyUpToChangeSet;
import static org.lucoenergia.conluz.infrastructure.shared.db.liquibase.LiquibaseTestSupport.freshDatabase;
import static org.lucoenergia.conluz.infrastructure.shared.db.liquibase.LiquibaseTestSupport.rollbackToChangeSet;

/**
 * The changeset that makes sharing_agreement_file.uploaded_by nullable and sets it to NULL when the uploader's
 * account is deleted (#412).
 */
class SharingAgreementFileUploaderSetNullMigrationIntegrationTest {

    /** The last changeset before the one under test. */
    private static final String BOUNDARY_CHANGESET_ID = "resync_single_membership_member_records_from_users";
    private static final String CHANGESET_ID = "set_null_sharing_agreement_file_uploaded_by_on_user_delete";

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withUsername("luz")
            .withPassword("blank");

    @BeforeAll
    static void startContainer() {
        POSTGRES.start();
    }

    @Test
    void deletingTheUploaderFailsBeforeTheChangeset() throws Exception {
        try (Connection connection = freshDatabase(POSTGRES, "before")) {
            applyUpToChangeSet(connection, BOUNDARY_CHANGESET_ID);
            UUID uploader = insertUser(connection, "11111111A");
            insertFile(connection, uploader);

            assertThrows(SQLException.class, () -> deleteUser(connection, uploader));
        }
    }

    @Test
    void theColumnBecomesNullable_existingUploadersAreUnchanged_andDeletingTheUploaderKeepsTheFile() throws Exception {
        try (Connection connection = freshDatabase(POSTGRES, "applies")) {
            applyUpToChangeSet(connection, BOUNDARY_CHANGESET_ID);
            assertFalse(uploadedByIsNullable(connection));
            UUID uploader = insertUser(connection, "11111111A");
            UUID otherUploader = insertUser(connection, "22222222B");
            UUID file = insertFile(connection, uploader);
            UUID otherFile = insertFile(connection, otherUploader);

            applyRest(connection);

            assertTrue(uploadedByIsNullable(connection));
            assertEquals(uploader, uploadedBy(connection, file));

            deleteUser(connection, uploader);

            assertNull(uploadedBy(connection, file));
            assertEquals(otherUploader, uploadedBy(connection, otherFile));
        }
    }

    @Test
    void rollingBack_restoresNotNullAndRestrict_whenEveryFileHasAnUploader_andItCanBeAppliedAgain() throws Exception {
        try (Connection connection = freshDatabase(POSTGRES, "rollback")) {
            applyUpToChangeSet(connection, BOUNDARY_CHANGESET_ID);
            UUID uploader = insertUser(connection, "11111111A");
            UUID file = insertFile(connection, uploader);
            applyRest(connection);
            commit(connection);

            rollbackToChangeSet(connection, BOUNDARY_CHANGESET_ID);

            assertFalse(changesetApplied(connection));
            assertFalse(uploadedByIsNullable(connection));
            assertEquals(uploader, uploadedBy(connection, file));
            assertThrows(SQLException.class, () -> deleteUser(connection, uploader));
            if (!connection.getAutoCommit()) {
                connection.rollback();
            }

            applyRest(connection);

            assertTrue(uploadedByIsNullable(connection));
            deleteUser(connection, uploader);
            assertNull(uploadedBy(connection, file));
        }
    }

    @Test
    void rollingBackHalts_listingTheFilesWithoutAnUploader_andRollsNothingBack() throws Exception {
        try (Connection connection = freshDatabase(POSTGRES, "halts")) {
            applyUpToChangeSet(connection, BOUNDARY_CHANGESET_ID);
            UUID uploader = insertUser(connection, "11111111A");
            UUID file = insertFile(connection, uploader);
            insertFile(connection, insertUser(connection, "22222222B"));
            applyRest(connection);
            deleteUser(connection, uploader);
            commit(connection);

            Exception failure = assertThrows(Exception.class,
                    () -> rollbackToChangeSet(connection, BOUNDARY_CHANGESET_ID));

            if (!connection.getAutoCommit()) {
                connection.rollback();
            }
            assertTrue(messageChainContains(failure, "some files have no uploader"),
                    "The failure should carry the rollback's message");
            assertTrue(messageChainContains(failure, file.toString()),
                    "The failure should list the files without an uploader");
            assertTrue(changesetApplied(connection));
            assertTrue(uploadedByIsNullable(connection));
            assertNull(uploadedBy(connection, file));
        }
    }

    // --- Helpers ---

    private static UUID insertUser(Connection connection, String personalId) throws SQLException {
        UUID id = UUID.randomUUID();
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO users (id, personal_id, full_name, enabled) VALUES (?, ?, 'Uploader', true)")) {
            statement.setObject(1, id);
            statement.setString(2, personalId);
            statement.executeUpdate();
        }
        return id;
    }

    /**
     * Inserts a file with its own community, supply, plant and agreement, the chain a file needs to exist.
     */
    private static UUID insertFile(Connection connection, UUID uploader) throws SQLException {
        UUID community = UUID.randomUUID();
        UUID supply = UUID.randomUUID();
        UUID plant = UUID.randomUUID();
        UUID agreement = UUID.randomUUID();
        UUID file = UUID.randomUUID();
        execute(connection, "INSERT INTO communities (id, name, code) VALUES (?, 'Community', ?)",
                community, community.toString());
        execute(connection, "INSERT INTO supplies (id, code, address, enabled, community_id) "
                + "VALUES (?, ?, 'Address', true, ?)", supply, supply.toString(), community);
        execute(connection, "INSERT INTO plants (id, name, provider_code, address, inverter_provider, total_power, "
                + "supply_id) VALUES (?, 'Plant', 'code', 'Address', 'HUAWEI', 10, ?)", plant, supply);
        execute(connection, "INSERT INTO sharing_agreement (id, plant_id, name, status, created_at) "
                + "VALUES (?, ?, 'Agreement', 'DRAFT', now())", agreement, plant);
        execute(connection, "INSERT INTO sharing_agreement_file (id, sharing_agreement_id, filename, content, "
                + "content_hash, uploaded_at, uploaded_by) VALUES (?, ?, 'file.txt', '\\x00', 'hash', now(), ?)",
                file, agreement, uploader);
        return file;
    }

    private static void deleteUser(Connection connection, UUID user) throws SQLException {
        execute(connection, "DELETE FROM users WHERE id = ?", user);
    }

    private static void execute(Connection connection, String sql, Object... parameters) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) {
                statement.setObject(i + 1, parameters[i]);
            }
            statement.executeUpdate();
        }
    }

    private static UUID uploadedBy(Connection connection, UUID file) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT uploaded_by FROM sharing_agreement_file WHERE id = ?")) {
            statement.setObject(1, file);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next(), "The file should still exist");
                return resultSet.getObject(1, UUID.class);
            }
        }
    }

    private static boolean uploadedByIsNullable(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT is_nullable FROM information_schema.columns "
                        + "WHERE table_name = 'sharing_agreement_file' AND column_name = 'uploaded_by'");
             ResultSet resultSet = statement.executeQuery()) {
            assertTrue(resultSet.next(), "sharing_agreement_file.uploaded_by should exist");
            return "YES".equals(resultSet.getString(1));
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

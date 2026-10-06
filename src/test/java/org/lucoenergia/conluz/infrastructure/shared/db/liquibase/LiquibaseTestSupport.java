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
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * Drives the real changelog through Liquibase's command API (not Spring Boot autoconfiguration), so a
 * migration test can stop at a given changeset, seed rows as they existed at that point, and then
 * apply the rest of the chain.
 */
final class LiquibaseTestSupport {

    static final String CHANGELOG_PATH = "db/liquibase/db.changelog-main.xml";

    private LiquibaseTestSupport() {
    }

    static int oneBasedCountUpTo(String changesetId) throws Exception {
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

    static void applyUpToChangeSet(Connection connection, String changesetId) throws Exception {
        int count = oneBasedCountUpTo(changesetId);
        new CommandScope(UpdateCountCommandStep.COMMAND_NAME)
                .addArgumentValue(DbUrlConnectionArgumentsCommandStep.DATABASE_ARG, toLiquibaseDatabase(connection))
                .addArgumentValue(UpdateCountCommandStep.CHANGELOG_FILE_ARG, CHANGELOG_PATH)
                .addArgumentValue(UpdateCountCommandStep.COUNT_ARG, count)
                .execute();
    }

    /**
     * Like {@link #applyUpToChangeSet}, on a database where some changesets are already applied: Liquibase's
     * update-count applies the next {@code count} changesets, not the changesets up to the {@code count}-th.
     */
    static void continueUpToChangeSet(Connection connection, String changesetId) throws Exception {
        // Liquibase leaves the connection out of auto-commit, and update-count discards an open transaction: commit
        // the rows seeded since the last command so they survive it
        if (!connection.getAutoCommit()) {
            connection.commit();
        }
        int count = oneBasedCountUpTo(changesetId) - appliedChangeSetCount(connection);
        new CommandScope(UpdateCountCommandStep.COMMAND_NAME)
                .addArgumentValue(DbUrlConnectionArgumentsCommandStep.DATABASE_ARG, toLiquibaseDatabase(connection))
                .addArgumentValue(UpdateCountCommandStep.CHANGELOG_FILE_ARG, CHANGELOG_PATH)
                .addArgumentValue(UpdateCountCommandStep.COUNT_ARG, count)
                .execute();
    }

    static void applyRest(Connection connection) throws Exception {
        new CommandScope(UpdateCommandStep.COMMAND_NAME)
                .addArgumentValue(DbUrlConnectionArgumentsCommandStep.DATABASE_ARG, toLiquibaseDatabase(connection))
                .addArgumentValue(UpdateCommandStep.CHANGELOG_FILE_ARG, CHANGELOG_PATH)
                .execute();
    }

    static void rollbackCount(Connection connection, int count) throws Exception {
        new CommandScope(RollbackCountCommandStep.COMMAND_NAME)
                .addArgumentValue(DbUrlConnectionArgumentsCommandStep.DATABASE_ARG, toLiquibaseDatabase(connection))
                .addArgumentValue(DatabaseChangelogCommandStep.CHANGELOG_FILE_ARG, CHANGELOG_PATH)
                .addArgumentValue(RollbackCountCommandStep.COUNT_ARG, count)
                .execute();
    }

    /**
     * Rolls back every applied changeset that comes after {@code changesetId}, however many were appended to the
     * changelog since, leaving the database as it was right after that changeset.
     */
    static void rollbackToChangeSet(Connection connection, String changesetId) throws Exception {
        rollbackCount(connection, appliedChangeSetCount(connection) - oneBasedCountUpTo(changesetId));
    }

    static void seed(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static int appliedChangeSetCount(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM databasechangelog")) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }

    /**
     * Creates a new, empty database on the given container and connects to it, so every scenario
     * starts from its own schema.
     */
    static Connection freshDatabase(PostgreSQLContainer<?> postgres, String databaseName) throws SQLException {
        try (Connection admin = DriverManager.getConnection(jdbcUrl(postgres, "postgres"), postgres.getUsername(), postgres.getPassword());
             Statement statement = admin.createStatement()) {
            statement.execute("CREATE DATABASE " + databaseName);
        }
        return DriverManager.getConnection(jdbcUrl(postgres, databaseName), postgres.getUsername(), postgres.getPassword());
    }

    private static String jdbcUrl(PostgreSQLContainer<?> postgres, String databaseName) {
        return "jdbc:postgresql://" + postgres.getHost() + ":" + postgres.getMappedPort(5432) + "/" + databaseName;
    }

    private static Database toLiquibaseDatabase(Connection connection) throws Exception {
        return DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
    }
}

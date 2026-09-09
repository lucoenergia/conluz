package org.lucoenergia.conluz.infrastructure.shared.error;

import org.postgresql.util.PSQLException;

/**
 * Walks an exception's cause chain to check whether a PSQLException carries a specific
 * constraint name, via the structured ServerErrorMessage field -- never by parsing
 * e.getMessage(), which is localised by the server's lc_messages and would break on a
 * differently configured server with no code change.
 */
public final class PostgresConstraintNameChecker {

    private PostgresConstraintNameChecker() {
    }

    public static boolean matches(Throwable exception, String constraintName) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof PSQLException psqlException
                    && psqlException.getServerErrorMessage() != null
                    && constraintName.equals(psqlException.getServerErrorMessage().getConstraint())) {
                return true;
            }
        }
        return false;
    }
}

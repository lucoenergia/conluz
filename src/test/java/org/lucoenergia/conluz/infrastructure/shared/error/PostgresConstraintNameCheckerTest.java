package org.lucoenergia.conluz.infrastructure.shared.error;

import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostgresConstraintNameCheckerTest {

    private PSQLException exceptionForConstraint(String constraintName) {
        ServerErrorMessage serverError = new ServerErrorMessage("Sfoo\0n" + constraintName + "\0");
        return new PSQLException(serverError);
    }

    @Test
    void matchesWhenThePsqlExceptionCarriesTheSameConstraintName() {
        PSQLException exception = exceptionForConstraint("no_overlapping_coefficients");

        assertTrue(PostgresConstraintNameChecker.matches(exception, "no_overlapping_coefficients"));
    }

    @Test
    void doesNotMatchADifferentConstraint() {
        PSQLException exception = exceptionForConstraint("some_other_constraint");

        assertFalse(PostgresConstraintNameChecker.matches(exception, "no_overlapping_coefficients"));
    }

    @Test
    void matchesThroughAWrappingCauseChain() {
        PSQLException psqlException = exceptionForConstraint("no_overlapping_coefficients");
        RuntimeException wrapper = new RuntimeException("wrapped", psqlException);

        assertTrue(PostgresConstraintNameChecker.matches(wrapper, "no_overlapping_coefficients"));
    }

    @Test
    void doesNotMatchWhenNoPsqlExceptionIsInTheChain() {
        assertFalse(PostgresConstraintNameChecker.matches(new RuntimeException("plain"), "no_overlapping_coefficients"));
    }
}

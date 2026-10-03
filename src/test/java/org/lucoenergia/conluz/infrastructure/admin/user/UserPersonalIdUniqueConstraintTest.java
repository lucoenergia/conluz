package org.lucoenergia.conluz.infrastructure.admin.user;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.user.UserAlreadyExistsException;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.springframework.dao.DataIntegrityViolationException;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

class UserPersonalIdUniqueConstraintTest {

    private DataIntegrityViolationException violationOf(String constraintName) {
        ServerErrorMessage serverError = new ServerErrorMessage("SERROR\0C23505\0n" + constraintName + "\0");
        return new DataIntegrityViolationException("could not execute statement", new PSQLException(serverError));
    }

    @Test
    void translatesAViolationOfThePersonalIdConstraintIntoUserAlreadyExists() {
        DataIntegrityViolationException violation = violationOf("users_personal_id_uq");

        RuntimeException translated = UserPersonalIdUniqueConstraint.translate(violation);

        assertInstanceOf(UserAlreadyExistsException.class, translated);
        assertSame(violation, translated.getCause());
    }

    @Test
    void returnsAViolationOfAnyOtherConstraintUnchanged() {
        DataIntegrityViolationException violation = violationOf("users_id_pk");

        assertSame(violation, UserPersonalIdUniqueConstraint.translate(violation));
    }

    @Test
    void returnsAViolationWithoutAConstraintNameUnchanged() {
        DataIntegrityViolationException violation = new DataIntegrityViolationException(
                "null value in column \"full_name\"", new PSQLException(new ServerErrorMessage("SERROR\0C23502\0")));

        assertSame(violation, UserPersonalIdUniqueConstraint.translate(violation));
    }
}

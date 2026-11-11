package org.lucoenergia.conluz.infrastructure.production.plant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.production.plant.PlantCilAlreadyInUseException;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.springframework.dao.DataIntegrityViolationException;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

class PlantCilUniqueConstraintTest {

    private DataIntegrityViolationException violationOf(String constraintName) {
        ServerErrorMessage serverError = new ServerErrorMessage("SERROR\0C23505\0n" + constraintName + "\0");
        return new DataIntegrityViolationException("could not execute statement", new PSQLException(serverError));
    }

    @Test
    @DisplayName("PLT-005 a violation of plants_cil_uq is translated into the CIL conflict")
    void translatesAViolationOfTheCilConstraintIntoTheCilConflict() {
        DataIntegrityViolationException violation = violationOf("plants_cil_uq");

        RuntimeException translated = PlantCilUniqueConstraint.translate(violation);

        assertInstanceOf(PlantCilAlreadyInUseException.class, translated);
        assertSame(violation, translated.getCause());
    }

    @Test
    @DisplayName("PLT-005 a violation of any other constraint is returned unchanged")
    void returnsAViolationOfAnyOtherConstraintUnchanged() {
        DataIntegrityViolationException violation = violationOf("plants_supply_id_uq");

        assertSame(violation, PlantCilUniqueConstraint.translate(violation));
    }

    @Test
    @DisplayName("PLT-005 a violation without a constraint name is returned unchanged")
    void returnsAViolationWithoutAConstraintNameUnchanged() {
        DataIntegrityViolationException violation = new DataIntegrityViolationException(
                "null value in column \"name\"", new PSQLException(new ServerErrorMessage("SERROR\0C23502\0")));

        assertSame(violation, PlantCilUniqueConstraint.translate(violation));
    }
}

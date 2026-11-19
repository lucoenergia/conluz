package org.lucoenergia.conluz.infrastructure.admin.supply.partitioncoefficient;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.production.sharingagreement.DuplicatePartitionCoefficientSupplyException;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

class SupplyPartitionCoefficientAgreementSupplyUniqueConstraintTest {

    private static final UUID AGREEMENT_ID = UUID.randomUUID();
    private static final UUID SUPPLY_ID = UUID.randomUUID();

    private DataIntegrityViolationException violationOf(String constraintName) {
        ServerErrorMessage serverError = new ServerErrorMessage("SERROR\0C23505\0n" + constraintName + "\0");
        return new DataIntegrityViolationException("could not execute statement", new PSQLException(serverError));
    }

    @Test
    @DisplayName("SHA-018 a violation of the agreement-supply constraint is translated into the duplicate-supply refusal")
    void translatesAViolationOfTheConstraintIntoTheDuplicateSupplyRefusal() {
        DataIntegrityViolationException violation =
                violationOf("supply_partition_coefficient_sharing_agreement_id_supply_id_uq");

        RuntimeException translated =
                SupplyPartitionCoefficientAgreementSupplyUniqueConstraint.translate(violation, AGREEMENT_ID, SUPPLY_ID);

        DuplicatePartitionCoefficientSupplyException duplicate =
                assertInstanceOf(DuplicatePartitionCoefficientSupplyException.class, translated);
        assertEquals(AGREEMENT_ID, duplicate.getSharingAgreementId());
        assertEquals(SUPPLY_ID, duplicate.getSupplyId());
        assertSame(violation, duplicate.getCause());
    }

    @Test
    @DisplayName("SHA-018 a violation of any other constraint is returned unchanged")
    void returnsAViolationOfAnyOtherConstraintUnchanged() {
        DataIntegrityViolationException violation = violationOf("no_overlapping_coefficients");

        assertSame(violation,
                SupplyPartitionCoefficientAgreementSupplyUniqueConstraint.translate(violation, AGREEMENT_ID, SUPPLY_ID));
    }

    @Test
    @DisplayName("SHA-018 a violation without a constraint name is returned unchanged")
    void returnsAViolationWithoutAConstraintNameUnchanged() {
        DataIntegrityViolationException violation = new DataIntegrityViolationException(
                "null value in column \"coefficient\"", new PSQLException(new ServerErrorMessage("SERROR\0C23502\0")));

        assertSame(violation,
                SupplyPartitionCoefficientAgreementSupplyUniqueConstraint.translate(violation, AGREEMENT_ID, SUPPLY_ID));
    }
}

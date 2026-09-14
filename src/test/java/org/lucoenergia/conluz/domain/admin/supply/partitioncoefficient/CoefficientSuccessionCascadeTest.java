package org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoefficientSuccessionCascadeTest {

    private static final Instant VALID_FROM = Instant.parse("2025-01-01T00:00:00Z");
    private static final Instant VALID_TO = Instant.parse("2025-06-01T00:00:00Z");

    @Test
    void notCascadeDerived_whenNoLaterActivatedRowExists() {
        SupplyPartitionCoefficient coefficient = build(VALID_FROM, VALID_TO);

        assertFalse(CoefficientSuccessionCascade.isValidToCascadeDerived(coefficient, Optional.empty()));
    }

    @Test
    void notCascadeDerived_whenLaterRowStartsAfterCurrentValidTo() {
        SupplyPartitionCoefficient coefficient = build(VALID_FROM, VALID_TO);
        SupplyPartitionCoefficient next = build(VALID_TO.plusSeconds(3600), null);

        assertFalse(CoefficientSuccessionCascade.isValidToCascadeDerived(coefficient, Optional.of(next)));
    }

    @Test
    void cascadeDerived_whenLaterRowStartsExactlyAtCurrentValidTo() {
        // This is exactly the shape CoefficientActivationServiceImpl.setValidFrom writes when
        // activating a successor: the predecessor's validTo is set to the successor's validFrom.
        SupplyPartitionCoefficient coefficient = build(VALID_FROM, VALID_TO);
        SupplyPartitionCoefficient next = build(VALID_TO, null);

        assertTrue(CoefficientSuccessionCascade.isValidToCascadeDerived(coefficient, Optional.of(next)));
    }

    /**
     * Documents the invariant relied on by callers: a coefficient can never have {@code validTo}
     * set while its own {@code validFrom} is still null. Explicit close ({@code setValidTo})
     * rejects any coefficient whose {@code validFrom} is null (COEFFICIENT_NOT_ACTIVE) before ever
     * writing a value; the activation cascade ({@code setValidFrom}) only ever writes a new
     * {@code validTo} onto a predecessor resolved via {@code findPredecessor}, whose backing
     * queries ({@code findOpenPredecessor}, {@code findPredecessorEndingAt}) both require
     * {@code validFrom IS NOT NULL} -- so a cascade-closed predecessor always already had its own
     * {@code validFrom} set. Reaching this state would therefore require a bug elsewhere, not a
     * legitimate input to this predicate.
     */
    @Test
    void validFromNullWithValidToSet_isStructurallyUnreachable_documentedNotHandled() {
        SupplyPartitionCoefficient coefficient = build(null, VALID_TO);
        SupplyPartitionCoefficient next = build(VALID_TO, null);

        // The predicate itself has no opinion on validFrom -- it only compares validTo to the next
        // row's validFrom -- so this still evaluates correctly even for this unreachable shape.
        assertTrue(CoefficientSuccessionCascade.isValidToCascadeDerived(coefficient, Optional.of(next)));
    }

    private static SupplyPartitionCoefficient build(Instant validFrom, Instant validTo) {
        return new SupplyPartitionCoefficient.Builder()
                .withId(UUID.randomUUID())
                .withSupplyId(UUID.randomUUID())
                .withPlantId(UUID.randomUUID())
                .withSharingAgreementId(UUID.randomUUID())
                .withCoefficient(BigDecimal.valueOf(0.5))
                .withValidFrom(validFrom)
                .withValidTo(validTo)
                .withCreatedAt(Instant.now())
                .build();
    }
}

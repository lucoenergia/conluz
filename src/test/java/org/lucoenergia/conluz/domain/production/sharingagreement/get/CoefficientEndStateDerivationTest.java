package org.lucoenergia.conluz.domain.production.sharingagreement.get;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.CoefficientEndState;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyPartitionCoefficient;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreement;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementStatus;
import org.lucoenergia.conluz.domain.production.sharingagreement.get.CoefficientEndStateDerivation.EndOfCoverage;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Every agreement here is created in an order that differs from the order of its periods: creation order must
 * decide nothing.
 */
class CoefficientEndStateDerivationTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Madrid");

    private static final Instant Y2023 = Instant.parse("2022-12-31T23:00:00Z");
    private static final Instant Y2024 = Instant.parse("2023-12-31T23:00:00Z");
    private static final Instant Y2025 = Instant.parse("2024-12-31T23:00:00Z");
    private static final Instant Y2026 = Instant.parse("2025-12-31T23:00:00Z");

    private final UUID supplyX = UUID.randomUUID();
    private final UUID supplyY = UUID.randomUUID();
    private final List<SharingAgreement> agreements = new ArrayList<>();
    private final List<SupplyPartitionCoefficient> coefficients = new ArrayList<>();
    private int created;

    private SharingAgreement agreement(SharingAgreementStatus status) {
        return agreement(status, null);
    }

    // Each call is created one day after the previous one.
    private SharingAgreement agreement(SharingAgreementStatus status, LocalDate distributorFileSentOn) {
        SharingAgreement agreement = new SharingAgreement.Builder()
                .withId(UUID.randomUUID())
                .withPlantId(UUID.randomUUID())
                .withStatus(status)
                .withDistributorFileSentOn(distributorFileSentOn)
                .withCreatedAt(Instant.parse("2026-01-01T00:00:00Z").plusSeconds(86_400L * created++))
                .build();
        agreements.add(agreement);
        return agreement;
    }

    private SupplyPartitionCoefficient row(SharingAgreement agreement, UUID supplyId, Instant validFrom,
                                           Instant validTo) {
        SupplyPartitionCoefficient row = new SupplyPartitionCoefficient.Builder()
                .withId(UUID.randomUUID())
                .withSupplyId(supplyId)
                .withSharingAgreementId(agreement.getId())
                .withCoefficient(BigDecimal.valueOf(0.5))
                .withValidFrom(validFrom)
                .withValidTo(validTo)
                .withCreatedAt(Instant.EPOCH)
                .build();
        coefficients.add(row);
        return row;
    }

    private EndOfCoverage derive(SharingAgreement agreement, SupplyPartitionCoefficient row) {
        return new CoefficientEndStateDerivation(agreement, agreements, coefficients, ZONE).derive(row);
    }

    private static void assertEnd(CoefficientEndState state, Instant endDate, EndOfCoverage actual) {
        assertEquals(new EndOfCoverage(state, endDate), actual);
    }

    // --- Applied coefficients (SHA-016) ---

    @Test
    @DisplayName("SHA-016 an applied open coefficient with no other agreement is OPEN")
    void appliedWithNoOtherAgreementIsOpen() {
        SharingAgreement current = agreement(SharingAgreementStatus.PUBLISHED);
        SupplyPartitionCoefficient row = row(current, supplyX, Y2025, null);

        assertEnd(CoefficientEndState.OPEN, null, derive(current, row));
    }

    @Test
    @DisplayName("SHA-016 an applied open coefficient stays OPEN when a later-created historical agreement lacks its supply")
    void appliedStaysOpenWhenLaterCreatedHistoricalAgreementLacksItsSupply() {
        SharingAgreement current = agreement(SharingAgreementStatus.PUBLISHED);
        SupplyPartitionCoefficient row = row(current, supplyX, Y2025, null);
        SharingAgreement historical = agreement(SharingAgreementStatus.SUPERSEDED);
        row(historical, supplyY, Y2023, Y2024);

        assertEnd(CoefficientEndState.OPEN, null, derive(current, row));
    }

    @Test
    @DisplayName("SHA-016 an applied open coefficient is OPEN_ORPHAN when an agreement starting after it lacks its supply, even one created before it")
    void appliedIsOrphanWhenAnAgreementStartingAfterItLacksItsSupply() {
        SharingAgreement newer = agreement(SharingAgreementStatus.PUBLISHED);
        row(newer, supplyY, Y2025, null);
        SharingAgreement older = agreement(SharingAgreementStatus.SUPERSEDED);
        SupplyPartitionCoefficient row = row(older, supplyX, Y2023, null);

        assertEnd(CoefficientEndState.OPEN_ORPHAN, null, derive(older, row));
    }

    @Test
    @DisplayName("SHA-016 an applied open coefficient is PENDING_SUCCESSION when any agreement after it in registered order holds a pending row, not only the nearest created one")
    void appliedIsPendingSuccessionBehindAHistoricalLaterCreatedAgreement() {
        SharingAgreement current = agreement(SharingAgreementStatus.PUBLISHED);
        SupplyPartitionCoefficient row = row(current, supplyY, Y2025, null);
        SharingAgreement historical = agreement(SharingAgreementStatus.SUPERSEDED);
        row(historical, supplyY, Y2023, Y2025);
        SharingAgreement next = agreement(SharingAgreementStatus.PUBLISHED, LocalDate.of(2026, 1, 15));
        row(next, supplyY, null, null);

        assertEnd(CoefficientEndState.PENDING_SUCCESSION, null, derive(current, row));
    }

    @Test
    @DisplayName("SHA-016 a pending row in an agreement registered before an applied coefficient does not succeed it")
    void appliedIgnoresPendingRowOfAnAgreementRegisteredBeforeIt() {
        SharingAgreement current = agreement(SharingAgreementStatus.PUBLISHED);
        SupplyPartitionCoefficient row = row(current, supplyY, Y2025, null);
        SharingAgreement earlier = agreement(SharingAgreementStatus.PUBLISHED, LocalDate.of(2024, 6, 1));
        row(earlier, supplyY, null, null);

        assertEnd(CoefficientEndState.OPEN, null, derive(current, row));
    }

    @Test
    @DisplayName("SHA-016 an undated pending agreement registers last, so it succeeds an applied coefficient until its sent date places it before")
    void undatedPendingAgreementRegistersLast() {
        SharingAgreement current = agreement(SharingAgreementStatus.PUBLISHED);
        SupplyPartitionCoefficient row = row(current, supplyY, Y2025, null);
        SharingAgreement historical = agreement(SharingAgreementStatus.PUBLISHED);
        row(historical, supplyY, null, null);

        assertEnd(CoefficientEndState.PENDING_SUCCESSION, null, derive(current, row));

        agreements.set(1, new SharingAgreement.Builder()
                .withId(historical.getId())
                .withPlantId(historical.getPlantId())
                .withStatus(historical.getStatus())
                .withDistributorFileSentOn(LocalDate.of(2024, 6, 1))
                .withCreatedAt(historical.getCreatedAt())
                .build());

        assertEnd(CoefficientEndState.OPEN, null, derive(current, row));
    }

    @Test
    @DisplayName("SHA-016 OPEN_ORPHAN takes precedence over a pending successor")
    void orphanTakesPrecedenceOverPendingSuccessor() {
        SharingAgreement current = agreement(SharingAgreementStatus.PUBLISHED);
        row(current, supplyX, Y2025, null);
        row(current, supplyY, null, null);
        SharingAgreement a2023 = agreement(SharingAgreementStatus.PUBLISHED);
        row(a2023, supplyX, Y2023, Y2024);
        SupplyPartitionCoefficient row = row(a2023, supplyY, Y2023, null);
        SharingAgreement b2024 = agreement(SharingAgreementStatus.SUPERSEDED);
        row(b2024, supplyX, Y2024, Y2025);

        assertEnd(CoefficientEndState.OPEN_ORPHAN, null, derive(a2023, row));
    }

    @Test
    @DisplayName("SHA-016 an activated row of the same supply starting after an applied open coefficient ends it (DERIVED)")
    void appliedIsDerivedByAnActivatedRowStartingAfterIt() {
        SharingAgreement next = agreement(SharingAgreementStatus.PUBLISHED);
        row(next, supplyX, Y2026, null);
        SharingAgreement current = agreement(SharingAgreementStatus.PUBLISHED);
        SupplyPartitionCoefficient row = row(current, supplyX, Y2025, null);

        assertEnd(CoefficientEndState.DERIVED, Y2026, derive(current, row));
    }

    @Test
    @DisplayName("SHA-016 a DRAFT agreement neither orphans nor succeeds an applied coefficient")
    void draftAgreementsDoNotCount() {
        SharingAgreement current = agreement(SharingAgreementStatus.PUBLISHED);
        SupplyPartitionCoefficient row = row(current, supplyX, Y2025, null);
        SharingAgreement draft = agreement(SharingAgreementStatus.DRAFT);
        row(draft, supplyX, null, null);
        row(draft, supplyY, null, null);

        assertEnd(CoefficientEndState.OPEN, null, derive(current, row));
    }

    @Test
    @DisplayName("SHA-008 an applied open coefficient is not ended by a row of another agreement that starts before it")
    void appliedIsNotEndedByARowStartingBeforeIt() {
        SharingAgreement current = agreement(SharingAgreementStatus.PUBLISHED);
        SupplyPartitionCoefficient row = row(current, supplyX, Y2025, null);
        SharingAgreement historical = agreement(SharingAgreementStatus.SUPERSEDED);
        row(historical, supplyX, Y2023, Y2025);

        assertEnd(CoefficientEndState.OPEN, null, derive(current, row));
    }

    // --- Pending coefficients (SHA-017) ---

    @Test
    @DisplayName("SHA-017 a pending coefficient of the last agreement in registered order is OPEN, even when agreements were created after it")
    void pendingOfTheLastRegisteredAgreementIsOpen() {
        SharingAgreement current = agreement(SharingAgreementStatus.PUBLISHED);
        SupplyPartitionCoefficient row = row(current, supplyX, null, null);
        SharingAgreement historical = agreement(SharingAgreementStatus.SUPERSEDED, LocalDate.of(2023, 1, 1));
        row(historical, supplyY, Y2023, Y2024);

        assertEnd(CoefficientEndState.OPEN, null, derive(current, row));
    }

    @Test
    @DisplayName("SHA-017 a pending coefficient is OPEN_ORPHAN when the next agreement in registered order lacks its supply")
    void pendingIsOrphanWhenNextRegisteredAgreementLacksItsSupply() {
        SharingAgreement last = agreement(SharingAgreementStatus.PUBLISHED, LocalDate.of(2025, 1, 1));
        row(last, supplyX, null, null);
        SharingAgreement pending = agreement(SharingAgreementStatus.PUBLISHED, LocalDate.of(2023, 1, 1));
        SupplyPartitionCoefficient row = row(pending, supplyX, null, null);
        SharingAgreement next = agreement(SharingAgreementStatus.PUBLISHED, LocalDate.of(2024, 1, 1));
        row(next, supplyY, null, null);

        assertEnd(CoefficientEndState.OPEN_ORPHAN, null, derive(pending, row));
    }

    @Test
    @DisplayName("SHA-017 a pending coefficient is PENDING_SUCCESSION when the next agreement in registered order holds a pending row of its supply")
    void pendingIsPendingSuccessionWhenNextRegisteredAgreementHoldsAPendingRow() {
        SharingAgreement next = agreement(SharingAgreementStatus.PUBLISHED, LocalDate.of(2024, 1, 1));
        row(next, supplyX, null, null);
        SharingAgreement pending = agreement(SharingAgreementStatus.PUBLISHED, LocalDate.of(2023, 1, 1));
        SupplyPartitionCoefficient row = row(pending, supplyX, null, null);

        assertEnd(CoefficientEndState.PENDING_SUCCESSION, null, derive(pending, row));
    }

    @Test
    @DisplayName("SHA-017 a pending coefficient is DERIVED at the start of the next registered agreement's activated row")
    void pendingIsDerivedByTheNextRegisteredAgreementsActivatedRow() {
        SharingAgreement next = agreement(SharingAgreementStatus.PUBLISHED);
        row(next, supplyX, Y2025, null);
        SharingAgreement pending = agreement(SharingAgreementStatus.PUBLISHED, LocalDate.of(2024, 1, 1));
        SupplyPartitionCoefficient row = row(pending, supplyX, null, null);

        assertEnd(CoefficientEndState.DERIVED, Y2025, derive(pending, row));
    }

    @Test
    @DisplayName("SHA-017 a pending coefficient of a DRAFT agreement registers after the dated agreements")
    void pendingOfADraftRegistersAfterTheDatedAgreements() {
        SharingAgreement published = agreement(SharingAgreementStatus.PUBLISHED);
        row(published, supplyY, Y2025, null);
        SharingAgreement draft = agreement(SharingAgreementStatus.DRAFT);
        SupplyPartitionCoefficient row = row(draft, supplyX, null, null);

        assertEnd(CoefficientEndState.OPEN, null, derive(draft, row));
    }

    // --- Stored validTo ---

    @Test
    void storedValidToIsClosedWhenAuthored() {
        SharingAgreement current = agreement(SharingAgreementStatus.SUPERSEDED);
        Instant authored = Instant.parse("2025-06-01T00:00:00Z");
        SupplyPartitionCoefficient row = row(current, supplyX, Y2025, authored);

        assertEnd(CoefficientEndState.CLOSED, authored, derive(current, row));
    }

    @Test
    void storedValidToIsDerivedWhenTheNextActivatedRowStartsThere() {
        SharingAgreement next = agreement(SharingAgreementStatus.PUBLISHED);
        row(next, supplyX, Y2026, null);
        SharingAgreement current = agreement(SharingAgreementStatus.SUPERSEDED);
        SupplyPartitionCoefficient row = row(current, supplyX, Y2025, Y2026);

        assertEnd(CoefficientEndState.DERIVED, Y2026, derive(current, row));
    }
}

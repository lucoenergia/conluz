package org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LeavingSupplyClosureTest {

    private static final UUID PLANT_ID = UUID.randomUUID();
    private static final UUID AGREEMENT_A = UUID.randomUUID();
    private static final UUID AGREEMENT_B = UUID.randomUUID();
    private static final UUID SUPPLY_X = UUID.randomUUID();
    private static final UUID SUPPLY_Y = UUID.randomUUID();
    private static final UUID SUPPLY_Z = UUID.randomUUID();
    private static final Instant D = day("2025-06-01");

    private static Instant day(String date) {
        return Instant.parse(date + "T00:00:00Z");
    }

    private static SupplyPartitionCoefficient row(UUID agreementId, UUID supplyId, String validFrom, String validTo) {
        return new SupplyPartitionCoefficient.Builder()
                .withId(UUID.randomUUID())
                .withSupplyId(supplyId)
                .withPlantId(PLANT_ID)
                .withSharingAgreementId(agreementId)
                .withCoefficient(BigDecimal.ONE)
                .withValidFrom(validFrom == null ? null : day(validFrom))
                .withValidTo(validTo == null ? null : day(validTo))
                .withCreatedAt(Instant.EPOCH)
                .build();
    }

    private record Period(UUID id, Instant validFrom, Instant validTo) {
        static Period of(SupplyPartitionCoefficient row) {
            return new Period(row.getId(), row.getValidFrom(), row.getValidTo());
        }
    }

    private static Period period(SupplyPartitionCoefficient row, String validFrom, String validTo) {
        return new Period(row.getId(), validFrom == null ? null : day(validFrom), validTo == null ? null : day(validTo));
    }

    private static List<Period> close(List<SupplyPartitionCoefficient> timeline) {
        return LeavingSupplyClosure.close(timeline, AGREEMENT_B, D).stream().map(Period::of).toList();
    }

    @Test
    @DisplayName("SHA-009 the first activation closes at D the open coefficient of every supply absent from the agreement")
    void closesEveryOpenCoefficientOfASupplyAbsentFromTheAgreement() {
        SupplyPartitionCoefficient xInA = row(AGREEMENT_A, SUPPLY_X, "2025-01-01", null);
        SupplyPartitionCoefficient yInA = row(AGREEMENT_A, SUPPLY_Y, "2025-01-01", null);
        SupplyPartitionCoefficient zInA = row(AGREEMENT_A, SUPPLY_Z, "2025-01-01", null);
        SupplyPartitionCoefficient xInB = row(AGREEMENT_B, SUPPLY_X, null, null);

        assertThat(close(List.of(xInA, yInA, zInA, xInB))).containsExactlyInAnyOrder(
                period(yInA, "2025-01-01", "2025-06-01"),
                period(zInA, "2025-01-01", "2025-06-01"));
    }

    @Test
    @DisplayName("SHA-009 a supply holding a pending coefficient of the agreement is not leaving")
    void aSupplyWithAPendingCoefficientInTheAgreementIsNotLeaving() {
        SupplyPartitionCoefficient yInA = row(AGREEMENT_A, SUPPLY_Y, "2025-01-01", null);
        SupplyPartitionCoefficient yInB = row(AGREEMENT_B, SUPPLY_Y, null, null);

        assertThat(close(List.of(yInA, yInB))).isEmpty();
    }

    @Test
    @DisplayName("SHA-009 nothing is closed once a coefficient of the agreement has a validFrom")
    void nothingIsClosedWhenTheActivationIsNotTheFirst() {
        SupplyPartitionCoefficient xInB = row(AGREEMENT_B, SUPPLY_X, "2025-03-01", null);
        SupplyPartitionCoefficient zInB = row(AGREEMENT_B, SUPPLY_Z, null, null);
        SupplyPartitionCoefficient yInA = row(AGREEMENT_A, SUPPLY_Y, "2025-01-01", null);

        assertThat(close(List.of(xInB, zInB, yInA))).isEmpty();
    }

    @Test
    @DisplayName("SHA-009 a pending coefficient of a leaving supply is not in force and is not closed")
    void aPendingCoefficientIsNotClosed() {
        SupplyPartitionCoefficient yPendingInA = row(AGREEMENT_A, SUPPLY_Y, null, null);
        SupplyPartitionCoefficient xInB = row(AGREEMENT_B, SUPPLY_X, null, null);

        assertThat(close(List.of(yPendingInA, xInB))).isEmpty();
    }

    @Test
    @DisplayName("SHA-010 a coefficient that ended on or before D is not touched")
    void aCoefficientEndedOnOrBeforeDIsNotTouched() {
        SupplyPartitionCoefficient yEndedBefore = row(AGREEMENT_A, SUPPLY_Y, "2025-01-01", "2025-05-01");
        SupplyPartitionCoefficient zEndedOnD = row(AGREEMENT_A, SUPPLY_Z, "2025-01-01", "2025-06-01");
        SupplyPartitionCoefficient xInB = row(AGREEMENT_B, SUPPLY_X, null, null);

        assertThat(close(List.of(yEndedBefore, zEndedOnD, xInB))).isEmpty();
    }

    @Test
    @DisplayName("SHA-010 a coefficient closed after D is shortened to D")
    void aCoefficientClosedAfterDIsShortenedToD() {
        SupplyPartitionCoefficient yClosedAfter = row(AGREEMENT_A, SUPPLY_Y, "2025-01-01", "2025-09-01");
        SupplyPartitionCoefficient xInB = row(AGREEMENT_B, SUPPLY_X, null, null);

        assertThat(close(List.of(yClosedAfter, xInB))).containsExactly(period(yClosedAfter, "2025-01-01", "2025-06-01"));
    }

    @Test
    @DisplayName("SHA-010 a coefficient starting on or after D is not touched; only the one in force at D is closed")
    void aCoefficientStartingOnOrAfterDIsNotTouched() {
        SupplyPartitionCoefficient yBefore = row(AGREEMENT_A, SUPPLY_Y, "2025-01-01", "2025-08-01");
        SupplyPartitionCoefficient yAfter = row(UUID.randomUUID(), SUPPLY_Y, "2025-08-01", null);
        SupplyPartitionCoefficient zOnD = row(AGREEMENT_A, SUPPLY_Z, "2025-06-01", null);
        SupplyPartitionCoefficient xInB = row(AGREEMENT_B, SUPPLY_X, null, null);

        assertThat(close(List.of(yBefore, yAfter, zOnD, xInB))).containsExactly(
                period(yBefore, "2025-01-01", "2025-06-01"));
    }
}

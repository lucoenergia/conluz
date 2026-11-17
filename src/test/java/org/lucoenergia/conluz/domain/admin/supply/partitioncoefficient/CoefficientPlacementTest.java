package org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CoefficientPlacementTest {

    private static final UUID SUPPLY_ID = UUID.randomUUID();
    private static final UUID PLANT_ID = UUID.randomUUID();

    private static Instant day(String date) {
        return Instant.parse(date + "T00:00:00Z");
    }

    private static SupplyPartitionCoefficient row(String validFrom, String validTo) {
        return new SupplyPartitionCoefficient.Builder()
                .withId(UUID.randomUUID())
                .withSupplyId(SUPPLY_ID)
                .withPlantId(PLANT_ID)
                .withSharingAgreementId(UUID.randomUUID())
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

    private static List<Period> writesOf(CoefficientPlacement.Result result) {
        assertThat(result).isInstanceOf(CoefficientPlacement.Placed.class);
        return ((CoefficientPlacement.Placed) result).writes().stream().map(Period::of).toList();
    }

    @Test
    @DisplayName("SHA-003 a coefficient activated before an open one ends where the open one starts, which is unchanged")
    void olderUnderAnOpenNewerOne() {
        SupplyPartitionCoefficient open = row("2024-05-23", null);
        SupplyPartitionCoefficient pending = row(null, null);

        var result = CoefficientPlacement.place(List.of(open, pending), pending, day("2023-04-12"));

        assertThat(writesOf(result)).containsExactly(period(pending, "2023-04-12", "2024-05-23"));
    }

    @Test
    @DisplayName("SHA-003 a coefficient activated between two ends the earlier one and ends where the later one starts")
    void betweenTwo() {
        SupplyPartitionCoefficient earlier = row("2023-04-12", "2024-05-23");
        SupplyPartitionCoefficient later = row("2024-05-23", null);
        SupplyPartitionCoefficient pending = row(null, null);

        var result = CoefficientPlacement.place(List.of(later, pending, earlier), pending, day("2024-02-01"));

        assertThat(writesOf(result)).containsExactly(
                period(pending, "2024-02-01", "2024-05-23"),
                period(earlier, "2023-04-12", "2024-02-01"));
    }

    @Test
    @DisplayName("SHA-003 a coefficient activated after an open one ends it and stays open")
    void afterAnOpenOne() {
        SupplyPartitionCoefficient older = row("2022-01-01", "2023-01-01");
        SupplyPartitionCoefficient open = row("2023-01-01", null);
        SupplyPartitionCoefficient pending = row(null, null);

        var result = CoefficientPlacement.place(List.of(older, open, pending), pending, day("2024-03-01"));

        assertThat(writesOf(result)).containsExactly(
                period(pending, "2024-03-01", null),
                period(open, "2023-01-01", "2024-03-01"));
    }

    @Test
    @DisplayName("SHA-003 a close an author set on or before the new start is kept, and the gap stays")
    void authoredCloseKept() {
        SupplyPartitionCoefficient closed = row("2023-01-01", "2024-01-01");
        SupplyPartitionCoefficient pending = row(null, null);

        var result = CoefficientPlacement.place(List.of(closed, pending), pending, day("2024-03-01"));

        assertThat(writesOf(result)).containsExactly(period(pending, "2024-03-01", null));
    }

    @Test
    @DisplayName("SHA-003 a close an author set after the new start is moved back to it")
    void authoredCloseShortened() {
        SupplyPartitionCoefficient closed = row("2023-01-01", "2024-06-01");
        SupplyPartitionCoefficient pending = row(null, null);

        var result = CoefficientPlacement.place(List.of(closed, pending), pending, day("2024-03-01"));

        assertThat(writesOf(result)).containsExactly(
                period(pending, "2024-03-01", null),
                period(closed, "2023-01-01", "2024-03-01"));
    }

    @Test
    @DisplayName("SHA-003 a pending row of the timeline is not a neighbour")
    void pendingRowsAreIgnored() {
        SupplyPartitionCoefficient open = row("2024-01-01", null);
        SupplyPartitionCoefficient otherPending = row(null, null);
        SupplyPartitionCoefficient pending = row(null, null);

        var result = CoefficientPlacement.place(List.of(otherPending, open, pending), pending, day("2024-03-01"));

        assertThat(writesOf(result)).containsExactly(
                period(pending, "2024-03-01", null),
                period(open, "2024-01-01", "2024-03-01"));
    }

    @Test
    @DisplayName("SHA-004 a correction moving a coefficient later past its successor joins its old neighbours and ends the successor at it")
    void correctionMovingLater() {
        SupplyPartitionCoefficient first = row("2023-01-01", "2023-06-01");
        SupplyPartitionCoefficient moved = row("2023-06-01", "2024-01-01");
        SupplyPartitionCoefficient last = row("2024-01-01", null);

        var result = CoefficientPlacement.place(List.of(first, moved, last), moved, day("2024-03-01"));

        assertThat(writesOf(result)).containsExactlyInAnyOrder(
                period(moved, "2024-03-01", null),
                period(first, "2023-01-01", "2024-01-01"),
                period(last, "2024-01-01", "2024-03-01"));
        assertThat(writesOf(result).get(0)).isEqualTo(period(moved, "2024-03-01", null));
    }

    @Test
    @DisplayName("SHA-004 a correction moving a coefficient earlier past its predecessor joins its old neighbours and ends at the predecessor")
    void correctionMovingEarlier() {
        SupplyPartitionCoefficient first = row("2023-01-01", "2023-06-01");
        SupplyPartitionCoefficient moved = row("2023-06-01", "2024-01-01");
        SupplyPartitionCoefficient last = row("2024-01-01", null);

        var result = CoefficientPlacement.place(List.of(first, moved, last), moved, day("2022-06-01"));

        assertThat(writesOf(result)).containsExactly(
                period(moved, "2022-06-01", "2023-01-01"),
                period(first, "2023-01-01", "2024-01-01"));
    }

    @Test
    @DisplayName("SHA-004 a correction within its own period moves the predecessor's end with it")
    void correctionWithinItsOwnPeriod() {
        SupplyPartitionCoefficient first = row("2023-01-01", "2023-06-01");
        SupplyPartitionCoefficient moved = row("2023-06-01", null);

        var result = CoefficientPlacement.place(List.of(first, moved), moved, day("2023-09-01"));

        assertThat(writesOf(result)).containsExactly(
                period(moved, "2023-09-01", null),
                period(first, "2023-01-01", "2023-09-01"));
    }

    @Test
    @DisplayName("SHA-004 a correction keeps the coefficient's own authored close")
    void correctionKeepsOwnAuthoredClose() {
        SupplyPartitionCoefficient closed = row("2023-01-01", "2023-06-01");

        var result = CoefficientPlacement.place(List.of(closed), closed, day("2023-03-01"));

        assertThat(writesOf(result)).containsExactly(period(closed, "2023-03-01", "2023-06-01"));
    }

    @Test
    @DisplayName("SHA-004 a correction shortens the coefficient's own authored close to the next start when that comes earlier")
    void correctionCapsOwnAuthoredCloseAtTheNextStart() {
        SupplyPartitionCoefficient closed = row("2023-06-01", "2023-12-01");
        SupplyPartitionCoefficient earlier = row("2023-01-01", "2023-03-01");

        var result = CoefficientPlacement.place(List.of(earlier, closed), closed, day("2022-06-01"));

        assertThat(writesOf(result)).containsExactly(period(closed, "2022-06-01", "2023-01-01"));
    }

    @Test
    @DisplayName("SHA-004 a correction to a date on or after the coefficient's own authored close is refused")
    void correctionAtOrAfterOwnAuthoredCloseIsRefused() {
        SupplyPartitionCoefficient closed = row("2023-01-01", "2023-06-01");
        SupplyPartitionCoefficient later = row("2024-01-01", null);

        assertThat(CoefficientPlacement.place(List.of(closed, later), closed, day("2023-06-01")))
                .isInstanceOf(CoefficientPlacement.NotBeforeOwnClose.class);
        assertThat(CoefficientPlacement.place(List.of(closed, later), closed, day("2023-08-01")))
                .isInstanceOf(CoefficientPlacement.NotBeforeOwnClose.class);
    }

    @Test
    @DisplayName("SHA-004 a correction does not join a predecessor separated from the coefficient by an authored gap")
    void correctionKeepsAnAuthoredGapBefore() {
        SupplyPartitionCoefficient closed = row("2023-01-01", "2023-03-01");
        SupplyPartitionCoefficient moved = row("2023-06-01", null);

        var result = CoefficientPlacement.place(List.of(closed, moved), moved, day("2023-09-01"));

        assertThat(writesOf(result)).containsExactly(period(moved, "2023-09-01", null));
    }

    @Test
    @DisplayName("SHA-004 an authored close equal to the next start counts as cascade-derived, so a correction replaces it")
    void authoredCloseEqualToTheNextStartCountsAsCascadeDerived() {
        SupplyPartitionCoefficient closedAtNext = row("2023-01-01", "2023-06-01");
        SupplyPartitionCoefficient next = row("2023-06-01", "2023-09-01");

        var result = CoefficientPlacement.place(List.of(closedAtNext, next), closedAtNext, day("2023-07-01"));

        assertThat(writesOf(result)).containsExactly(
                period(closedAtNext, "2023-07-01", null),
                period(next, "2023-06-01", "2023-07-01"));
    }

    @Test
    @DisplayName("SHA-004 a predecessor's authored close equal to the moved coefficient's start counts as cascade-derived, so the predecessor is extended to the new start")
    void predecessorCloseEqualToTheMovedStartCountsAsCascadeDerived() {
        SupplyPartitionCoefficient closedAtNext = row("2023-01-01", "2023-06-01");
        SupplyPartitionCoefficient moved = row("2023-06-01", null);

        var result = CoefficientPlacement.place(List.of(closedAtNext, moved), moved, day("2023-09-01"));

        assertThat(writesOf(result)).containsExactly(
                period(moved, "2023-09-01", null),
                period(closedAtNext, "2023-01-01", "2023-09-01"));
    }

    @Test
    @DisplayName("SHA-005 a start equal to another coefficient's start is refused, naming that coefficient")
    void equalStartIsRefused() {
        SupplyPartitionCoefficient first = row("2023-01-01", "2024-01-01");
        SupplyPartitionCoefficient second = row("2024-01-01", null);
        SupplyPartitionCoefficient pending = row(null, null);

        var result = CoefficientPlacement.place(List.of(first, second, pending), pending, day("2024-01-01"));

        assertThat(result).isEqualTo(new CoefficientPlacement.StartTaken(second));
    }
}

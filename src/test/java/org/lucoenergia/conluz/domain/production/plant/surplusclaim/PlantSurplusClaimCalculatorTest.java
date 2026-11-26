package org.lucoenergia.conluz.domain.production.plant.surplusclaim;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.PlantReference;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyPartitionCoefficient;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyReference;
import org.lucoenergia.conluz.domain.consumption.datadis.metrics.DailyPublishedAggregate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The claim built from figures already read, in Europe/Madrid. Unless a test says otherwise the month is June 2026,
 * the plant's nominal power is 60 kW, and two supplies are in scope with coefficients of 0.6 and 0.4 for the whole
 * month, each with a surplus record for every hour.
 */
class PlantSurplusClaimCalculatorTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final YearMonth JUNE = YearMonth.of(2026, 6);
    private static final double TOLERANCE = 1e-9;

    private final UUID plantId = UUID.randomUUID();
    private final UUID plantSupplyId = UUID.randomUUID();
    // Created in reverse order of their codes, so that ordering by code is not the order they are given in.
    private final SupplyReference supplyB = new SupplyReference(UUID.randomUUID(), "ES0000000000000000BB", "B");
    private final SupplyReference supplyA = new SupplyReference(UUID.randomUUID(), "ES0000000000000000AA", "A");

    @Test
    @DisplayName("SCL-002 AC4 an hour with a record carrying 0 is 0, and an hour without any record is null")
    void anHourWithAZeroRecordIsZeroAndAnHourWithoutAnyRecordIsNull() {
        Map<Instant, Double> surplus = new HashMap<>();
        surplus.put(at(2026, 6, 10, 10), 0d);
        surplus.put(at(2026, 6, 10, 12), 5d);

        PlantSurplusClaim claim = calculate(input(JUNE).surplus(surplus));

        SurplusClaimDay day = day(claim, LocalDate.of(2026, 6, 10));
        assertEquals(0d, hour(day, 10).surplusKWh());
        assertNull(hour(day, 11).surplusKWh());
        assertEquals(5d, hour(day, 12).surplusKWh());
        assertEquals(5d, day.surplusKWh(), TOLERANCE);
    }

    @Test
    @DisplayName("SCL-003 AC5 a day one supply misses an hour of is ESTIMATED, the complete days REAL, and SCL-009 the supply has gaps on that day")
    void aDayOneSupplyMissesAnHourOfIsEstimated() {
        PlantSurplusClaim claim = calculate(input(JUNE)
                .recordedHours(supplyA.code(), LocalDate.of(2026, 6, 10), 23));

        assertEquals(SurplusClaimDayState.ESTIMATED, day(claim, LocalDate.of(2026, 6, 10)).state());
        assertEquals(SurplusClaimDayState.REAL, day(claim, LocalDate.of(2026, 6, 9)).state());
        assertEquals(SurplusClaimDayState.REAL, day(claim, LocalDate.of(2026, 6, 11)).state());
        assertEquals(29, claim.summary().realDays());
        assertEquals(1, claim.summary().estimatedDays());
        assertEquals(1, claim.warnings().suppliesWithGaps().size());
        assertEquals(supplyA, claim.warnings().suppliesWithGaps().get(0).supply());
        assertEquals(List.of(LocalDate.of(2026, 6, 10)), claim.warnings().suppliesWithGaps().get(0).days());
        assertTrue(claim.warnings().suppliesWithoutData().isEmpty());
    }

    @Test
    @DisplayName("SCL-003 a day without any record of one supply is ESTIMATED, the supply's other days counting as complete")
    void aDayWithoutAnyRecordOfOneSupplyIsEstimated() {
        PlantSurplusClaim claim = calculate(input(JUNE).withoutDay(supplyB.code(), LocalDate.of(2026, 6, 20)));

        assertEquals(SurplusClaimDayState.ESTIMATED, day(claim, LocalDate.of(2026, 6, 20)).state());
        assertEquals(29, claim.summary().realDays());
        assertEquals(List.of(LocalDate.of(2026, 6, 20)), claim.warnings().suppliesWithGaps().get(0).days());
    }

    @Test
    @DisplayName("SCL-009 AC6 a supply without any record is listed with its coefficient, SCL-003 every day is ESTIMATED, and SCL-006 it counts in scope but not with data")
    void aSupplyWithoutAnyRecordIsListedWithItsCoefficient() {
        PlantSurplusClaim claim = calculate(input(JUNE).withoutRecords(supplyB.code()));

        assertEquals(1, claim.warnings().suppliesWithoutData().size());
        assertEquals(supplyB, claim.warnings().suppliesWithoutData().get(0).supply());
        assertEquals(0, new BigDecimal("0.4").compareTo(claim.warnings().suppliesWithoutData().get(0).coefficient()));
        assertTrue(claim.days().stream().allMatch(day -> day.state() == SurplusClaimDayState.ESTIMATED));
        assertEquals(2, claim.summary().suppliesInScope());
        assertEquals(1, claim.summary().suppliesWithData());
        // A supply without data is not also listed with gaps.
        assertTrue(claim.warnings().suppliesWithGaps().isEmpty());
    }

    @Test
    @DisplayName("SCL-009 the coefficient of a supply without data is the one in force on the last day of the month it held one")
    void theCoefficientOfASupplyWithoutDataIsTheLastOneItHeldInTheMonth() {
        SurplusClaimInputBuilder input = input(JUNE).withoutRecords(supplyB.code());
        input.coefficients.clear();
        input.coefficients.add(coefficient(supplyA, "0.6", LocalDate.of(2026, 1, 1), null));
        input.coefficients.add(coefficient(supplyB, "0.3", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 15)));
        input.coefficients.add(coefficient(supplyB, "0.4", LocalDate.of(2026, 6, 15), LocalDate.of(2026, 6, 25)));
        // Starts after the month: not held in it.
        input.coefficients.add(coefficient(supplyB, "0.5", LocalDate.of(2026, 7, 1), null));

        PlantSurplusClaim claim = calculate(input);

        assertEquals(0, new BigDecimal("0.4").compareTo(claim.warnings().suppliesWithoutData().get(0).coefficient()));
    }

    @Test
    @DisplayName("SCL-004 AC7 the days the clocks change in Europe/Madrid have 23 and 25 hours, each starting at a distinct instant")
    void theDaysTheClocksChangeHaveTwentyThreeAndTwentyFiveHours() {
        PlantSurplusClaim march = calculate(input(YearMonth.of(2026, 3)));
        PlantSurplusClaim october = calculate(input(YearMonth.of(2026, 10)));

        SurplusClaimDay springForward = day(march, LocalDate.of(2026, 3, 29));
        SurplusClaimDay fallBack = day(october, LocalDate.of(2026, 10, 25));
        assertEquals(23, springForward.hours().size());
        assertEquals(25, fallBack.hours().size());
        assertEquals(24, day(march, LocalDate.of(2026, 3, 28)).hours().size());
        assertDistinctHourlyInstants(springForward);
        assertDistinctHourlyInstants(fallBack);
        assertEquals(Instant.parse("2026-03-28T23:00:00Z"), springForward.hours().get(0).startsAt());
        assertEquals(Instant.parse("2026-10-24T22:00:00Z"), fallBack.hours().get(0).startsAt());
        assertEquals(Instant.parse("2026-10-25T22:00:00Z"), fallBack.hours().get(24).startsAt());
        // Every supply stored the day's 23 and 25 hours, so both days are complete.
        assertEquals(SurplusClaimDayState.REAL, springForward.state());
        assertEquals(SurplusClaimDayState.REAL, fallBack.state());
    }

    @Test
    @DisplayName("SCL-010 AC8 coefficients adding up to 0.92 on 1 to 5 June report 0.08 uncovered on those five days")
    void coefficientsAddingUpToLessThanOneReportTheUncoveredShare() {
        SurplusClaimInputBuilder input = input(JUNE);
        input.coefficients.clear();
        input.coefficients.add(coefficient(supplyA, "0.52", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 6)));
        input.coefficients.add(coefficient(supplyA, "0.6", LocalDate.of(2026, 6, 6), null));
        input.coefficients.add(coefficient(supplyB, "0.4", LocalDate.of(2026, 1, 1), null));

        SurplusClaimWarnings.UncoveredShare uncovered = calculate(input).warnings().uncoveredShare();

        assertNotNull(uncovered);
        assertEquals(0, new BigDecimal("0.08").compareTo(uncovered.largestShare()));
        assertEquals(List.of(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 2), LocalDate.of(2026, 6, 3),
                LocalDate.of(2026, 6, 4), LocalDate.of(2026, 6, 5)), uncovered.days());
    }

    @Test
    @DisplayName("SCL-010 the largest uncovered share is reported, and a shortfall within 0.0001 is not uncovered")
    void theLargestUncoveredShareIsReportedAndARoundingShortfallIsNot() {
        SurplusClaimInputBuilder input = input(JUNE);
        input.coefficients.clear();
        input.coefficients.add(coefficient(supplyA, "0.5", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 3)));
        input.coefficients.add(coefficient(supplyA, "0.55", LocalDate.of(2026, 6, 3), LocalDate.of(2026, 6, 5)));
        input.coefficients.add(coefficient(supplyA, "0.5999", LocalDate.of(2026, 6, 5), null));
        input.coefficients.add(coefficient(supplyB, "0.4", LocalDate.of(2026, 1, 1), null));

        SurplusClaimWarnings.UncoveredShare uncovered = calculate(input).warnings().uncoveredShare();

        assertEquals(0, new BigDecimal("0.1").compareTo(uncovered.largestShare()));
        assertEquals(List.of(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 2), LocalDate.of(2026, 6, 3),
                LocalDate.of(2026, 6, 4)), uncovered.days());
    }

    @Test
    @DisplayName("SCL-010 the plant supply's coefficient counts toward the shared production")
    void thePlantSupplysCoefficientCountsTowardTheSharedProduction() {
        SurplusClaimInputBuilder input = input(JUNE);
        input.coefficients.clear();
        input.coefficients.add(coefficient(supplyA, "0.55", LocalDate.of(2026, 1, 1), null));
        input.coefficients.add(coefficient(supplyB, "0.4", LocalDate.of(2026, 1, 1), null));
        input.coefficients.add(coefficientOf(plantSupplyId, "0.05", LocalDate.of(2026, 1, 1), null));

        assertNull(calculate(input).warnings().uncoveredShare());
    }

    @Test
    @DisplayName("SCL-010 a day with no coefficient in force, in a month with applied coefficients, is fully uncovered")
    void aDayWithNoCoefficientInForceIsFullyUncovered() {
        SurplusClaimInputBuilder input = input(JUNE);
        input.coefficients.clear();
        input.coefficients.add(coefficient(supplyA, "0.6", LocalDate.of(2026, 6, 3), null));
        input.coefficients.add(coefficient(supplyB, "0.4", LocalDate.of(2026, 6, 3), null));

        SurplusClaimWarnings.UncoveredShare uncovered = calculate(input).warnings().uncoveredShare();

        assertEquals(0, BigDecimal.ONE.compareTo(uncovered.largestShare()));
        assertEquals(List.of(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 2)), uncovered.days());
    }

    @Test
    @DisplayName("SCL-010 the uncovered share is null when no applied coefficient overlaps the month")
    void theUncoveredShareIsNullWithoutAppliedCoefficients() {
        SurplusClaimInputBuilder input = input(JUNE);
        input.coefficients.clear();
        input.coefficients.add(coefficient(supplyA, "0.6", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 1)));
        input.scope.clear();

        SurplusClaimWarnings warnings = calculate(input).warnings();

        assertTrue(warnings.noAppliedCoefficients());
        assertNull(warnings.uncoveredShare());
    }

    @Test
    @DisplayName("SCL-011 AC9 without an applied coefficient no supply is in scope, SCL-003 every day is ESTIMATED, and SCL-006 the total and income are null")
    void withoutAnAppliedCoefficientNothingIsInScope() {
        SurplusClaimInputBuilder input = input(JUNE);
        input.coefficients.clear();
        input.scope.clear();
        input.daysBySupplyCode.clear();
        input.surplus.clear();

        PlantSurplusClaim claim = calculate(input);

        assertTrue(claim.warnings().noAppliedCoefficients());
        assertEquals(0, claim.summary().suppliesInScope());
        assertEquals(0, claim.summary().suppliesWithData());
        assertEquals(0, claim.summary().realDays());
        assertEquals(30, claim.summary().estimatedDays());
        assertNull(claim.summary().surplusKWh());
        assertNull(claim.summary().surplusIncome());
        assertTrue(claim.days().stream().allMatch(day -> day.surplusKWh() == null && day.peakHour() == null));
    }

    @Test
    @DisplayName("SCL-011 an applied coefficient overlapping the month raises no noAppliedCoefficients")
    void anAppliedCoefficientOverlappingTheMonthRaisesNoWarning() {
        assertFalse(calculate(input(JUNE)).warnings().noAppliedCoefficients());
    }

    @Test
    @DisplayName("SCL-011 AC10 an hour above the nominal power times one hour is listed, one equal to it is not")
    void anHourAboveTheNominalPowerIsListed() {
        Map<Instant, Double> surplus = new HashMap<>();
        surplus.put(at(2026, 6, 12, 13), 61d);
        surplus.put(at(2026, 6, 10, 13), 60d);
        surplus.put(at(2026, 6, 11, 14), 75.5d);

        List<SurplusClaimHour> above = calculate(input(JUNE).surplus(surplus)).warnings().hoursAboveNominalPower();

        assertEquals(List.of(new SurplusClaimHour(at(2026, 6, 11, 14), 75.5d),
                new SurplusClaimHour(at(2026, 6, 12, 13), 61d)), above);
    }

    @Test
    @DisplayName("SCL-011 AC2 the plant supply's coefficient in the month is reported as its share")
    void thePlantSupplysCoefficientIsReportedAsItsShare() {
        SurplusClaimInputBuilder input = input(JUNE);
        input.coefficients.add(coefficientOf(plantSupplyId, "0.05", LocalDate.of(2026, 1, 1), null));

        assertEquals(0, new BigDecimal("0.05").compareTo(calculate(input).warnings().plantSupplyShare()));
        assertNull(calculate(input(JUNE)).warnings().plantSupplyShare());
    }

    @Test
    @DisplayName("SCL-006 AC11 each hour is priced at its mean price, and the surplus of an hour without a price is unpriced")
    void eachHourIsPricedAtItsMeanPrice() {
        Map<Instant, Double> surplus = new HashMap<>();
        surplus.put(at(2026, 6, 10, 12), 5d);
        surplus.put(at(2026, 6, 10, 13), 1.5d);
        // The mean of quarter-hour prices of 0.04, 0.06, 0.08 and 0.10 €/kWh.
        Map<Instant, Double> prices = Map.of(at(2026, 6, 10, 12), 0.07d);

        SurplusClaimSummary summary = calculate(input(JUNE).surplus(surplus).prices(prices)).summary();

        assertEquals(6.5d, summary.surplusKWh(), TOLERANCE);
        assertEquals(0, new BigDecimal("0.35").compareTo(summary.surplusIncome().getAmountEur()));
        assertEquals(1.5d, summary.surplusIncome().getUnpricedSurplusKWh(), TOLERANCE);
    }

    @Test
    @DisplayName("SCL-006 the monthly total is the sum of the month's hours, zero hours included, and is not null when they all carry 0")
    void theMonthlyTotalIsTheSumOfTheMonthsHours() {
        Map<Instant, Double> surplus = new HashMap<>();
        surplus.put(at(2026, 6, 1, 0), 0d);
        surplus.put(at(2026, 6, 30, 23), 0d);

        SurplusClaimSummary summary = calculate(input(JUNE).surplus(surplus)).summary();

        assertEquals(0d, summary.surplusKWh());
        assertEquals(0, BigDecimal.ZERO.compareTo(summary.surplusIncome().getAmountEur()));
    }

    @Test
    @DisplayName("SCL-005 AC15 the peak hour is the earlier of two hours holding the day's largest surplus, with its start and surplus")
    void thePeakHourIsTheEarlierOfTwoEqualLargestHours() {
        Map<Instant, Double> surplus = new HashMap<>();
        surplus.put(at(2026, 6, 10, 11), 7d);
        surplus.put(at(2026, 6, 10, 13), 9d);
        surplus.put(at(2026, 6, 10, 15), 9d);

        SurplusClaimDay day = day(calculate(input(JUNE).surplus(surplus)), LocalDate.of(2026, 6, 10));

        assertEquals(new SurplusClaimHour(at(2026, 6, 10, 13), 9d), day.peakHour());
        assertEquals(25d, day.surplusKWh(), TOLERANCE);
    }

    @Test
    @DisplayName("SCL-005 AC15 a day whose total is 0 has no peak hour, and a day without any record has no total either")
    void aDayWhoseTotalIsZeroHasNoPeakHour() {
        Map<Instant, Double> surplus = new HashMap<>();
        surplus.put(at(2026, 6, 10, 11), 0d);
        surplus.put(at(2026, 6, 10, 13), 0d);

        PlantSurplusClaim claim = calculate(input(JUNE).surplus(surplus));

        assertEquals(0d, day(claim, LocalDate.of(2026, 6, 10)).surplusKWh());
        assertNull(day(claim, LocalDate.of(2026, 6, 10)).peakHour());
        assertNull(day(claim, LocalDate.of(2026, 6, 11)).surplusKWh());
        assertNull(day(claim, LocalDate.of(2026, 6, 11)).peakHour());
    }

    @Test
    @DisplayName("SCL-012 when nothing is raised, every warning list is empty and never null")
    void whenNothingIsRaisedEveryWarningListIsEmpty() {
        SurplusClaimWarnings warnings = calculate(input(JUNE)).warnings();

        assertTrue(warnings.suppliesWithoutData().isEmpty());
        assertTrue(warnings.suppliesWithGaps().isEmpty());
        assertTrue(warnings.hoursAboveNominalPower().isEmpty());
        assertNull(warnings.uncoveredShare());
        assertNull(warnings.plantSupplyShare());
        assertFalse(warnings.noAppliedCoefficients());
    }

    @Test
    @DisplayName("SCL-012 supplies in warnings are ordered by code, and their days chronologically")
    void suppliesInWarningsAreOrderedByCodeAndTheirDaysChronologically() {
        SupplyReference supplyC = new SupplyReference(UUID.randomUUID(), "ES0000000000000000CC", "C");
        SupplyReference supplyD = new SupplyReference(UUID.randomUUID(), "ES0000000000000000DD", null);
        SurplusClaimInputBuilder input = input(JUNE)
                .recordedHours(supplyB.code(), LocalDate.of(2026, 6, 20), 3)
                .recordedHours(supplyB.code(), LocalDate.of(2026, 6, 2), 3)
                .recordedHours(supplyA.code(), LocalDate.of(2026, 6, 15), 0);
        input.scope.add(0, supplyD);
        input.scope.add(0, supplyC);
        input.coefficients.add(coefficient(supplyC, "0", LocalDate.of(2026, 1, 1), null));
        input.coefficients.add(coefficient(supplyD, "0", LocalDate.of(2026, 1, 1), null));

        SurplusClaimWarnings warnings = calculate(input).warnings();

        assertEquals(List.of(supplyA, supplyB),
                warnings.suppliesWithGaps().stream().map(SurplusClaimWarnings.SupplyWithGaps::supply).toList());
        assertEquals(List.of(LocalDate.of(2026, 6, 2), LocalDate.of(2026, 6, 20)),
                warnings.suppliesWithGaps().get(1).days());
        assertEquals(List.of(supplyC, supplyD),
                warnings.suppliesWithoutData().stream().map(SurplusClaimWarnings.SupplyWithoutData::supply).toList());
    }

    private static void assertDistinctHourlyInstants(SurplusClaimDay day) {
        List<Instant> starts = day.hours().stream().map(SurplusClaimHour::startsAt).toList();
        assertEquals(starts.size(), new HashSet<>(starts).size());
        for (int i = 1; i < starts.size(); i++) {
            assertEquals(Duration.ofHours(1), Duration.between(starts.get(i - 1), starts.get(i)));
        }
    }

    private static SurplusClaimDay day(PlantSurplusClaim claim, LocalDate date) {
        return claim.days().stream().filter(day -> day.date().equals(date)).findFirst().orElseThrow();
    }

    private static SurplusClaimHour hour(SurplusClaimDay day, int localHour) {
        return day.hours().stream()
                .filter(hour -> hour.startsAt().atZone(MADRID).getHour() == localHour)
                .findFirst().orElseThrow();
    }

    private static Instant at(int year, int month, int day, int localHour) {
        return LocalDateTime.of(year, month, day, localHour, 0).atZone(MADRID).toInstant();
    }

    private PlantSurplusClaim calculate(SurplusClaimInputBuilder input) {
        return PlantSurplusClaimCalculator.calculate(input.build());
    }

    private SupplyPartitionCoefficient coefficient(SupplyReference supply, String value, LocalDate validFrom,
                                                   LocalDate validTo) {
        return coefficientOf(supply.id(), value, validFrom, validTo);
    }

    private SupplyPartitionCoefficient coefficientOf(UUID supplyId, String value, LocalDate validFrom,
                                                     LocalDate validTo) {
        return new SupplyPartitionCoefficient.Builder()
                .withId(UUID.randomUUID())
                .withSupplyId(supplyId)
                .withPlantId(plantId)
                .withCoefficient(new BigDecimal(value))
                .withValidFrom(validFrom.atStartOfDay(MADRID).toInstant())
                .withValidTo(validTo == null ? null : validTo.atStartOfDay(MADRID).toInstant())
                .build();
    }

    private SurplusClaimInputBuilder input(YearMonth month) {
        SurplusClaimInputBuilder input = new SurplusClaimInputBuilder(month);
        input.scope.add(supplyB);
        input.scope.add(supplyA);
        input.coefficients.add(coefficient(supplyA, "0.6", LocalDate.of(2025, 1, 1), null));
        input.coefficients.add(coefficient(supplyB, "0.4", LocalDate.of(2025, 1, 1), null));
        input.completeMonth(supplyA.code());
        input.completeMonth(supplyB.code());
        return input;
    }

    /**
     * The input of one month, built up by the tests.
     */
    private final class SurplusClaimInputBuilder {

        private final YearMonth month;
        private final List<SupplyReference> scope = new ArrayList<>();
        private final List<SupplyPartitionCoefficient> coefficients = new ArrayList<>();
        private final Map<String, List<DailyPublishedAggregate>> daysBySupplyCode = new HashMap<>();
        private Map<Instant, Double> surplus = new HashMap<>();
        private Map<Instant, Double> prices = new HashMap<>();

        private SurplusClaimInputBuilder(YearMonth month) {
            this.month = month;
        }

        /** A surplus record at every hour of every day of the month. */
        private void completeMonth(String code) {
            List<DailyPublishedAggregate> days = new ArrayList<>();
            for (LocalDate date = month.atDay(1); !date.isAfter(month.atEndOfMonth()); date = date.plusDays(1)) {
                long hours = Duration.between(date.atStartOfDay(MADRID), date.plusDays(1).atStartOfDay(MADRID))
                        .toHours();
                days.add(aggregate(date, hours));
            }
            daysBySupplyCode.put(code, days);
        }

        private SurplusClaimInputBuilder recordedHours(String code, LocalDate date, long hours) {
            List<DailyPublishedAggregate> days = new ArrayList<>(daysBySupplyCode.get(code).stream()
                    .filter(day -> !day.day().equals(date)).toList());
            days.add(aggregate(date, hours));
            daysBySupplyCode.put(code, days);
            return this;
        }

        private SurplusClaimInputBuilder withoutDay(String code, LocalDate date) {
            daysBySupplyCode.put(code, daysBySupplyCode.get(code).stream()
                    .filter(day -> !day.day().equals(date)).toList());
            return this;
        }

        private SurplusClaimInputBuilder withoutRecords(String code) {
            daysBySupplyCode.remove(code);
            return this;
        }

        private SurplusClaimInputBuilder surplus(Map<Instant, Double> surplus) {
            this.surplus = surplus;
            return this;
        }

        private SurplusClaimInputBuilder prices(Map<Instant, Double> prices) {
            this.prices = prices;
            return this;
        }

        private SurplusClaimInput build() {
            return new SurplusClaimInput(month, MADRID, new PlantReference(plantId, "North"), null, null,
                    plantSupplyId,
                    60d, new SurplusClaimAvailableMonths(null, null), coefficients, scope, surplus,
                    daysBySupplyCode, prices);
        }

        private static DailyPublishedAggregate aggregate(LocalDate date, long surplusHours) {
            return new DailyPublishedAggregate(date, 0d, 0d, 0d, surplusHours, surplusHours, "Real");
        }
    }
}

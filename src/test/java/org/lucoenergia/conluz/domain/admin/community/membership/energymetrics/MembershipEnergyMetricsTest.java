package org.lucoenergia.conluz.domain.admin.community.membership.energymetrics;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.tariff.TariffSource;
import org.lucoenergia.conluz.domain.consumption.EstimatedPrice;
import org.lucoenergia.conluz.domain.consumption.SupplyEnergyMetrics;
import org.lucoenergia.conluz.domain.consumption.SupplySavings;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Pins the aggregation over the per-supply metrics. The per-supply figures are built by hand, so
 * every expected number here is computed from them, never read off the implementation.
 */
class MembershipEnergyMetricsTest {

    private static final OffsetDateTime START = OffsetDateTime.parse("2026-07-01T00:00:00+02:00");
    private static final OffsetDateTime END = OffsetDateTime.parse("2026-07-01T23:00:00+02:00");
    private static final long HOURS_IN_PERIOD = 24L;

    @Test
    void eachTotalIsTheSumOfThePerSupplyTotals() {
        MembershipEnergyMetrics metrics = MembershipEnergyMetrics.of(START, END, List.of(
                supply(24L, 60d, 40d, 10d, "4.00"),
                supply(24L, 1d, 1d, 3d, "0.10"),
                supply(24L, 5d, 2d, 7d, "0.20")));

        assertEquals(66d, metrics.getEnergyBalance().getGridImportKWh(), 1e-9);
        assertEquals(43d, metrics.getEnergyBalance().getSelfConsumptionKWh(), 1e-9);
        assertEquals(20d, metrics.getEnergyBalance().getSurplusKWh(), 1e-9);
        assertEquals(109d, metrics.getEnergyBalance().getTotalConsumptionKWh(), 1e-9);
        assertEquals(63d, metrics.getEnergyBalance().getAssignedProductionKWh(), 1e-9);
        assertEquals(0, new BigDecimal("4.30").compareTo(metrics.getSavings().getAmountEur()));
    }

    /**
     * Supply A: 60 kWh imported, 40 self-consumed, 10 surplus -- self-sufficiency 40/100 = 0.40,
     * self-consumption 40/50 = 0.80. Supply B: 1, 1, 3 -- 1/2 = 0.50 and 1/4 = 0.25.
     *
     * <p>Averaging the per-supply ratios would give 0.45 and 0.525. Summing first gives
     * 41/102 = 0.4020 and 41/54 = 0.7593, which is what the membership reports. All four numbers
     * differ, and so do the two correct ratios from each other, so a swapped formula fails too.
     */
    @Test
    void ratiosAreDerivedFromTheSummedTotalsAndNeverByAveragingThePerSupplyRatios() {
        MembershipEnergyMetrics metrics = MembershipEnergyMetrics.of(START, END, List.of(
                supply(24L, 60d, 40d, 10d, "0"),
                supply(24L, 1d, 1d, 3d, "0")));

        assertEquals(0.4020d, metrics.getEnergyBalance().getSelfSufficiencyRatio(), 1e-4);
        assertEquals(0.7593d, metrics.getEnergyBalance().getSelfConsumptionRatio(), 1e-4);
    }

    @Test
    void aRatioWhoseSummedDenominatorIsZeroIsNullRatherThanZero() {
        MembershipEnergyMetrics withoutAssignedProduction = MembershipEnergyMetrics.of(START, END, List.of(
                supply(24L, 10d, 0d, 0d, "0"),
                supply(24L, 5d, 0d, 0d, "0")));
        MembershipEnergyMetrics withoutConsumption = MembershipEnergyMetrics.of(START, END, List.of(
                supply(24L, 0d, 0d, 4d, "0"),
                supply(24L, 0d, 0d, 6d, "0")));

        assertNull(withoutAssignedProduction.getEnergyBalance().getSelfConsumptionRatio());
        assertEquals(0d, withoutAssignedProduction.getEnergyBalance().getSelfSufficiencyRatio());
        assertNull(withoutConsumption.getEnergyBalance().getSelfSufficiencyRatio());
        assertEquals(0d, withoutConsumption.getEnergyBalance().getSelfConsumptionRatio());
    }

    /**
     * Three supplies over a 24-hour period: one complete, one with 10 records, one silent. The
     * expected hours span all three, and the two counters tell the silent supply apart from the
     * partial one: 3 supplies, 2 with data, and only 1 of them complete.
     */
    @Test
    void coverageSpansEverySupplyAndCountsTheSuppliesThatContributedRecords() {
        MembershipEnergyMetrics metrics = MembershipEnergyMetrics.of(START, END, List.of(
                supply(24L, 1d, 1d, 1d, "0"),
                supply(10L, 1d, 1d, 1d, "0"),
                supply(0L, 0d, 0d, 0d, "0")));

        assertEquals(34L, metrics.getHoursWithData());
        assertEquals(72L, metrics.getExpectedHours());
        assertEquals(3, metrics.getSupplyCount());
        assertEquals(2, metrics.getSuppliesWithData());
    }

    @Test
    void oneEstimatedSupplyMakesTheSavingsAnEstimateCarryingTheEstimatedPrice() {
        EstimatedPrice price = EstimatedPrice.of(new BigDecimal("0.15"));

        MembershipEnergyMetrics metrics = MembershipEnergyMetrics.of(START, END, List.of(
                supply(24L, 1d, 1d, 1d, SupplySavings.of(BigDecimal.ONE, TariffSource.REAL_TARIFF, null)),
                supply(24L, 1d, 1d, 1d, SupplySavings.of(BigDecimal.TEN, TariffSource.ESTIMATE, price))));

        assertEquals(TariffSource.ESTIMATE, metrics.getSavings().getTariffSource());
        assertEquals(price, metrics.getSavings().getEstimatedPrice());
    }

    @Test
    void savingsPricedOnlyWithContractedTariffsCarryNoEstimatedPrice() {
        MembershipEnergyMetrics metrics = MembershipEnergyMetrics.of(START, END, List.of(
                supply(24L, 1d, 1d, 1d, SupplySavings.of(BigDecimal.ONE, TariffSource.REAL_TARIFF, null)),
                supply(24L, 1d, 1d, 1d, SupplySavings.of(BigDecimal.TEN, TariffSource.REAL_TARIFF, null))));

        assertEquals(TariffSource.REAL_TARIFF, metrics.getSavings().getTariffSource());
        assertNull(metrics.getSavings().getEstimatedPrice());
    }

    /**
     * The savings rule: a resolved period always yields a figure, zero when nothing was priced.
     */
    @Test
    void savingsAreAFigureWheneverAPeriodExistsEvenWithoutSupplies() {
        MembershipEnergyMetrics metrics = MembershipEnergyMetrics.of(START, END, List.of());

        assertEquals(START, metrics.getStartDate());
        assertEquals(END, metrics.getEndDate());
        assertEquals(0, BigDecimal.ZERO.compareTo(metrics.getSavings().getAmountEur()));
        assertEquals(0L, metrics.getExpectedHours());
        assertEquals(0, metrics.getSupplyCount());
        assertNull(metrics.getEnergyBalance().getSelfSufficiencyRatio());
        assertNull(metrics.getEnergyBalance().getSelfConsumptionRatio());
    }

    /**
     * The savings rule: null only when no period could be resolved.
     */
    @Test
    void savingsAreNullOnlyWhenNoPeriodCouldBeResolved() {
        MembershipEnergyMetrics metrics = MembershipEnergyMetrics.withoutPeriod(2);

        assertNull(metrics.getStartDate());
        assertNull(metrics.getEndDate());
        assertNull(metrics.getSavings().getAmountEur());
        assertNull(metrics.getSavings().getEstimatedPrice());
        assertEquals(0d, metrics.getEnergyBalance().getTotalConsumptionKWh());
        assertNull(metrics.getEnergyBalance().getSelfSufficiencyRatio());
        assertNull(metrics.getEnergyBalance().getSelfConsumptionRatio());
        assertEquals(2, metrics.getSupplyCount());
        assertEquals(0, metrics.getSuppliesWithData());
    }

    private static SupplyEnergyMetrics supply(long hoursWithData, double gridImportKWh, double selfConsumptionKWh,
                                              double surplusKWh, String savingsEur) {
        return supply(hoursWithData, gridImportKWh, selfConsumptionKWh, surplusKWh,
                SupplySavings.of(new BigDecimal(savingsEur), TariffSource.ESTIMATE, null));
    }

    private static SupplyEnergyMetrics supply(long hoursWithData, double gridImportKWh, double selfConsumptionKWh,
                                              double surplusKWh, SupplySavings savings) {
        return new SupplyEnergyMetrics(SupplyMother.random().build(), START, END, hoursWithData, HOURS_IN_PERIOD,
                gridImportKWh, selfConsumptionKWh, surplusKWh, savings);
    }
}

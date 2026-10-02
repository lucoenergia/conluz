package org.lucoenergia.conluz.domain.admin.community.membership.consumption;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.tariff.TariffSource;
import org.lucoenergia.conluz.domain.consumption.EstimatedPrice;
import org.lucoenergia.conluz.domain.consumption.SupplyConsumptionBucket;
import org.lucoenergia.conluz.domain.consumption.SupplySavings;
import org.lucoenergia.conluz.domain.consumption.datadis.DatadisConsumption;
import org.lucoenergia.conluz.domain.shared.SupplyId;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MembershipMonthlyConsumptionBucketTest {

    private static final YearMonth JANUARY = YearMonth.of(2024, 1);
    private static final EstimatedPrice PRICE = EstimatedPrice.of(new BigDecimal("0.15"));

    private static final SupplyId FIRST = SupplyId.of(UUID.randomUUID());
    private static final SupplyId SECOND = SupplyId.of(UUID.randomUUID());
    private static final SupplyId THIRD = SupplyId.of(UUID.randomUUID());

    /**
     * AC1. Two supplies of very different magnitudes: 100.5 + 7.25 kWh consumed, 20.25 + 1.5
     * exported, 60 + 3 generated, 40 + 2.5 self-consumed, and savings of 6.00 + 0.375.
     */
    @Test
    void eachTotalIsTheSumOfThePerSupplyTotals() {
        MembershipMonthlyConsumptionBucket bucket = MembershipMonthlyConsumptionBucket.of(JANUARY, 2, Map.of(
                FIRST, List.of(bucket(100.5f, 20.25f, 60f, 40f, "6.00", TariffSource.ESTIMATE)),
                SECOND, List.of(bucket(7.25f, 1.5f, 3f, 2.5f, "0.375", TariffSource.ESTIMATE))));

        assertDecimal("107.75", bucket.getConsumptionKWh());
        assertDecimal("21.75", bucket.getSurplusEnergyKWh());
        assertDecimal("63", bucket.getGenerationEnergyKWh());
        assertDecimal("42.5", bucket.getSelfConsumptionEnergyKWh());
        assertDecimal("6.375", bucket.getSavings().orElseThrow().getAmountEur());
        assertEquals(JANUARY, bucket.getMonth());
    }

    /**
     * AC3. One supply priced with its contracted tariff and another with the estimate in the same
     * month: the month's savings are an estimate.
     */
    @Test
    void aMonthPartlyPricedWithTheEstimateIsAnEstimate() {
        MembershipMonthlyConsumptionBucket bucket = MembershipMonthlyConsumptionBucket.of(JANUARY, 2, Map.of(
                FIRST, List.of(bucket(10f, 0f, 4f, 4f, "0.80", TariffSource.REAL_TARIFF)),
                SECOND, List.of(bucket(10f, 0f, 2f, 2f, "0.30", TariffSource.ESTIMATE))));

        SupplySavings savings = bucket.getSavings().orElseThrow();
        assertEquals(TariffSource.ESTIMATE, savings.getTariffSource());
        assertDecimal("1.10", savings.getAmountEur());
    }

    /**
     * AC3, the other half: with every supply on its contracted tariff the month is not an estimate.
     */
    @Test
    void aMonthWhollyPricedWithContractedTariffsIsNotAnEstimate() {
        MembershipMonthlyConsumptionBucket bucket = MembershipMonthlyConsumptionBucket.of(JANUARY, 2, Map.of(
                FIRST, List.of(bucket(10f, 0f, 4f, 4f, "0.80", TariffSource.REAL_TARIFF)),
                SECOND, List.of(bucket(10f, 0f, 2f, 2f, "0.40", TariffSource.REAL_TARIFF))));

        assertEquals(TariffSource.REAL_TARIFF, bucket.getSavings().orElseThrow().getTariffSource());
    }

    // --- The three states the chart must tell apart: nothing stored, partly reported, complete ---

    /**
     * AC4. No supply stored a record: zero energy, no savings at all, and no supply with data.
     */
    @Test
    void aMonthWithNothingStoredHasNoSavingsAndNoSupplyWithData() {
        MembershipMonthlyConsumptionBucket bucket = MembershipMonthlyConsumptionBucket.of(JANUARY, 3, Map.of());

        assertDecimal("0", bucket.getConsumptionKWh());
        assertDecimal("0", bucket.getSurplusEnergyKWh());
        assertDecimal("0", bucket.getGenerationEnergyKWh());
        assertDecimal("0", bucket.getSelfConsumptionEnergyKWh());
        assertTrue(bucket.getSavings().isEmpty());
        assertEquals(3, bucket.getSupplyCount());
        assertEquals(0, bucket.getSuppliesWithData());
    }

    /**
     * AC5. Three supplies, one silent: the month reports all three and the two that stored a record.
     */
    @Test
    void aPartlyReportedMonthReportsBothCounters() {
        MembershipMonthlyConsumptionBucket bucket = MembershipMonthlyConsumptionBucket.of(JANUARY, 3, Map.of(
                FIRST, List.of(bucket(30f, 0f, 5f, 5f, "0.75", TariffSource.ESTIMATE)),
                SECOND, List.of(bucket(2f, 0f, 1f, 1f, "0.15", TariffSource.ESTIMATE))));

        assertEquals(3, bucket.getSupplyCount());
        assertEquals(2, bucket.getSuppliesWithData());
        assertDecimal("32", bucket.getConsumptionKWh());
        assertDecimal("0.90", bucket.getSavings().orElseThrow().getAmountEur());
    }

    @Test
    void aCompleteMonthHasEverySupplyWithData() {
        MembershipMonthlyConsumptionBucket bucket = MembershipMonthlyConsumptionBucket.of(JANUARY, 3, Map.of(
                FIRST, List.of(bucket(30f, 0f, 5f, 5f, "0.75", TariffSource.ESTIMATE)),
                SECOND, List.of(bucket(2f, 0f, 1f, 1f, "0.15", TariffSource.ESTIMATE)),
                THIRD, List.of(bucket(4f, 0f, 2f, 2f, "0.30", TariffSource.ESTIMATE))));

        assertEquals(3, bucket.getSupplyCount());
        assertEquals(3, bucket.getSuppliesWithData());
        assertDecimal("1.20", bucket.getSavings().orElseThrow().getAmountEur());
    }

    /**
     * A stored month with zero energy is a measured zero: the supply counts as one with data and
     * the month's savings are zero, not absent.
     */
    @Test
    void aStoredMonthWithZeroEnergyIsAMeasuredZero() {
        MembershipMonthlyConsumptionBucket bucket = MembershipMonthlyConsumptionBucket.of(JANUARY, 2, Map.of(
                FIRST, List.of(bucket(0f, 0f, 0f, 0f, "0", TariffSource.ESTIMATE))));

        assertEquals(1, bucket.getSuppliesWithData());
        SupplySavings savings = bucket.getSavings().orElseThrow();
        assertDecimal("0", savings.getAmountEur());
        assertEquals(TariffSource.ESTIMATE, savings.getTariffSource());
    }

    /**
     * Two buckets of one supply in the same month count that supply once. Its energy and savings
     * are both added, deliberately: duplicate points are not compensated for, so 10 + 10 kWh and
     * 0.30 + 0.30 EUR are reported.
     */
    @Test
    void twoBucketsOfOneSupplyCountItOnceAndAddBothFigures() {
        MembershipMonthlyConsumptionBucket bucket = MembershipMonthlyConsumptionBucket.of(JANUARY, 2, Map.of(
                FIRST, List.of(bucket(10f, 0f, 2f, 2f, "0.30", TariffSource.ESTIMATE),
                        bucket(10f, 0f, 2f, 2f, "0.30", TariffSource.ESTIMATE))));

        assertEquals(1, bucket.getSuppliesWithData());
        assertTrue(bucket.getSuppliesWithData() <= bucket.getSupplyCount());
        assertDecimal("20", bucket.getConsumptionKWh());
        assertDecimal("4", bucket.getSelfConsumptionEnergyKWh());
        assertDecimal("0.60", bucket.getSavings().orElseThrow().getAmountEur());
    }

    /**
     * Energy is added as Datadis reported it: 0.1 + 0.2 is 0.3, not the float sum 0.30000000447.
     */
    @Test
    void energyIsAddedWithoutFloatWideningError() {
        MembershipMonthlyConsumptionBucket bucket = MembershipMonthlyConsumptionBucket.of(JANUARY, 2, Map.of(
                FIRST, List.of(bucket(0.1f, 0f, 0f, 0f, "0", TariffSource.ESTIMATE)),
                SECOND, List.of(bucket(0.2f, 0f, 0f, 0f, "0", TariffSource.ESTIMATE))));

        assertEquals(new BigDecimal("0.3"), bucket.getConsumptionKWh());
    }

    private static SupplyConsumptionBucket bucket(float consumptionKWh, float surplusKWh, float generationKWh,
                                                  float selfConsumptionKWh, String savingsEur,
                                                  TariffSource source) {
        DatadisConsumption consumption = new DatadisConsumption();
        consumption.setDate("2024/01/01");
        consumption.setTime("00:00");
        consumption.setConsumptionKWh(consumptionKWh);
        consumption.setSurplusEnergyKWh(surplusKWh);
        consumption.setGenerationEnergyKWh(generationKWh);
        consumption.setSelfConsumptionEnergyKWh(selfConsumptionKWh);
        consumption.setObtainMethod("Real");
        EstimatedPrice price = source == TariffSource.ESTIMATE ? PRICE : null;
        return SupplyConsumptionBucket.of(consumption, SupplySavings.of(new BigDecimal(savingsEur), source, price));
    }

    private static void assertDecimal(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                () -> "expected " + expected + " but was " + actual);
    }
}

package org.lucoenergia.conluz.domain.admin.community.membership.consumption;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.tariff.TariffSource;
import org.lucoenergia.conluz.domain.consumption.EstimatedPrice;
import org.lucoenergia.conluz.domain.consumption.SupplyConsumptionBucket;
import org.lucoenergia.conluz.domain.consumption.SupplySavings;
import org.lucoenergia.conluz.domain.consumption.datadis.DatadisConsumption;
import org.lucoenergia.conluz.domain.shared.SupplyId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MembershipDailyConsumptionBucketTest {

    private static final LocalDate DAY = LocalDate.of(2024, 1, 10);
    private static final EstimatedPrice PRICE = EstimatedPrice.of(new BigDecimal("0.15"));

    private static final SupplyId FIRST = SupplyId.of(UUID.randomUUID());
    private static final SupplyId SECOND = SupplyId.of(UUID.randomUUID());

    /**
     * 10 + 2.5 kWh consumed, 1 + 0.5 exported, 4 + 1 self-consumed, savings 0.60 + 0.15; assigned
     * production is self-consumed plus exported, 6.5 kWh.
     */
    @Test
    @DisplayName("MCS-002 a day's figures are the sums of the contributing supplies' figures")
    void aDaysFiguresAreTheSumsOfTheContributingSupplies() {
        MembershipDailyConsumptionBucket bucket = MembershipDailyConsumptionBucket.of(DAY, 3, Map.of(
                FIRST, List.of(bucket(10f, 1f, 4f, "0.60")),
                SECOND, List.of(bucket(2.5f, 0.5f, 1f, "0.15"))));

        MembershipConsumptionTotals totals = bucket.getTotals();
        assertEquals(DAY, bucket.getDay());
        assertDecimal("12.5", totals.getConsumptionKWh());
        assertDecimal("1.5", totals.getSurplusEnergyKWh());
        assertDecimal("5", totals.getSelfConsumptionEnergyKWh());
        assertEquals(6.5, totals.getAssignedProductionKWh());
        assertDecimal("0.75", totals.getSavings().orElseThrow().getAmountEur());
        assertEquals(3, totals.getSupplyCount());
        assertEquals(2, totals.getSuppliesWithData());
    }

    @Test
    @DisplayName("MCS-001 a day no supply contributed to has no savings, not savings of zero")
    void aDayWithoutContributionsHasNoSavings() {
        MembershipConsumptionTotals totals = MembershipDailyConsumptionBucket.of(DAY, 2, Map.of()).getTotals();

        assertTrue(totals.getSavings().isEmpty());
        assertDecimal("0", totals.getConsumptionKWh());
        assertEquals(2, totals.getSupplyCount());
        assertEquals(0, totals.getSuppliesWithData());
    }

    @Test
    @DisplayName("MCS-001 a day of stored zeros has savings of zero")
    void aDayOfStoredZerosHasSavingsOfZero() {
        MembershipConsumptionTotals totals = MembershipDailyConsumptionBucket.of(DAY, 1, Map.of(
                FIRST, List.of(bucket(0f, 0f, 0f, "0")))).getTotals();

        assertDecimal("0", totals.getSavings().orElseThrow().getAmountEur());
        assertEquals(TariffSource.ESTIMATE, totals.getSavings().orElseThrow().getTariffSource());
        assertEquals(1, totals.getSuppliesWithData());
    }

    private static SupplyConsumptionBucket bucket(float consumptionKWh, float surplusKWh, float selfConsumptionKWh,
                                                  String savingsEur) {
        DatadisConsumption consumption = new DatadisConsumption();
        consumption.setDate("2024/01/10");
        consumption.setTime("00:00");
        consumption.setConsumptionKWh(consumptionKWh);
        consumption.setSurplusEnergyKWh(surplusKWh);
        consumption.setGenerationEnergyKWh(0f);
        consumption.setSelfConsumptionEnergyKWh(selfConsumptionKWh);
        consumption.setObtainMethod("Real");
        return SupplyConsumptionBucket.of(consumption,
                SupplySavings.of(new BigDecimal(savingsEur), TariffSource.ESTIMATE, PRICE));
    }

    private static void assertDecimal(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                () -> "expected " + expected + " but was " + actual);
    }
}

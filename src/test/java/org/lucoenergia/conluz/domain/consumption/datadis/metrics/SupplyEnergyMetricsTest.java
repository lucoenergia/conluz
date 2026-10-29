package org.lucoenergia.conluz.domain.consumption.datadis.metrics;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.tariff.TariffSource;
import org.lucoenergia.conluz.domain.consumption.SupplyEnergyMetrics;
import org.lucoenergia.conluz.domain.consumption.SupplySavings;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SupplyEnergyMetricsTest {

    private static final OffsetDateTime START_DATE = OffsetDateTime.parse("2024-02-01T00:00:00+01:00");
    private static final OffsetDateTime END_DATE = OffsetDateTime.parse("2024-02-01T02:00:00+01:00");

    private final Supply supply = SupplyMother.random().build();

    @Test
    void ratiosAreComputedFromTheSumsAndNotFromPerRecordRatios() {
        // The sums of 1 + 100 + 1 kWh imported, 1 + 50 + 2 kWh self-consumed and 9 + 0.5 + 2 kWh
        // of surplus. Averaging the three records' own ratios would give 0.5 and 0.5300 instead.
        SupplyEnergyMetrics metrics = metrics(102d, 53d, 11.5d);

        assertEquals(155d, metrics.getTotalConsumptionKWh(), 1e-9);
        assertEquals(64.5d, metrics.getAssignedProductionKWh(), 1e-9);
        assertEquals(53d / 155d, metrics.getSelfSufficiencyRatio(), 1e-9);
        assertEquals(53d / 64.5d, metrics.getSelfConsumptionRatio().getValue(), 1e-9);
    }

    @Test
    void aZeroTotalConsumptionYieldsANullSelfSufficiencyRatio() {
        SupplyEnergyMetrics metrics = metrics(0d, 0d, 5d);

        assertNull(metrics.getSelfSufficiencyRatio());
        assertEquals(0d, metrics.getSelfConsumptionRatio().getValue());
    }

    @Test
    void aZeroAssignedProductionYieldsANullSelfConsumptionRatio() {
        SupplyEnergyMetrics metrics = metrics(10d, 0d, 0d);

        assertNull(metrics.getSelfConsumptionRatio().getValue());
        assertEquals(0d, metrics.getSelfSufficiencyRatio());
    }

    @Test
    void bothRatiosAreNullWhenThereIsNoEnergyAtAll() {
        SupplyEnergyMetrics metrics = metrics(0d, 0d, 0d);

        assertNull(metrics.getSelfSufficiencyRatio());
        assertNull(metrics.getSelfConsumptionRatio().getValue());
    }

    /**
     * A ratio is either a finite number or null. Dividing before checking the denominator would
     * produce NaN or Infinity, which Jackson writes as a bare token that is not valid JSON.
     */
    @Test
    void aRatioIsNeverNotANumberNorInfinite() {
        for (SupplyEnergyMetrics metrics : new SupplyEnergyMetrics[]{
                metrics(0d, 0d, 0d),
                metrics(0d, 0d, 5d),
                metrics(10d, 0d, 0d),
                metrics(0d, 5d, 0d),
                SupplyEnergyMetrics.empty(supply, START_DATE, END_DATE, 3L, false)}) {

            assertFinite(metrics.getSelfSufficiencyRatio());
            assertFinite(metrics.getSelfConsumptionRatio().getValue());
        }
    }

    @Test
    void anEmptyResultReportsZeroEnergyAndNoRatios() {
        SupplyEnergyMetrics metrics = SupplyEnergyMetrics.empty(supply, null, null, 0L, false);

        assertNull(metrics.getStartDate());
        assertNull(metrics.getEndDate());
        assertEquals(0L, metrics.getHoursWithData());
        assertEquals(0L, metrics.getExpectedHours());
        assertEquals(0d, metrics.getTotalConsumptionKWh());
        assertEquals(0d, metrics.getAssignedProductionKWh());
        assertNull(metrics.getSelfSufficiencyRatio());
        assertNull(metrics.getSelfConsumptionRatio().getValue());
        assertNotNull(metrics.getSupply());
    }

    private SupplyEnergyMetrics metrics(double gridImportKWh, double selfConsumptionKWh, double surplusKWh) {
        return new SupplyEnergyMetrics(supply, START_DATE, END_DATE, 3L, 3L,
                gridImportKWh, selfConsumptionKWh, surplusKWh,
                SupplySavings.of(new BigDecimal("1.23"), TariffSource.ESTIMATE, null), false);
    }

    private void assertFinite(Double ratio) {
        if (ratio == null) {
            return;
        }
        assertTrue(Double.isFinite(ratio), () -> "Expected a finite ratio but got " + ratio);
    }
}

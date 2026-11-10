package org.lucoenergia.conluz.domain.production.plant.energy;

import org.lucoenergia.conluz.domain.consumption.EnergyMetricsPeriod;
import org.lucoenergia.conluz.domain.production.datadis.get.ProductionAggregate;

import java.util.Optional;

/**
 * The energy figures of a plant over a period: the production of its backing supply, unrounded, and
 * how much of the period its records cover.
 *
 * <p>Production is null exactly when no record was stored in the period, so a gap is never reported as
 * a zero; a period without bounds holds no record and spans no hour.</p>
 */
public class PlantEnergyMetrics {

    private final EnergyMetricsPeriod period;
    private final Double productionKWh;
    private final long hoursWithData;
    private final long expectedHours;

    private PlantEnergyMetrics(EnergyMetricsPeriod period, Double productionKWh, long hoursWithData,
                               long expectedHours) {
        this.period = period;
        this.productionKWh = productionKWh;
        this.hoursWithData = hoursWithData;
        this.expectedHours = expectedHours;
    }

    /**
     * The figures over {@code period}, from the totals of its records, or from none when it holds no
     * record.
     */
    public static PlantEnergyMetrics of(EnergyMetricsPeriod period, Optional<ProductionAggregate> aggregate) {
        return new PlantEnergyMetrics(period,
                aggregate.map(ProductionAggregate::productionKWh).orElse(null),
                aggregate.map(ProductionAggregate::hoursWithData).orElse(0L),
                period.getExpectedHours());
    }

    /**
     * The figures when no period could be resolved: no bounds, no production, no hour.
     */
    public static PlantEnergyMetrics withoutPeriod() {
        return new PlantEnergyMetrics(null, null, 0L, 0L);
    }

    /**
     * The period the figures are computed over, or empty when none could be resolved.
     */
    public Optional<EnergyMetricsPeriod> getPeriod() {
        return Optional.ofNullable(period);
    }

    /**
     * The production of the plant's backing supply over the period, in kWh, unrounded; null when no
     * record was stored in it.
     */
    public Double getProductionKWh() {
        return productionKWh;
    }

    /**
     * The number of hourly production records stored in the period, zeros included.
     */
    public long getHoursWithData() {
        return hoursWithData;
    }

    /**
     * The number of hours the period spans.
     */
    public long getExpectedHours() {
        return expectedHours;
    }
}

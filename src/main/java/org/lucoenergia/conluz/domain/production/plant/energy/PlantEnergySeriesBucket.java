package org.lucoenergia.conluz.domain.production.plant.energy;

import org.lucoenergia.conluz.domain.production.datadis.get.ProductionAggregate;

import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * One bucket of a plant's energy series -- an hour, a day or a month -- with the production of the
 * plant's backing supply in it, unrounded, its possible hours inside the period and its hours with a
 * record.
 *
 * <p>Production is null exactly when the bucket holds no record, so a gap is never reported as a zero,
 * while a stored zero, such as at night, is.</p>
 */
public class PlantEnergySeriesBucket {

    private final OffsetDateTime startsAt;
    private final Double productionKWh;
    private final long hoursWithData;
    private final long expectedHours;

    private PlantEnergySeriesBucket(OffsetDateTime startsAt, Double productionKWh, long hoursWithData,
                                    long expectedHours) {
        this.startsAt = startsAt;
        this.productionKWh = productionKWh;
        this.hoursWithData = hoursWithData;
        this.expectedHours = expectedHours;
    }

    /**
     * @param startsAt      the instant the bucket starts at, with the offset in force in the zone
     * @param expectedHours the bucket's hours inside the period
     * @param aggregate     the totals of the bucket's records, or empty when it holds none
     */
    public static PlantEnergySeriesBucket of(OffsetDateTime startsAt, long expectedHours,
                                             Optional<ProductionAggregate> aggregate) {
        return new PlantEnergySeriesBucket(startsAt,
                aggregate.map(ProductionAggregate::productionKWh).orElse(null),
                aggregate.map(ProductionAggregate::hoursWithData).orElse(0L),
                expectedHours);
    }

    public OffsetDateTime getStartsAt() {
        return startsAt;
    }

    /**
     * The production of the plant's backing supply in the bucket, in kWh, unrounded; null when no record
     * was stored in it.
     */
    public Double getProductionKWh() {
        return productionKWh;
    }

    /**
     * The number of hourly production records stored in the bucket, zeros included.
     */
    public long getHoursWithData() {
        return hoursWithData;
    }

    /**
     * The number of the bucket's hours inside the period.
     */
    public long getExpectedHours() {
        return expectedHours;
    }
}

package org.lucoenergia.conluz.domain.consumption.datadis.metrics;

import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.consumption.RecordedConsumptionPeriod;

import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * Reads aggregated totals of a supply's hourly consumption records without materialising the
 * records themselves, so the cost of a query does not grow with the length of the period.
 */
public interface GetDatadisConsumptionAggregateRepository {

    /**
     * Totals the supply's hourly consumption records between the two instants, both bounds
     * inclusive. Returns an {@link DatadisConsumptionAggregate#empty() empty aggregate} when no
     * record falls in the range.
     */
    DatadisConsumptionAggregate aggregateByRangeOfDates(Supply supply, OffsetDateTime startDate,
                                                        OffsetDateTime endDate);

    /**
     * The period actually covered by the supply's stored records, or empty when the supply has no
     * consumption record at all.
     */
    Optional<RecordedConsumptionPeriod> findRecordedPeriod(Supply supply);
}

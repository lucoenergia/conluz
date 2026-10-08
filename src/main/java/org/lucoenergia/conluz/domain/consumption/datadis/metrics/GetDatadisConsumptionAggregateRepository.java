package org.lucoenergia.conluz.domain.consumption.datadis.metrics;

import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.consumption.RecordedConsumptionPeriod;

import java.time.Instant;
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
     * Sums the supply's stored {@code self_consumption_energy_kwh} over the half-open instant
     * interval {@code [from, to)}: a record sitting exactly on {@code from} counts, one sitting
     * exactly on {@code to} does not.
     *
     * <p>Half-open, unlike {@link #aggregateByRangeOfDates}, precisely so that the adjacent
     * sub-intervals one period is cut into can be summed independently and added up: with two
     * inclusive bounds the instant on the shared boundary would be counted by both sides.
     *
     * <p>Returns {@code 0} when no record matches, when the field is absent from the
     * measurement's schema, and when the sum over it is null because none of this supply's
     * records carry it -- the same three degenerate shapes {@link #aggregateByRangeOfDates}
     * flattens to zero.
     */
    double sumSelfConsumptionKWh(Supply supply, Instant from, Instant to);

    /**
     * The period actually covered by the supply's stored records, or empty when the supply has no
     * consumption record at all.
     */
    Optional<RecordedConsumptionPeriod> findRecordedPeriod(Supply supply);

    /**
     * The first and the last of the supply's hourly records in the half-open interval
     * {@code [from, toExclusive)} whose assigned production has been published, or empty when none
     * has.
     *
     * <p>A record is published when it carries {@code self_consumption_energy_kwh}, zero included.
     * Datadis publishes a month's self-consumption only some days after the month ends; until then
     * the month's records carry consumption and the surplus the meter measured, but no
     * self-consumption at all. Surplus alone is therefore not publication.
     */
    Optional<RecordedConsumptionPeriod> findPublishedPeriod(Supply supply, Instant from, Instant toExclusive);

    /**
     * The number of the supply's hourly records in the half-open interval
     * {@code [from, toExclusive)} whose assigned production has been published, in the sense of
     * {@link #findPublishedPeriod}. Zero when there is none.
     */
    long countPublishedHours(Supply supply, Instant from, Instant toExclusive);

    /**
     * The timestamp of the supply's latest hourly record in the half-open interval
     * {@code [from, toExclusive)} that carries assigned production: a {@code self_consumption_energy_kwh} or a {@code surplus_energy_kwh}
     * greater than zero. Either field is enough, so a record whose assigned production was fed back
     * to the grid in full still counts. Empty when no such record exists.
     *
     * <p>Datadis publishes both fields for a month only some days after it ends. Until then the
     * month's records carry consumption alone, with those fields absent or zero, so this is the
     * latest instant whose assigned production has been published.
     */
    Optional<Instant> findLatestAssignedProductionRecord(Supply supply, Instant from, Instant toExclusive);
}

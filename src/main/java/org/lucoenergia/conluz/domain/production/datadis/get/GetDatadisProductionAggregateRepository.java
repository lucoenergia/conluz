package org.lucoenergia.conluz.domain.production.datadis.get;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reads totals of the hourly Datadis production of plant supplies without materialising the records
 * themselves, so the cost of a query does not grow with the length of the period.
 */
public interface GetDatadisProductionAggregateRepository {

    /**
     * The production stored for each of the given supply codes (CUPS) in the half-open interval
     * {@code [from, toExclusive)}, summed per code and keyed by it.
     *
     * <p>A code without any production record in the interval is absent from the map, which is not
     * the same as a stored zero: a code whose records all carry zero maps to {@code 0}. An empty
     * collection issues no query.
     */
    Map<String, Double> sumProductionByCups(Collection<String> cups, Instant from, Instant toExclusive);

    /**
     * The production stored for the given supply codes (CUPS) in the half-open interval
     * {@code [from, toExclusive)}, summed across the codes per calendar year of {@code zone} and keyed by
     * the year.
     *
     * <p>A year without any production record in the interval is absent from the map; a year whose
     * records all carry zero maps to {@code 0}. An empty collection issues no query.
     */
    Map<Integer, Double> sumProductionByYear(Collection<String> cups, Instant from, Instant toExclusive,
                                             ZoneId zone);
    /**
     * The production stored for one supply code (CUPS) in the half-open interval
     * {@code [from, toExclusive)}, summed, with the number of records it rests on.
     *
     * @return the totals, or empty when the interval holds no record, which is not the same as a
     *         stored zero
     */
    Optional<ProductionAggregate> aggregate(String cups, Instant from, Instant toExclusive);

    /**
     * The production stored for one supply code (CUPS) in the half-open interval
     * {@code [from, toExclusive)}, summed per local day of {@code zone}, with the number of records
     * each day rests on, in chronological order.
     *
     * <p>A day without any record in the interval is absent from the list; a day whose records all
     * carry zero is present with {@code 0}.
     */
    List<DailyProductionAggregate> aggregateByLocalDay(String cups, Instant from, Instant toExclusive,
                                                       ZoneId zone);

    /**
     * The instants of the first and the last production record stored for one supply code (CUPS), or
     * empty when it has none.
     */
    Optional<RecordedProductionPeriod> findRecordedPeriod(String cups);
}

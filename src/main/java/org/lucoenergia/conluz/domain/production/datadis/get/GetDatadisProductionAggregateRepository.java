package org.lucoenergia.conluz.domain.production.datadis.get;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;

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
}

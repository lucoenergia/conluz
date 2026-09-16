package org.lucoenergia.conluz.domain.consumption.savings;

import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.consumption.SupplySavings;

import java.time.Instant;

/**
 * Prices what a supply's self-consumed energy was worth over an instant interval, segment by
 * segment of the tariff schedule covering it.
 *
 * <p>The interval is half-open, {@code [from, toExclusive)}, which is the same convention
 * {@code GetDatadisConsumptionAggregateRepository#sumSelfConsumptionKWh} uses: adjacent
 * sub-intervals can therefore be priced independently and added without double-counting the
 * instant they share. Callers holding an API-level <em>inclusive</em> end must convert it once,
 * at the top of their service, with {@code DateConverter.toExclusiveUpperBound}.
 *
 * <p>Amounts come back unrounded: rounding is presentational and belongs where the response is
 * built, so a period cut into several segments -- or a figure summed over several supplies -- is
 * not rounded once per part.
 */
public interface SupplySavingsCalculator {

    /**
     * Prices {@code [from, toExclusive)} for the supply, issuing one aggregate query per tariff
     * segment that overlaps the interval.
     *
     * @param from        the inclusive start of the interval
     * @param toExclusive the exclusive end; must be strictly after {@code from}
     */
    SupplySavings estimate(Supply supply, Instant from, Instant toExclusive);

    /**
     * Prices the same interval when the caller has already summed the supply's self-consumption
     * over exactly it.
     *
     * <p>A schedule of a single segment necessarily covers the whole interval, so that segment's
     * energy <em>is</em> {@code totalSelfConsumptionKWh}: reusing the figure keeps the amount
     * consistent with the caller's own reported energy to the last bit and costs no second query.
     * A schedule of several segments still queries per segment, since the total says nothing about
     * how the energy is distributed across them.
     */
    SupplySavings estimate(Supply supply, Instant from, Instant toExclusive, double totalSelfConsumptionKWh);
}

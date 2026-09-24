package org.lucoenergia.conluz.domain.admin.supply.tariff;

import org.lucoenergia.conluz.domain.shared.SupplyId;

/**
 * Port that resolves the tariff applicable to a supply point over a period of time.
 *
 * <p>Given a supply and a {@link DateRange}, an implementation returns the
 * {@link TariffSchedule} describing which {@link TariffPlan} (and VAT) applies
 * across that span. Different implementations can source this information in
 * different ways &mdash; for example {@code EstimatedSupplyTariffResolver}
 * produces a flat-rate estimate when the real contracted tariff is unknown.
 */
public interface SupplyTariffResolver {

    /**
     * Returns the tariff applicable to {@code supply} across {@code range}.
     *
     * <p>Postcondition, binding on every implementation: the returned schedule
     * <strong>covers the requested range in its entirety</strong> -- its first segment starts
     * no later than {@code range.getStart()}, its last segment ends no earlier than
     * {@code range.getEnd()}, and there is no gap in between. A consumer may therefore price
     * every day of the range without checking for holes; a day with no segment would
     * otherwise be silently billed at zero.
     *
     * <p>An implementation that cannot price the whole range must fail rather than return a
     * partial schedule: {@link TariffSchedule} rejects empty and gapped segment lists at
     * construction, so a partial answer cannot be expressed.
     */
    TariffSchedule scheduleFor(SupplyId supply, DateRange range);
}

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

    TariffSchedule scheduleFor(SupplyId supply, DateRange range);
}

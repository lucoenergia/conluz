package org.lucoenergia.conluz.domain.admin.supply.tariff;

import org.lucoenergia.conluz.domain.shared.SupplyId;

public interface SupplyTariffResolver {

    TariffSchedule scheduleFor(SupplyId supply, DateRange range);
}

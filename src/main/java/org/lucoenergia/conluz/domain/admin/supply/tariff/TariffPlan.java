package org.lucoenergia.conluz.domain.admin.supply.tariff;

import java.math.BigDecimal;

/**
 * Marker abstraction for the pricing scheme that determines how much a kWh
 * costs. It is the "plan" attached to a {@link TariffSegment}.
 *
 * <p>Concrete plans model the different pricing structures supported by the
 * domain: {@link FlatPlan} (a single constant price) and {@link TimeOfUsePlan}
 * (price varying by time period). The {@link #flat(BigDecimal)} factory offers a
 * concise way to build the common flat-rate case.
 */
public interface TariffPlan {

    static FlatPlan flat(BigDecimal pricePerKwh) {
        return new FlatPlan(pricePerKwh);
    }
}

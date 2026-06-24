package org.lucoenergia.conluz.domain.admin.supply.tariff;

import java.math.BigDecimal;

public interface TariffPlan {

    static FlatPlan flat(BigDecimal pricePerKwh) {
        return new FlatPlan(pricePerKwh);
    }
}

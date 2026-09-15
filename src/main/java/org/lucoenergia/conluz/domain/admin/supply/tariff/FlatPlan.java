package org.lucoenergia.conluz.domain.admin.supply.tariff;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * A {@link TariffPlan} that charges a single, constant price per kWh regardless
 * of the time of day. This is the simplest pricing scheme and the one used for
 * estimated tariffs when no detailed time-of-use information is available.
 *
 * @see TimeOfUsePlan
 */
public class FlatPlan implements TariffPlan {

    private final BigDecimal pricePerKwh;

    public FlatPlan(BigDecimal pricePerKwh) {
        this.pricePerKwh = pricePerKwh;
    }

    public BigDecimal getPricePerKwh() {
        return pricePerKwh;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FlatPlan)) return false;
        FlatPlan flatPlan = (FlatPlan) o;
        return Objects.equals(pricePerKwh, flatPlan.pricePerKwh);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(pricePerKwh);
    }

    @Override
    public String toString() {
        return "FlatPlan{" +
                "pricePerKwh=" + pricePerKwh +
                '}';
    }
}

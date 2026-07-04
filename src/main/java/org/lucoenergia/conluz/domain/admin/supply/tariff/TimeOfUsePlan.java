package org.lucoenergia.conluz.domain.admin.supply.tariff;

/**
 * A {@link TariffPlan} whose price per kWh depends on the time of use (e.g.
 * peak/off-peak periods), as opposed to the constant price of a {@link FlatPlan}.
 *
 * <p>It currently acts as a placeholder type in the tariff model &mdash; it
 * carries no period/price data yet &mdash; and all instances are considered
 * equal. The per-period rates are expected to be added as the model evolves.
 */
public class TimeOfUsePlan implements TariffPlan {

    @Override
    public boolean equals(Object obj) {
        return obj instanceof TimeOfUsePlan;
    }

    @Override
    public int hashCode() {
        return TimeOfUsePlan.class.hashCode();
    }

    @Override
    public String toString() {
        return "TimeOfUsePlan{}";
    }
}

package org.lucoenergia.conluz.domain.admin.supply.tariff;

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

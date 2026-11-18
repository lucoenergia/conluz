package org.lucoenergia.conluz.domain.admin.supply.energytoday;

/**
 * Whether today's production assigned to a supply point can be shown, and why not when it cannot. The first status
 * that applies, in declaration order, is the one reported.
 */
public enum SupplyProductionTodayStatus {

    /**
     * The supply has no partition coefficient in force at any instant of the current local day so far, in any plant.
     */
    NO_COEFFICIENT_TODAY,

    /**
     * None of the plants where the supply has a coefficient in force today has an enabled Huawei configuration.
     */
    NOT_AVAILABLE,

    /**
     * At least one of those plants has an enabled Huawei configuration, but none has a snapshot of the current local
     * day.
     */
    NO_DATA_TODAY,

    /**
     * At least one of those plants has an enabled Huawei configuration and a snapshot of the current local day.
     */
    AVAILABLE
}

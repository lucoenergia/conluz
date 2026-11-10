package org.lucoenergia.conluz.domain.production.plant.inverter;

/**
 * Whether today's inverter production of a plant can be shown, and why not when it cannot.
 */
public enum InverterProductionStatus {

    /**
     * The plant has an enabled Huawei configuration and at least one snapshot of the current local day.
     */
    AVAILABLE,

    /**
     * The plant has an enabled Huawei configuration, but no snapshot of the current local day.
     */
    NO_DATA_TODAY,

    /**
     * The plant has no Huawei configuration, or it is disabled.
     */
    NOT_AVAILABLE
}

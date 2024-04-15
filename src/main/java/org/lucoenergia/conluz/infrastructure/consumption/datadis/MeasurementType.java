package org.lucoenergia.conluz.infrastructure.consumption.datadis;

/**
 * Set it to 0 (Zero) if you want to get the consumption per hour and to 1 (One) if you want to get the consumption per
 * quarter-hour. The hourly query is only available for PointTypes 1 and 2, and in the case of the distributor
 * E-distribución additionally for PointType 3.
 */
public class MeasurementType {

    private MeasurementType() {
    }

    public static final String PER_HOUR = "0";
    public static final String PER_QUARTER_HOUR = "1";
}

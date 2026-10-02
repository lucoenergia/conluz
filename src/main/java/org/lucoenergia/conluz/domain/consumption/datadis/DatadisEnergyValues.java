package org.lucoenergia.conluz.domain.consumption.datadis;

import java.math.BigDecimal;

/**
 * Reads the energy figures {@link DatadisConsumption} carries as {@code Float}.
 */
public final class DatadisEnergyValues {

    private DatadisEnergyValues() {
    }

    /**
     * The figure exactly as Datadis reported it, without the binary error widening the float would
     * carry into digits the caller never sees -- {@code 2.37f} widens to {@code 2.3700001239776611}.
     * The value is read back through the float's shortest decimal representation, which is the very
     * text a response carries. A missing figure reads as zero.
     */
    public static BigDecimal exactlyAsReported(Float kWh) {
        return kWh == null ? BigDecimal.ZERO : new BigDecimal(kWh.toString());
    }
}

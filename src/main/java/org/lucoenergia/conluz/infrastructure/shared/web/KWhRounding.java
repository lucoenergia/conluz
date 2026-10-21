package org.lucoenergia.conluz.infrastructure.shared.web;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Rounds an energy figure for a response, the one place kWh values lose precision (#385).
 *
 * <p>Rounding belongs to response mapping only: stored values, sums across supplies or periods,
 * ratios and savings are all computed from unrounded figures, and the final figure is rounded once,
 * here. Three decimals is the resolution the distributor reports in, so nothing it measured is lost;
 * what goes is the binary noise a floating-point sum leaves behind ({@code 0.1 + 0.2} reads
 * {@code 0.30000000000000004}).
 */
public final class KWhRounding {

    private static final int DECIMALS = 3;

    private KWhRounding() {
    }

    /**
     * The figure rounded to 3 decimals, half-up. The figure is read through its shortest decimal
     * representation rather than its exact binary value, so a figure that reads {@code 1.0005} is a
     * tie and rounds up, although the double nearest to it is slightly below.
     */
    public static double round(double kWh) {
        return BigDecimal.valueOf(kWh).setScale(DECIMALS, RoundingMode.HALF_UP).doubleValue();
    }
}

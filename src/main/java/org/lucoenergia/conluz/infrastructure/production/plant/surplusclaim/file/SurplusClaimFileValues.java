package org.lucoenergia.conluz.infrastructure.production.plant.surplusclaim.file;

import org.lucoenergia.conluz.domain.production.plant.surplusclaim.SurplusClaimDayState;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * How every surplus claim file writes a day's state and an hour's value.
 */
final class SurplusClaimFileValues {

    private SurplusClaimFileValues() {
    }

    /**
     * {@code R} for a real day, {@code E} for an estimated one.
     */
    static String state(SurplusClaimDayState state) {
        return switch (state) {
            case REAL -> "R";
            case ESTIMATED -> "E";
        };
    }

    /**
     * The unrounded value rounded half-up to the given decimals, once.
     */
    static BigDecimal round(double value, int decimals) {
        return BigDecimal.valueOf(value).setScale(decimals, RoundingMode.HALF_UP);
    }
}

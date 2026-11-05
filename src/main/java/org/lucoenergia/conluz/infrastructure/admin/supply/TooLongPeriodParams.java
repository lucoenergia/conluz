package org.lucoenergia.conluz.infrastructure.admin.supply;

import java.time.Period;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The {@code params} of an {@code ENERGY_METRICS_PERIOD_TOO_LONG} refusal: the longest period the
 * operation accepts, as an {@code amount} of a single {@code unit}, so that a client can state the
 * limit without knowing which operation enforces which one.
 */
final class TooLongPeriodParams {

    static final String AMOUNT = "amount";
    static final String UNIT = "unit";
    static final String MONTHS = "MONTHS";
    static final String DAYS = "DAYS";

    private TooLongPeriodParams() {
    }

    /**
     * {@code P24M} gives {@code {amount: "24", unit: "MONTHS"}} and {@code P31D}
     * {@code {amount: "31", unit: "DAYS"}}; the amount is written as a string, as every value of
     * {@code params} is.
     *
     * @throws IllegalStateException when the period is not a number of months alone or of days alone:
     *         every limit an operation enforces is one of those, so anything else is a programming error
     */
    static Map<String, String> of(Period maxLength) {
        if (maxLength.getYears() == 0 && maxLength.getDays() == 0 && maxLength.getMonths() > 0) {
            return params(maxLength.getMonths(), MONTHS);
        }
        if (maxLength.getYears() == 0 && maxLength.getMonths() == 0 && maxLength.getDays() > 0) {
            return params(maxLength.getDays(), DAYS);
        }
        throw new IllegalStateException(
                "The longest period an operation accepts must be counted in months alone or in days alone, but was "
                + maxLength);
    }

    private static Map<String, String> params(int amount, String unit) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put(AMOUNT, String.valueOf(amount));
        params.put(UNIT, unit);
        return params;
    }
}

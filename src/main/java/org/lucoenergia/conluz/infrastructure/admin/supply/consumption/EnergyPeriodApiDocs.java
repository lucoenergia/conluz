package org.lucoenergia.conluz.infrastructure.admin.supply.consumption;

/**
 * The description of a requested period, shared word for word by every operation that accepts one
 * for energy metrics or a consumption series, so that all of them document the same convention.
 */
public final class EnergyPeriodApiDocs {

    /**
     * The bounds convention, as a paragraph of an operation description.
     */
    public static final String RANGE_BOUNDS = """
            **Range bounds:** the period is half-open, `[startDate, endDate)`: `startDate` is
            included and `endDate` is not, so a record stamped exactly at `endDate` belongs to the
            next period. A calendar day runs from `00:00` of that day to `00:00` of the following
            day, and a calendar month from `00:00` of its first day to `00:00` of the first day of
            the next month, each bound with the offset in force at that instant -- for example
            `2026-08-01T00:00:00+02:00` to `2026-09-01T00:00:00+02:00` for August 2026 in
            Europe/Madrid.

            **Period checks:** a requested period is checked in this order, and the first rule it
            breaks is answered 400 with that rule's code:

            1. `period` is not combined with `startDate` or `endDate`, on the operations that accept
               `period` (`ENERGY_METRICS_PERIOD_CONFLICTING`).
            2. `startDate` and `endDate` are supplied together (`ENERGY_METRICS_PERIOD_INCOMPLETE`).
               On the operations where both are required, a missing one is a bad request before any
               of these checks.
            3. `startDate` is strictly before `endDate`: equal bounds make an empty period
               (`ENERGY_METRICS_PERIOD_START_AFTER_END`).
            4. `startDate` is before the start of the current hour, read in the period's time zone
               (`ENERGY_METRICS_PERIOD_START_IN_FUTURE`).
            5. `endDate` is no later than 24 calendar months after `startDate`, counted in the
               period's time zone on the bounds as requested (`ENERGY_METRICS_PERIOD_TOO_LONG`).
            """;

    private EnergyPeriodApiDocs() {
    }
}

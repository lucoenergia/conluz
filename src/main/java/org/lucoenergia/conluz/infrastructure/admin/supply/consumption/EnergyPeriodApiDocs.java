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
            """;

    private EnergyPeriodApiDocs() {
    }
}

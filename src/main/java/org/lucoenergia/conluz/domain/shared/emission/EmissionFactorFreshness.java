package org.lucoenergia.conluz.domain.shared.emission;

import java.time.LocalDate;
import java.time.Month;
import java.util.Set;

/**
 * Whether the configured emission factors keep up with the MITECO publication calendar.
 *
 * <p>MITECO publishes each year's factors in May of the following year, so from 1 June of year Y the
 * factors must include year Y − 1, and before that date year Y − 2.
 */
public final class EmissionFactorFreshness {

    private static final Month FIRST_MONTH_AFTER_PUBLICATION = Month.JUNE;

    private EmissionFactorFreshness() {
    }

    /**
     * @param configuredYears the years with a configured factor
     * @param dateInMadrid    the date to check against, in Europe/Madrid
     */
    public static Result check(Set<Integer> configuredYears, LocalDate dateInMadrid) {
        boolean published = !dateInMadrid.isBefore(LocalDate.of(dateInMadrid.getYear(),
                FIRST_MONTH_AFTER_PUBLICATION, 1));
        int requiredYear = dateInMadrid.getYear() - (published ? 1 : 2);
        return new Result(requiredYear, configuredYears.contains(requiredYear));
    }

    /**
     * @param requiredYear the latest year whose factor must be configured on the date checked
     * @param configured   whether it is
     */
    public record Result(int requiredYear, boolean configured) {
    }
}

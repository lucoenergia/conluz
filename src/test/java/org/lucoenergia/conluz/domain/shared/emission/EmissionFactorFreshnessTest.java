package org.lucoenergia.conluz.domain.shared.emission;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The freshness rule with fixed dates, so it runs in the regular build. The check of the shipped factors
 * against today's date is {@code EmissionFactorFreshnessCheckTest}, outside it.
 */
class EmissionFactorFreshnessTest {

    private static final Set<Integer> UP_TO_2025 = Set.of(2021, 2022, 2023, 2024, 2025);
    private static final Set<Integer> UP_TO_2026 = Set.of(2021, 2022, 2023, 2024, 2025, 2026);

    @ParameterizedTest(name = "{0} requires {1}")
    @CsvSource({
            "2027-01-01, 2025",
            "2027-05-31, 2025",
            "2027-06-01, 2026",
            "2027-12-31, 2026",
            "2026-11-09, 2025"
    })
    @DisplayName("CEM-016 the required year is Y - 1 from 1 June of Y, and Y - 2 before")
    void theRequiredYearFollowsThePublicationCalendar(LocalDate date, int requiredYear) {
        assertEquals(requiredYear, EmissionFactorFreshness.check(UP_TO_2025, date).requiredYear());
    }

    @ParameterizedTest(name = "factors up to {0} on {1}: configured = {2}")
    @CsvSource({
            "2025, 2027-05-31, true",
            "2025, 2027-06-01, false",
            "2026, 2027-06-01, true",
            "2026, 2028-05-31, true",
            "2026, 2028-06-01, false"
    })
    @DisplayName("CEM-016 AC10 the check fails from the day the next year is due")
    void theCheckFailsFromTheDayTheNextYearIsDue(int lastYear, LocalDate date, boolean configured) {
        Set<Integer> years = lastYear == 2025 ? UP_TO_2025 : UP_TO_2026;

        EmissionFactorFreshness.Result result = EmissionFactorFreshness.check(years, date);

        assertEquals(configured, result.configured());
    }
}

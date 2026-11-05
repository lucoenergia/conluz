package org.lucoenergia.conluz.infrastructure.admin.supply;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.lucoenergia.conluz.domain.consumption.EnergyMetricsPeriodValidator;

import java.time.Period;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TooLongPeriodParamsTest {

    @Test
    @DisplayName("EPR-004 the 24-month limit is reported as 24 MONTHS")
    void theTwentyFourMonthLimitIsReportedInMonths() {
        Map<String, String> params = TooLongPeriodParams.of(EnergyMetricsPeriodValidator.MAX_LENGTH);

        assertEquals(Map.of("amount", "24", "unit", "MONTHS"), params);
        assertEquals(List.of("amount", "unit"), List.copyOf(params.keySet()));
    }

    @Test
    @DisplayName("EPR-005 the 31-day limit of the hourly series is reported as 31 DAYS")
    void theThirtyOneDayLimitIsReportedInDays() {
        assertEquals(Map.of("amount", "31", "unit", "DAYS"),
                TooLongPeriodParams.of(EnergyMetricsPeriodValidator.MAX_HOURLY_LENGTH));
    }

    /**
     * Every limit an operation enforces is a number of months alone or of days alone: one mixing
     * units, counted in years, or empty is a programming error, not a refusal to report.
     */
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"P1M1D", "P2Y", "P1Y6M", "P0D"})
    @DisplayName("EPR-004 EPR-005 a limit that is not months alone or days alone is a broken invariant")
    void aLimitMixingUnitsOrWithNoneIsABrokenInvariant(String period) {
        assertThrows(IllegalStateException.class, () -> TooLongPeriodParams.of(Period.parse(period)));
    }
}

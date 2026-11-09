package org.lucoenergia.conluz.domain.admin.community.energy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.shared.emission.EmissionFactors;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Each test builds its own factors: the shipped ones are only read by the configuration tests.
 */
class AvoidedEmissionsTest {

    private static final EmissionFactors FACTORS_2024_2025 = EmissionFactors.of(Map.of(
            2024, new BigDecimal("0.200"),
            2025, new BigDecimal("0.150")));

    @Test
    @DisplayName("CEM-011 CEM-013 AC1 1,000 kWh in 2025 at 0.150 avoid 150 kg, with the factor year 2025")
    void aPeriodInsideOneYearUsesThatYearsFactor() {
        AvoidedEmissions avoided = AvoidedEmissions.estimate(Map.of(2025, 1000d), FACTORS_2024_2025);

        assertAmount("150", avoided);
        assertEquals(List.of(2025), avoided.getFactorYears());
    }

    @Test
    @DisplayName("CEM-011 CEM-013 AC2 400 kWh in 2024 and 600 in 2025 avoid 170 kg, factor years 2024 and 2025")
    void eachYearUsesItsOwnFactorAndTheProductsAreSummed() {
        AvoidedEmissions avoided = AvoidedEmissions.estimate(Map.of(2024, 400d, 2025, 600d), FACTORS_2024_2025);

        assertAmount("170", avoided);
        assertEquals(List.of(2024, 2025), avoided.getFactorYears());
    }

    @Test
    @DisplayName("CEM-011 AC6 the per-year products are summed unrounded")
    void theProductsAreSummedUnrounded() {
        EmissionFactors factors = EmissionFactors.of(Map.of(2024, new BigDecimal("0.0002"),
                2025, new BigDecimal("0.0002")));

        // 0.0006 kg each: rounding each product to 3 decimals first would give 0.001 + 0.001 = 0.002, where
        // the unrounded sum, 0.0012, rounds to 0.001.
        AvoidedEmissions avoided = AvoidedEmissions.estimate(Map.of(2024, 3d, 2025, 3d), factors);

        assertAmount("0.0012", avoided);
    }

    @Test
    @DisplayName("CEM-012 AC3 a year after the last factor uses the latest earlier one")
    void aYearAfterTheLastFactorUsesTheLatestEarlierOne() {
        AvoidedEmissions avoided = AvoidedEmissions.estimate(Map.of(2026, 1000d), FACTORS_2024_2025);

        assertAmount("150", avoided);
        assertEquals(List.of(2025), avoided.getFactorYears());
    }

    @Test
    @DisplayName("CEM-012 a year between two factors uses the earlier one, never the later")
    void aYearBetweenTwoFactorsUsesTheEarlierOne() {
        EmissionFactors factors = EmissionFactors.of(Map.of(2021, new BigDecimal("0.300"),
                2024, new BigDecimal("0.200")));

        AvoidedEmissions avoided = AvoidedEmissions.estimate(Map.of(2022, 10d, 2023, 20d), factors);

        assertAmount("9", avoided);
        assertEquals(List.of(2021), avoided.getFactorYears());
    }

    @Test
    @DisplayName("CEM-012 AC4 a year with production before every factor makes the figure null")
    void aYearBeforeEveryFactorMakesTheFigureNull() {
        EmissionFactors factors = EmissionFactors.of(Map.of(2021, new BigDecimal("0.259"),
                2022, new BigDecimal("0.273")));

        assertNull(AvoidedEmissions.estimate(Map.of(2020, 100d, 2021, 200d), factors));
    }

    @Test
    @DisplayName("CEM-013 the factor years are distinct and ascending, whatever the order of the years")
    void theFactorYearsAreDistinctAndAscending() {
        Map<Integer, Double> productionByYear = new LinkedHashMap<>();
        productionByYear.put(2027, 1d);
        productionByYear.put(2024, 1d);
        productionByYear.put(2026, 1d);
        productionByYear.put(2025, 1d);

        AvoidedEmissions avoided = AvoidedEmissions.estimate(productionByYear, FACTORS_2024_2025);

        assertEquals(List.of(2024, 2025), avoided.getFactorYears());
    }

    @Test
    @DisplayName("CEM-011 CEM-013 a year without a production record neither adds nor applies a factor")
    void aYearWithoutAProductionRecordAppliesNoFactor() {
        // A period from November 2019 to March 2021 whose records are all in 2021: 2019 and 2020 are absent,
        // so they neither make the figure null nor appear among the factor years.
        EmissionFactors factors = EmissionFactors.of(Map.of(2021, new BigDecimal("0.259"),
                2022, new BigDecimal("0.273")));

        AvoidedEmissions avoided = AvoidedEmissions.estimate(Map.of(2021, 100d), factors);

        assertAmount("25.9", avoided);
        assertEquals(List.of(2021), avoided.getFactorYears());
    }

    @Test
    @DisplayName("CEM-011 CEM-013 a year whose only records are zero counts, and applies its factor")
    void aYearOfStoredZerosCounts() {
        AvoidedEmissions avoided = AvoidedEmissions.estimate(Map.of(2024, 0d, 2025, 1000d), FACTORS_2024_2025);

        assertAmount("150", avoided);
        assertEquals(List.of(2024, 2025), avoided.getFactorYears());
    }

    @Test
    @DisplayName("CEM-012 a year of stored zeros before every factor still makes the figure null")
    void aYearOfStoredZerosBeforeEveryFactorMakesTheFigureNull() {
        assertNull(AvoidedEmissions.estimate(Map.of(2023, 0d, 2024, 1000d), FACTORS_2024_2025));
    }

    @Test
    @DisplayName("CEM-014 without any production record there is nothing to estimate")
    void withoutAnyProductionRecordTheFigureIsNull() {
        assertNull(AvoidedEmissions.estimate(Map.of(), FACTORS_2024_2025));
    }

    private static void assertAmount(String expected, AvoidedEmissions avoided) {
        assertEquals(0, new BigDecimal(expected).compareTo(avoided.getAmountKg()),
                () -> "expected " + expected + ", was " + avoided.getAmountKg());
    }
}

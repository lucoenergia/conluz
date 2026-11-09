package org.lucoenergia.conluz.domain.shared.emission;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EmissionFactorsTest {

    private final EmissionFactors factors = EmissionFactors.of(Map.of(
            2025, new BigDecimal("0.258"),
            2021, new BigDecimal("0.259"),
            2023, new BigDecimal("0.260")));

    @Test
    @DisplayName("CEM-012 a year with a factor applies its own")
    void aYearWithAFactorAppliesItsOwn() {
        assertEquals(Optional.of(2023), factors.appliedYearFor(2023));
        assertEquals(Optional.of(2025), factors.appliedYearFor(2025));
    }

    @Test
    @DisplayName("CEM-012 a year without a factor applies the latest earlier one")
    void aYearWithoutAFactorAppliesTheLatestEarlierOne() {
        assertEquals(Optional.of(2021), factors.appliedYearFor(2022));
        assertEquals(Optional.of(2023), factors.appliedYearFor(2024));
        assertEquals(Optional.of(2025), factors.appliedYearFor(2027));
    }

    @Test
    @DisplayName("CEM-012 a year before every factor applies none")
    void aYearBeforeEveryFactorAppliesNone() {
        assertEquals(Optional.empty(), factors.appliedYearFor(2020));
    }

    @Test
    void theYearsAreAscending() {
        assertEquals(List.of(2021, 2023, 2025), List.copyOf(factors.years()));
    }

    @Test
    void theFactorOfAYearWithoutOneIsRefused() {
        assertEquals(0, new BigDecimal("0.260").compareTo(factors.factorOf(2023)));
        assertThrows(IllegalArgumentException.class, () -> factors.factorOf(2024));
    }
}

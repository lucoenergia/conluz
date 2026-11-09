package org.lucoenergia.conluz.domain.shared.emission;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeMap;

/**
 * The electricity emission factors, one per calendar year, in kg CO₂e/kWh.
 *
 * <p>A year without a factor of its own takes the factor of the latest earlier year that has one; a year
 * earlier than every configured year has none.
 */
public final class EmissionFactors {

    private final TreeMap<Integer, BigDecimal> kgCo2ePerKWhByYear;

    private EmissionFactors(TreeMap<Integer, BigDecimal> kgCo2ePerKWhByYear) {
        this.kgCo2ePerKWhByYear = kgCo2ePerKWhByYear;
    }

    /**
     * @param kgCo2ePerKWhByYear the factor of each year, in kg CO₂e/kWh, keyed by the year
     */
    public static EmissionFactors of(Map<Integer, BigDecimal> kgCo2ePerKWhByYear) {
        return new EmissionFactors(new TreeMap<>(kgCo2ePerKWhByYear));
    }

    /**
     * The year whose factor applies to {@code year}: the year itself when it has a factor, otherwise the
     * latest earlier year that has one, or empty when every configured year is later.
     */
    public Optional<Integer> appliedYearFor(int year) {
        return Optional.ofNullable(kgCo2ePerKWhByYear.floorKey(year));
    }

    /**
     * The factor configured for {@code year}, in kg CO₂e/kWh.
     *
     * @throws IllegalArgumentException when no factor is configured for that year
     */
    public BigDecimal factorOf(int year) {
        BigDecimal factor = kgCo2ePerKWhByYear.get(year);
        if (factor == null) {
            throw new IllegalArgumentException("No emission factor is configured for year " + year);
        }
        return factor;
    }

    /**
     * Every year with a configured factor, in ascending order.
     */
    public SortedSet<Integer> years() {
        return Collections.unmodifiableSortedSet(kgCo2ePerKWhByYear.navigableKeySet());
    }
}

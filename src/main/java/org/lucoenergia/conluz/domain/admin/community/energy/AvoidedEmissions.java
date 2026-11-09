package org.lucoenergia.conluz.domain.admin.community.energy;

import org.lucoenergia.conluz.domain.shared.emission.EmissionFactors;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * The CO₂ the community's plants avoided: the emissions their production would have caused had the same
 * electricity been bought from the grid, and the factor years that were applied.
 *
 * <p>Each local calendar year's production is multiplied by the factor applied to that year, its own or the
 * latest earlier one, and the products are summed. Only the years that hold a production record count: a
 * year without any adds nothing and applies no factor. Nothing is rounded here: the amount is summed at full
 * precision and left to the response to round once.
 */
public class AvoidedEmissions {

    private final BigDecimal amountKg;
    private final List<Integer> factorYears;

    private AvoidedEmissions(BigDecimal amountKg, List<Integer> factorYears) {
        this.amountKg = amountKg;
        this.factorYears = factorYears;
    }

    /**
     * @param productionByYear the production of the plants in each local calendar year, in kWh, keyed by
     *                         the year; a year without any production record is absent
     * @param factors          the emission factors to apply
     * @return the avoided emissions, or null when no year holds a production record, or when one of them is
     * earlier than every configured factor
     */
    public static AvoidedEmissions estimate(Map<Integer, Double> productionByYear, EmissionFactors factors) {
        if (productionByYear.isEmpty()) {
            return null;
        }
        BigDecimal amount = BigDecimal.ZERO;
        SortedSet<Integer> appliedYears = new TreeSet<>();
        for (Map.Entry<Integer, Double> year : productionByYear.entrySet()) {
            Optional<Integer> appliedYear = factors.appliedYearFor(year.getKey());
            if (appliedYear.isEmpty()) {
                return null;
            }
            appliedYears.add(appliedYear.get());
            amount = amount.add(BigDecimal.valueOf(year.getValue()).multiply(factors.factorOf(appliedYear.get())));
        }
        return new AvoidedEmissions(amount, List.copyOf(appliedYears));
    }

    /**
     * The avoided emissions, in kg CO₂e, unrounded.
     */
    public BigDecimal getAmountKg() {
        return amountKg;
    }

    /**
     * The distinct years whose factors were applied, in ascending order.
     */
    public List<Integer> getFactorYears() {
        return factorYears;
    }
}

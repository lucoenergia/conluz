package org.lucoenergia.conluz.domain.admin.community.energy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * An estimate of what a community's surplus earned at OMIE day-ahead prices, and the surplus that could
 * not be priced.
 *
 * <p>Each hour's surplus is multiplied by the mean of the prices stored for that hour, negative and zero
 * prices applied as they are. An hour without a price adds nothing to the amount; its surplus is
 * counted as unpriced instead. Nothing is rounded here: the amount is summed at full precision and left
 * to the response to round once.
 */
public class SurplusIncome {

    private final BigDecimal amountEur;
    private final double unpricedSurplusKWh;

    private SurplusIncome(BigDecimal amountEur, double unpricedSurplusKWh) {
        this.amountEur = amountEur;
        this.unpricedSurplusKWh = unpricedSurplusKWh;
    }

    /**
     * @param surplusByHour   the community's surplus in each hour, in kWh, keyed by the instant the hour
     *                        starts at
     * @param meanPriceByHour the mean OMIE price of each hour, in €/kWh, keyed the same way; an hour
     *                        without a stored price is absent
     */
    public static SurplusIncome estimate(Map<Instant, Double> surplusByHour, Map<Instant, Double> meanPriceByHour) {
        BigDecimal amount = BigDecimal.ZERO;
        double unpriced = 0d;
        for (Map.Entry<Instant, Double> hour : surplusByHour.entrySet()) {
            Double price = meanPriceByHour.get(hour.getKey());
            if (price == null) {
                unpriced += hour.getValue();
            } else {
                amount = amount.add(BigDecimal.valueOf(hour.getValue()).multiply(BigDecimal.valueOf(price)));
            }
        }
        return new SurplusIncome(amount, unpriced);
    }

    /**
     * The estimated income, in euros, unrounded. It may be negative.
     */
    public BigDecimal getAmountEur() {
        return amountEur;
    }

    /**
     * The surplus of the hours without a stored price, in kWh, unrounded.
     */
    public double getUnpricedSurplusKWh() {
        return unpricedSurplusKWh;
    }
}

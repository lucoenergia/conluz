package org.lucoenergia.conluz.domain.production.sharingagreement.observation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.List;

/**
 * What a supply received over some of its valid days (OBS-009): the median of its daily observed β, on a 0-1
 * scale rounded half-up to the 6 decimals of a stored coefficient, and the number of valid days it rests on.
 *
 * @param value     the median, or null when there is no valid day
 * @param validDays the number of valid days the median rests on
 */
public record ObservedBeta(BigDecimal value, int validDays) {

    static final int SCALE = 6;

    public static final ObservedBeta NONE = new ObservedBeta(null, 0);

    /**
     * The median of the given daily β: the middle value of an odd count, the mean of the two middle values of an
     * even one. {@link #NONE} when there is none.
     */
    static ObservedBeta of(Collection<Double> betas) {
        if (betas.isEmpty()) {
            return NONE;
        }
        List<Double> sorted = betas.stream().sorted().toList();
        int middle = sorted.size() / 2;
        BigDecimal median = sorted.size() % 2 == 1
                ? BigDecimal.valueOf(sorted.get(middle))
                // Averaged in decimal, so a mean ending in 5 at the 7th decimal is not turned into ...4999 first.
                : BigDecimal.valueOf(sorted.get(middle - 1)).add(BigDecimal.valueOf(sorted.get(middle)))
                .divide(BigDecimal.valueOf(2));
        return new ObservedBeta(median.setScale(SCALE, RoundingMode.HALF_UP), sorted.size());
    }
}

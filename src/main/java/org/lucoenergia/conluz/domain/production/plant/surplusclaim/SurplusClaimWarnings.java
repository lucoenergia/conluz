package org.lucoenergia.conluz.domain.production.plant.surplusclaim;

import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyReference;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * What the admin should know before claiming a month. None of them prevents the claim.
 *
 * @param suppliesWithoutData    the supplies in scope without any surplus record in the month, by code
 * @param suppliesWithGaps       the supplies in scope with records in the month but not for every hour of some days,
 *                               by code
 * @param uncoveredShare         the days whose applied coefficients do not add up to the whole production; null when
 *                               there is none, or when no applied coefficient overlaps the month
 * @param plantSupplyShare       the coefficient the plant's own supply holds in the month; null when it holds none
 * @param noAppliedCoefficients  whether no applied coefficient of the plant overlaps the month
 * @param hoursAboveNominalPower the hours whose surplus exceeds what the plant's nominal power can produce in an hour,
 *                               in order
 */
public record SurplusClaimWarnings(List<SupplyWithoutData> suppliesWithoutData,
                                   List<SupplyWithGaps> suppliesWithGaps,
                                   UncoveredShare uncoveredShare,
                                   BigDecimal plantSupplyShare,
                                   boolean noAppliedCoefficients,
                                   List<SurplusClaimHour> hoursAboveNominalPower) {

    /**
     * @param coefficient the coefficient in force on the last day of the month on which the supply held one
     */
    public record SupplyWithoutData(SupplyReference supply, BigDecimal coefficient) {
    }

    /**
     * @param days the days on which the supply misses a record for some hour, in order
     */
    public record SupplyWithGaps(SupplyReference supply, List<LocalDate> days) {
    }

    /**
     * @param largestShare the largest share of the production left without a coefficient on any of the days
     * @param days         the days whose coefficients add up to less than the whole production, in order
     */
    public record UncoveredShare(BigDecimal largestShare, List<LocalDate> days) {
    }
}

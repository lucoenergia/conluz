package org.lucoenergia.conluz.domain.production.plant.surplusclaim;

import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.PlantReference;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyPartitionCoefficient;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyReference;
import org.lucoenergia.conluz.domain.consumption.datadis.metrics.DailyPublishedAggregate;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What {@link PlantSurplusClaimCalculator} needs to build a plant's claim for one month, already read.
 *
 * @param plantSupplyId       the plant's own supply, whose surplus is never claimed
 * @param nominalPowerKW      the plant's nominal power
 * @param appliedCoefficients every coefficient of the plant with a validFrom, the plant's own supply included
 * @param suppliesInScope     the supplies whose surplus the claim sums: each holds one of the applied coefficients
 *                            overlapping the month
 * @param surplusByHour       the surplus of the supplies in scope summed per hour of the month, keyed by the instant
 *                            the hour starts at; an hour none of them stored a surplus record for is absent
 * @param daysBySupplyCode    the supplies in scope's records totalled per local day of the month, keyed by supply
 *                            code; a supply without any record in the month is absent
 * @param meanPriceByHour     the mean OMIE price of each hour of the month, keyed the same way; an hour without a price
 *                            is absent
 */
public record SurplusClaimInput(YearMonth month, ZoneId zone, PlantReference plant, String cil, UUID plantSupplyId,
                                double nominalPowerKW, SurplusClaimAvailableMonths availableMonths,
                                List<SupplyPartitionCoefficient> appliedCoefficients,
                                List<SupplyReference> suppliesInScope, Map<Instant, Double> surplusByHour,
                                Map<String, List<DailyPublishedAggregate>> daysBySupplyCode,
                                Map<Instant, Double> meanPriceByHour) {
}

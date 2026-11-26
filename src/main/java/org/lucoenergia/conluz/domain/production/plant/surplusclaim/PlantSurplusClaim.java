package org.lucoenergia.conluz.domain.production.plant.surplusclaim;

import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.PlantReference;

import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

/**
 * A plant's surplus for one closed month, hour by hour, as it would be claimed from the retailer, with what is
 * missing from it. Nothing is rounded.
 *
 * @param zone           the zone of the plant's community, in which the days and hours are read
 * @param cil            the plant's CIL; null when it has none
 * @param regulatoryCode the plant's regulatory code; null when it has none
 * @param days           every local day of the month, in order
 */
public record PlantSurplusClaim(YearMonth month, ZoneId zone, PlantReference plant, String cil,
                                String regulatoryCode, SurplusClaimAvailableMonths availableMonths,
                                SurplusClaimSummary summary,
                                List<SurplusClaimDay> days, SurplusClaimWarnings warnings) {
}

package org.lucoenergia.conluz.domain.admin.community.energy;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * One bucket of a community energy series -- an hour, a day or a month -- with the figures of the
 * supplies with data in it, summed, and how many of the supplies in scope those are.
 *
 * <p>A bucket in which no supply has data is a gap: its figures are null, never zero. A bucket in
 * which supplies have data that carry no energy is a measured zero.
 */
public class CommunityEnergySeriesBucket {

    private final OffsetDateTime startsAt;
    private final Double selfConsumptionKWh;
    private final Double surplusKWh;
    private final int supplyCount;
    private final int suppliesWithData;

    private CommunityEnergySeriesBucket(OffsetDateTime startsAt, Double selfConsumptionKWh, Double surplusKWh,
                                        int supplyCount, int suppliesWithData) {
        this.startsAt = startsAt;
        this.selfConsumptionKWh = selfConsumptionKWh;
        this.surplusKWh = surplusKWh;
        this.supplyCount = supplyCount;
        this.suppliesWithData = suppliesWithData;
    }

    /**
     * The bucket starting at {@code startsAt}, summing the contribution of each supply with data in it.
     *
     * @param supplyCount   the number of supplies in scope
     * @param contributions one per supply with data in the bucket
     */
    public static CommunityEnergySeriesBucket of(OffsetDateTime startsAt, int supplyCount,
                                                 List<SupplyEnergyContribution> contributions) {
        if (contributions.isEmpty()) {
            return new CommunityEnergySeriesBucket(startsAt, null, null, supplyCount, 0);
        }
        double selfConsumptionKWh = 0d;
        double surplusKWh = 0d;
        for (SupplyEnergyContribution contribution : contributions) {
            selfConsumptionKWh += contribution.selfConsumptionKWh();
            surplusKWh += contribution.surplusKWh();
        }
        return new CommunityEnergySeriesBucket(startsAt, selfConsumptionKWh, surplusKWh, supplyCount,
                contributions.size());
    }

    /**
     * The instant the bucket starts at, with the offset in force in the community's time zone there.
     */
    public OffsetDateTime getStartsAt() {
        return startsAt;
    }

    /**
     * The self-consumption of the supplies with data, summed and unrounded; null when there is none.
     */
    public Double getSelfConsumptionKWh() {
        return selfConsumptionKWh;
    }

    /**
     * The surplus of the supplies with data, summed and unrounded; null when there is none.
     */
    public Double getSurplusKWh() {
        return surplusKWh;
    }

    public int getSupplyCount() {
        return supplyCount;
    }

    public int getSuppliesWithData() {
        return suppliesWithData;
    }
}

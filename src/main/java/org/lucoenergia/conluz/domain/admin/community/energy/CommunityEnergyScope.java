package org.lucoenergia.conluz.domain.admin.community.energy;

import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.consumption.EnergyMetricsPeriod;

import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/**
 * What the energy figures of a community are computed over: every enabled supply of the community,
 * the community's time zone, and the one period all of them share, or no period when none could be
 * resolved.
 */
public class CommunityEnergyScope {

    private final List<Supply> supplies;
    private final ZoneId zone;
    private final EnergyMetricsPeriod period;

    public CommunityEnergyScope(List<Supply> supplies, ZoneId zone, EnergyMetricsPeriod period) {
        this.supplies = List.copyOf(supplies);
        this.zone = zone;
        this.period = period;
    }

    /**
     * The supplies in scope: every enabled supply of the community, whatever the membership status of
     * its owner.
     */
    public List<Supply> getSupplies() {
        return supplies;
    }

    /**
     * The community's time zone, the one the period and its buckets are read in.
     */
    public ZoneId getZone() {
        return zone;
    }

    /**
     * The period every supply is computed over, or empty when none could be resolved.
     */
    public Optional<EnergyMetricsPeriod> getPeriod() {
        return Optional.ofNullable(period);
    }
}

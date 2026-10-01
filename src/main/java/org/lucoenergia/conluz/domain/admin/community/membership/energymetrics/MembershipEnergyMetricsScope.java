package org.lucoenergia.conluz.domain.admin.community.membership.energymetrics;

import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.consumption.EnergyMetricsPeriod;

import java.util.List;
import java.util.Optional;

/**
 * What the energy metrics of a membership are computed over: every supply the member owns in the
 * community, and the one period all of them share, or no period when none could be resolved.
 */
public class MembershipEnergyMetricsScope {

    private final List<Supply> supplies;
    private final EnergyMetricsPeriod period;

    public MembershipEnergyMetricsScope(List<Supply> supplies, EnergyMetricsPeriod period) {
        this.supplies = List.copyOf(supplies);
        this.period = period;
    }

    public List<Supply> getSupplies() {
        return supplies;
    }

    /**
     * The period every supply is computed over, or empty when none could be resolved.
     */
    public Optional<EnergyMetricsPeriod> getPeriod() {
        return Optional.ofNullable(period);
    }
}

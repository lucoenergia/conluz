package org.lucoenergia.conluz.domain.consumption.datadis.metrics;

import org.lucoenergia.conluz.domain.shared.SupplyId;

import java.time.OffsetDateTime;

public interface GetSupplyEnergyMetricsService {

    /**
     * Aggregates the supply's stored consumption records into energy totals and the two
     * self-sufficiency / self-consumption ratios.
     *
     * <p>Both dates are optional and must be supplied together: when both are null the period is
     * resolved from the supply's earliest and latest stored record. Supplying exactly one of them,
     * or a start after the end, raises {@link InvalidEnergyMetricsPeriodException}.</p>
     *
     * @throws org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException when no supply
     *         exists with the given identifier
     */
    SupplyEnergyMetrics getEnergyMetrics(SupplyId supplyId, OffsetDateTime startDate, OffsetDateTime endDate);
}

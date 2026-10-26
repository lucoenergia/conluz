package org.lucoenergia.conluz.domain.consumption;

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

    /**
     * As {@link #getEnergyMetrics(SupplyId, OffsetDateTime, OffsetDateTime)}, or over a period the
     * server resolves by itself when {@code referencePeriod} is given.
     *
     * <p>{@link EnergyMetricsReferencePeriod#LATEST_PUBLISHED_MONTH} is resolved by
     * {@link ReferenceMonthResolver} from this supply's records alone, never from another supply of
     * its owner, with calendar months read in the supply's zone. When no month qualifies, the
     * metrics carry no period, as for a supply without any stored record.</p>
     *
     * @param referencePeriod the period to resolve, or null to bound it with the dates
     * @throws InvalidEnergyMetricsPeriodException when a reference period is requested together
     *         with a date, or when the dates themselves are invalid
     * @throws org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException when no supply
     *         exists with the given identifier
     */
    SupplyEnergyMetrics getEnergyMetrics(SupplyId supplyId, OffsetDateTime startDate, OffsetDateTime endDate,
                                         EnergyMetricsReferencePeriod referencePeriod);
}

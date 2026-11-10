package org.lucoenergia.conluz.domain.production.plant.energy;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface GetPlantEnergyMetricsService {

    /**
     * The energy figures of the plant over the period resolved by {@link PlantEnergyScopeResolver}.
     *
     * @param referencePeriod the reference period to resolve, or null when none is requested
     * @return the figures, without a period when none could be resolved
     */
    PlantEnergyMetrics getEnergyMetrics(UUID plantId, OffsetDateTime startDate, OffsetDateTime endDate,
                                        PlantEnergyMetricsReferencePeriod referencePeriod);
}

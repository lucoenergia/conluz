package org.lucoenergia.conluz.domain.production.plant.energy;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface GetPlantDailyEnergySeriesService {

    /**
     * The plant's production per local day of the period from {@code startDate}, inclusive, to {@code endDate},
     * exclusive, with an end after the start of the current hour clipped to it.
     *
     * @throws org.lucoenergia.conluz.domain.consumption.InvalidEnergyMetricsPeriodException when the
     *         requested period is invalid
     */
    List<PlantEnergySeriesBucket> getDailySeries(UUID plantId, OffsetDateTime startDate, OffsetDateTime endDate);
}

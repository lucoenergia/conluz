package org.lucoenergia.conluz.domain.production;

import java.time.OffsetDateTime;
import java.util.List;

public interface GetProductionRepository {

    InstantProduction getInstantProduction();

    List<ProductionByHour> getHourlyProductionByRangeOfDates(OffsetDateTime startDate, OffsetDateTime endDate);

    List<ProductionByHour> getHourlyProductionByRangeOfDates(OffsetDateTime startDate, OffsetDateTime endDate,
                                                             Float partitionCoefficient);
}

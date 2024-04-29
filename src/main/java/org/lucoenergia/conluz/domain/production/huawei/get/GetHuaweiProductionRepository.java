package org.lucoenergia.conluz.domain.production.huawei.get;

import org.lucoenergia.conluz.domain.production.huawei.RealTimeProduction;

import java.time.OffsetDateTime;
import java.util.List;

public interface GetHuaweiProductionRepository {

    List<RealTimeProduction> getRealTimeProductionByRangeOfDates(OffsetDateTime startDate, OffsetDateTime endDate);
}

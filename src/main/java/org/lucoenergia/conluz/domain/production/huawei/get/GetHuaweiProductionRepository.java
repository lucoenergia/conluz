package org.lucoenergia.conluz.domain.production.huawei.get;

import org.lucoenergia.conluz.domain.production.huawei.RealTimeProduction;

import java.time.Instant;
import java.util.List;

public interface GetHuaweiProductionRepository {

    List<RealTimeProduction> getRealTimeProductionByRangeOfDates(Instant startDate, Instant endDate);
}

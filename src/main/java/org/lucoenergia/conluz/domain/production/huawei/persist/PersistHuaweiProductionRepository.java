package org.lucoenergia.conluz.domain.production.huawei.persist;

import org.lucoenergia.conluz.domain.production.huawei.RealTimeProduction;

import java.util.List;

public interface PersistHuaweiProductionRepository {

    void persistRealTimeProduction(List<RealTimeProduction> productions);
}

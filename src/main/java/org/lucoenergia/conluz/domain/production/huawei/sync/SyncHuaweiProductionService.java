package org.lucoenergia.conluz.domain.production.huawei.sync;

public interface SyncHuaweiProductionService {

    /**
     * Syncs the real-time production data from Huawei energy stations.
     * It retrieves all energy stations with Huawei inverters, gets the real-time production data
     * for each station, and persists the production data.
     */
    void syncRealTimeProduction();

    void syncHourlyProduction();
}

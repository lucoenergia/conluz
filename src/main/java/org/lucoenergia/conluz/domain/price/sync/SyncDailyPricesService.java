package org.lucoenergia.conluz.domain.price.sync;

import java.time.OffsetDateTime;

public interface SyncDailyPricesService {

    void syncDailyPrices(OffsetDateTime day);
}

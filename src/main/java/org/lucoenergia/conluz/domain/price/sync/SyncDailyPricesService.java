package org.lucoenergia.conluz.domain.price.sync;

import java.time.OffsetDateTime;

public interface SyncDailyPricesService {

    void syncDailyPricesByDateInterval(OffsetDateTime startDate, OffsetDateTime endDate);
}

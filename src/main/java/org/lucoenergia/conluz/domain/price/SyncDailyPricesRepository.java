package org.lucoenergia.conluz.domain.price;

import java.time.OffsetDateTime;
import java.util.List;

public interface SyncDailyPricesRepository {

    List<PriceByHour> syncDailyPrices(OffsetDateTime day);
}

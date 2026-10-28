package org.lucoenergia.conluz.domain.price.sync;

import java.time.LocalDate;
import java.util.List;

public interface SyncDailyPricesService {

    /**
     * Fetches the OMIE prices of each market day of {@code [startDate, endDate)} again and stores them, each day's
     * prices replacing whatever was stored at the same instants. A day that is not served keeps what is stored.
     *
     * @return the outcome of each day of the range, in date order
     * @throws InvalidPriceSyncPeriodException when the range may not be synchronised; nothing is fetched then
     */
    List<PriceSyncResult> syncDailyPricesByDays(LocalDate startDate, LocalDate endDate);
}

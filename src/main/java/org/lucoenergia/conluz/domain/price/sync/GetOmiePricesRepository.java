package org.lucoenergia.conluz.domain.price.sync;

import java.time.LocalDate;
import java.util.List;

/**
 * Fetches the day-ahead market prices published by OMIE.
 */
public interface GetOmiePricesRepository {

    /**
     * The prices of each market day of the half-open range {@code [startDate, endDate)}, one result per day in date
     * order, whether or not the day's file could be fetched and parsed. A day that fails does not prevent the others
     * from being fetched.
     */
    List<OmieDayPrices> getPricesByDays(LocalDate startDate, LocalDate endDate);
}

package org.lucoenergia.conluz.domain.price.get;

import org.lucoenergia.conluz.domain.price.PriceByHour;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Service for retrieving price information.
 */
public interface GetPriceService {

    /**
     * Get prices by range of dates.
     *
     * @param startDate the start date
     * @param endDate the end date
     * @return a list of prices by hour
     */
    List<PriceByHour> getPricesByRangeOfDates(OffsetDateTime startDate, OffsetDateTime endDate);
}
package org.lucoenergia.conluz.domain.price.get;

import org.lucoenergia.conluz.domain.price.PriceByHour;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

public interface GetPriceRepository {

    List<PriceByHour> getPricesByRangeOfDates(OffsetDateTime startDate, OffsetDateTime endDate);

    /**
     * The mean of the OMIE prices, in €/kWh, stored in each hour of {@code zone} in the half-open interval
     * {@code [from, toExclusive)}, keyed by the instant the hour starts at: the mean of its four
     * quarter-hour prices, or its single hourly price. An hour without any stored price is absent.
     */
    Map<Instant, Double> meanPriceByHour(Instant from, Instant toExclusive, ZoneId zone);
}

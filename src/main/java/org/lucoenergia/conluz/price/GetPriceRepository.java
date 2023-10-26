package org.lucoenergia.conluz.price;

import java.time.OffsetDateTime;
import java.util.List;

public interface GetPriceRepository {

    List<PriceByHour> getPricesByRangeOfDates(OffsetDateTime startDate, OffsetDateTime endDate);
}

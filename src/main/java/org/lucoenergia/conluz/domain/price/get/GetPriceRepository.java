package org.lucoenergia.conluz.domain.price.get;

import org.lucoenergia.conluz.domain.price.PriceByHour;

import java.time.OffsetDateTime;
import java.util.List;

public interface GetPriceRepository {

    List<PriceByHour> getPricesByRangeOfDates(OffsetDateTime startDate, OffsetDateTime endDate);

    List<PriceByHour> getPricesByDay(OffsetDateTime startDate);
}

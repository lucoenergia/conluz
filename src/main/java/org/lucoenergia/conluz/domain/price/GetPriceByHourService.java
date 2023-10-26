package org.lucoenergia.conluz.domain.price;

import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class GetPriceByHourService {

    private final GetPriceRepository getPriceRepository;

    public GetPriceByHourService(GetPriceRepository getPriceRepository) {
        this.getPriceRepository = getPriceRepository;
    }

    public List<PriceByHour> getPricesByRangeOfDates(OffsetDateTime startDate, OffsetDateTime endDate) {
        return getPriceRepository.getPricesByRangeOfDates(startDate, endDate);
    }
}

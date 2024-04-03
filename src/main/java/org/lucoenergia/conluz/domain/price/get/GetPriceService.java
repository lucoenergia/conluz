package org.lucoenergia.conluz.domain.price.get;

import org.lucoenergia.conluz.domain.price.PriceByHour;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class GetPriceService {

    private final GetPriceRepository getPriceRepository;

    public GetPriceService(GetPriceRepository getPriceRepository) {
        this.getPriceRepository = getPriceRepository;
    }

    public List<PriceByHour> getPricesByRangeOfDates(OffsetDateTime startDate, OffsetDateTime endDate) {
        return getPriceRepository.getPricesByRangeOfDates(startDate, endDate);
    }
}

package org.lucoenergia.conluz.domain.price.get;

import org.lucoenergia.conluz.domain.price.PriceByHour;
import org.lucoenergia.conluz.infrastructure.price.omie.get.GetPriceRepositoryInflux;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class GetPriceService {

    private final GetPriceRepositoryInflux getPriceRepository;

    public GetPriceService(GetPriceRepositoryInflux getPriceRepository) {
        this.getPriceRepository = getPriceRepository;
    }

    public List<PriceByHour> getPricesByRangeOfDates(OffsetDateTime startDate, OffsetDateTime endDate) {
        return getPriceRepository.getPricesByRangeOfDates(startDate, endDate);
    }
}

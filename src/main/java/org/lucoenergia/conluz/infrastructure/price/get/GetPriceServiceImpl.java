package org.lucoenergia.conluz.infrastructure.price.get;

import org.lucoenergia.conluz.domain.price.get.GetPriceService;
import org.lucoenergia.conluz.domain.price.PriceByHour;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Transactional(readOnly = true)
@Service
public class GetPriceServiceImpl implements GetPriceService {

    private final GetPriceRepositoryInflux getPriceRepository;

    public GetPriceServiceImpl(GetPriceRepositoryInflux getPriceRepository) {
        this.getPriceRepository = getPriceRepository;
    }

    @Override
    public List<PriceByHour> getPricesByRangeOfDates(OffsetDateTime startDate, OffsetDateTime endDate) {
        return getPriceRepository.getPricesByRangeOfDates(startDate, endDate);
    }
}
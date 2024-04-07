package org.lucoenergia.conluz.domain.price.sync;

import org.lucoenergia.conluz.domain.price.PriceByHour;
import org.lucoenergia.conluz.infrastructure.price.omie.get.GetPriceRepositoryInflux;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class SyncDailyPricesService {

    private final GetPriceRepositoryInflux getPriceRepository;

    public SyncDailyPricesService(GetPriceRepositoryInflux getPriceRepository) {
        this.getPriceRepository = getPriceRepository;
    }

    public void syncDailyPrices(OffsetDateTime day) {
        List<PriceByHour> prices = getPriceRepository.getPricesByDay(day);
    }
}

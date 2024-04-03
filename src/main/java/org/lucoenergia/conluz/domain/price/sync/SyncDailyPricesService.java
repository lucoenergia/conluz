package org.lucoenergia.conluz.domain.price.sync;

import org.lucoenergia.conluz.domain.price.PriceByHour;
import org.lucoenergia.conluz.domain.price.persist.PersistOmiePricesRepository;
import org.lucoenergia.conluz.infrastructure.price.omie.get.GetPriceRepositoryInflux;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class SyncDailyPricesService {

    private final GetPriceRepositoryInflux getPriceRepository;
    private final PersistOmiePricesRepository persistOmiePricesRepository;

    public SyncDailyPricesService(GetPriceRepositoryInflux getPriceRepository,
                                  PersistOmiePricesRepository persistOmiePricesRepository) {
        this.getPriceRepository = getPriceRepository;
        this.persistOmiePricesRepository = persistOmiePricesRepository;
    }

    public void syncDailyPrices(OffsetDateTime day) {
        List<PriceByHour> prices = getPriceRepository.getPricesByDay(day);
        persistOmiePricesRepository.persistPrices(prices);
    }
}

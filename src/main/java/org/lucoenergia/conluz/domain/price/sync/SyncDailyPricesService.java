package org.lucoenergia.conluz.domain.price.sync;

import org.lucoenergia.conluz.domain.price.PriceByHour;
import org.lucoenergia.conluz.domain.price.persist.PersistOmiePricesRepository;
import org.lucoenergia.conluz.infrastructure.price.omie.get.GetPriceRepositoryRest;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class SyncDailyPricesService {

    private final GetPriceRepositoryRest getPriceRepositoryRest;
    private final PersistOmiePricesRepository persistOmiePricesRepository;

    public SyncDailyPricesService(GetPriceRepositoryRest getPriceRepositoryRest, PersistOmiePricesRepository persistOmiePricesRepository) {
        this.getPriceRepositoryRest = getPriceRepositoryRest;
        this.persistOmiePricesRepository = persistOmiePricesRepository;
    }

    public void syncDailyPrices(OffsetDateTime day) {
        List<PriceByHour> prices = getPriceRepositoryRest.getPricesByDay(day);
        persistOmiePricesRepository.persistPrices(prices);
    }
}

package org.lucoenergia.conluz.infrastructure.price.sync;

import org.lucoenergia.conluz.domain.price.PriceByHour;
import org.lucoenergia.conluz.domain.price.persist.PersistOmiePricesRepository;
import org.lucoenergia.conluz.domain.price.sync.SyncDailyPricesService;
import org.lucoenergia.conluz.infrastructure.price.get.GetPriceRepositoryRest;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class SyncDailyPricesServiceImpl implements SyncDailyPricesService {

    private final GetPriceRepositoryRest getPriceRepositoryRest;
    private final PersistOmiePricesRepository persistOmiePricesRepository;

    public SyncDailyPricesServiceImpl(GetPriceRepositoryRest getPriceRepositoryRest, PersistOmiePricesRepository persistOmiePricesRepository) {
        this.getPriceRepositoryRest = getPriceRepositoryRest;
        this.persistOmiePricesRepository = persistOmiePricesRepository;
    }

    public void syncDailyPrices(OffsetDateTime day) {
        List<PriceByHour> prices = getPriceRepositoryRest.getPricesByDay(day);
        persistOmiePricesRepository.persistPrices(prices);
    }
}

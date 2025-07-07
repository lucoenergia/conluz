package org.lucoenergia.conluz.infrastructure.price.sync;

import org.lucoenergia.conluz.domain.price.PriceByHour;
import org.lucoenergia.conluz.domain.price.persist.PersistOmiePricesRepository;
import org.lucoenergia.conluz.domain.price.sync.SyncDailyPricesService;
import org.lucoenergia.conluz.infrastructure.price.get.GetPriceRepositoryRest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class SyncDailyPricesServiceImpl implements SyncDailyPricesService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SyncDailyPricesServiceImpl.class);

    private final GetPriceRepositoryRest getPriceRepositoryRest;
    private final PersistOmiePricesRepository persistOmiePricesRepository;

    public SyncDailyPricesServiceImpl(GetPriceRepositoryRest getPriceRepositoryRest,
                                      PersistOmiePricesRepository persistOmiePricesRepository) {
        this.getPriceRepositoryRest = getPriceRepositoryRest;
        this.persistOmiePricesRepository = persistOmiePricesRepository;
    }

    @Override
    public void syncDailyPricesByDateInterval(OffsetDateTime startDate, OffsetDateTime endDate) {

        if (startDate.isAfter(endDate)) {
            LOGGER.error("Start date is after end date. Start date: {}, end date: {}", startDate, endDate);
            return;
        }

        List<PriceByHour> prices = getPriceRepositoryRest.getPricesByRangeOfDates(startDate, endDate);

        persistOmiePricesRepository.persistPrices(prices);
    }
}

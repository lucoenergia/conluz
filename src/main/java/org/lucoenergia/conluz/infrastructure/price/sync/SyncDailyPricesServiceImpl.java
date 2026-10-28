package org.lucoenergia.conluz.infrastructure.price.sync;

import org.lucoenergia.conluz.domain.price.PriceByHour;
import org.lucoenergia.conluz.domain.price.persist.PersistOmiePricesRepository;
import org.lucoenergia.conluz.domain.price.sync.GetOmiePricesRepository;
import org.lucoenergia.conluz.domain.price.sync.SyncDailyPricesService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class SyncDailyPricesServiceImpl implements SyncDailyPricesService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SyncDailyPricesServiceImpl.class);

    private final GetOmiePricesRepository getOmiePricesRepository;
    private final PersistOmiePricesRepository persistOmiePricesRepository;

    public SyncDailyPricesServiceImpl(GetOmiePricesRepository getOmiePricesRepository,
                                      PersistOmiePricesRepository persistOmiePricesRepository) {
        this.getOmiePricesRepository = getOmiePricesRepository;
        this.persistOmiePricesRepository = persistOmiePricesRepository;
    }

    @Override
    public void syncDailyPricesByDateInterval(OffsetDateTime startDate, OffsetDateTime endDate) {

        if (startDate.isAfter(endDate)) {
            LOGGER.error("Start date is after end date. Start date: {}, end date: {}", startDate, endDate);
            return;
        }

        List<PriceByHour> prices = getOmiePricesRepository
                .getPricesByDays(startDate.toLocalDate(), endDate.toLocalDate().plusDays(1)).stream()
                .flatMap(day -> day.prices().stream())
                .toList();

        persistOmiePricesRepository.persistPrices(prices);
    }
}

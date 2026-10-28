package org.lucoenergia.conluz.infrastructure.price.sync;

import org.lucoenergia.conluz.domain.price.PriceByHour;
import org.lucoenergia.conluz.domain.price.persist.PersistOmiePricesRepository;
import org.lucoenergia.conluz.domain.price.sync.GetOmiePricesRepository;
import org.lucoenergia.conluz.domain.price.sync.OmieDayPrices;
import org.lucoenergia.conluz.domain.price.sync.PriceSyncPeriodValidator;
import org.lucoenergia.conluz.domain.price.sync.PriceSyncResult;
import org.lucoenergia.conluz.domain.price.sync.SyncDailyPricesService;
import org.lucoenergia.conluz.infrastructure.price.OmieConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@Service
public class SyncDailyPricesServiceImpl implements SyncDailyPricesService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SyncDailyPricesServiceImpl.class);

    private final GetOmiePricesRepository getOmiePricesRepository;
    private final PersistOmiePricesRepository persistOmiePricesRepository;
    private final Clock clock;

    public SyncDailyPricesServiceImpl(GetOmiePricesRepository getOmiePricesRepository,
                                      PersistOmiePricesRepository persistOmiePricesRepository,
                                      Clock clock) {
        this.getOmiePricesRepository = getOmiePricesRepository;
        this.persistOmiePricesRepository = persistOmiePricesRepository;
        this.clock = clock;
    }

    @Override
    public List<PriceSyncResult> syncDailyPricesByDays(LocalDate startDate, LocalDate endDate) {

        PriceSyncPeriodValidator.validate(startDate, endDate, LocalDate.now(clock.withZone(OmieConfig.OMIE_ZONE)));

        List<OmieDayPrices> days = getOmiePricesRepository.getPricesByDays(startDate, endDate);

        List<PriceByHour> prices = days.stream()
                .flatMap(day -> day.prices().stream())
                .toList();
        if (!prices.isEmpty()) {
            persistOmiePricesRepository.persistPrices(prices);
        }

        List<PriceSyncResult> results = days.stream().map(PriceSyncResult::of).toList();
        LOGGER.info("Synchronized OMIE prices from {} to {}, exclusive: {}", startDate, endDate, results);
        return results;
    }
}

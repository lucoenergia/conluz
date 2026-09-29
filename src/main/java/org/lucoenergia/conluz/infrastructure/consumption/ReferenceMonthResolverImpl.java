package org.lucoenergia.conluz.infrastructure.consumption;

import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.consumption.ReferenceMonthResolver;
import org.lucoenergia.conluz.domain.consumption.datadis.metrics.GetDatadisConsumptionAggregateRepository;
import org.lucoenergia.conluz.domain.shared.time.ClockProvider;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/**
 * Asks the store, once per supply, for the latest record carrying assigned production inside the
 * search window, and keeps the month of the latest one. The cost is one query per supply, whatever
 * the size of the window.
 */
@Component
public class ReferenceMonthResolverImpl implements ReferenceMonthResolver {

    private final GetDatadisConsumptionAggregateRepository getDatadisConsumptionAggregateRepository;
    private final ClockProvider clockProvider;

    public ReferenceMonthResolverImpl(GetDatadisConsumptionAggregateRepository getDatadisConsumptionAggregateRepository,
                                      ClockProvider clockProvider) {
        this.getDatadisConsumptionAggregateRepository = getDatadisConsumptionAggregateRepository;
        this.clockProvider = clockProvider;
    }

    @Override
    public Optional<YearMonth> resolveLatestPublishedMonth(List<Supply> supplies, ZoneId zone) {
        YearMonth currentMonth = YearMonth.from(clockProvider.now().atZone(zone));
        Instant windowStart = startOf(currentMonth.minusMonths(SEARCH_WINDOW_MONTHS), zone);
        // Exclusive: the current month is never a candidate.
        Instant windowEnd = startOf(currentMonth, zone);

        return supplies.stream()
                .map(supply -> getDatadisConsumptionAggregateRepository
                        .findLatestAssignedProductionRecord(supply, windowStart, windowEnd))
                .flatMap(Optional::stream)
                .max(Instant::compareTo)
                .map(latest -> YearMonth.from(latest.atZone(zone)));
    }

    private static Instant startOf(YearMonth month, ZoneId zone) {
        return month.atDay(1).atStartOfDay(zone).toInstant();
    }
}

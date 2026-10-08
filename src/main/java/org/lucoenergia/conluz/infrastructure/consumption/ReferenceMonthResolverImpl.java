package org.lucoenergia.conluz.infrastructure.consumption;

import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.consumption.RecordedConsumptionPeriod;
import org.lucoenergia.conluz.domain.consumption.ReferenceMonthResolver;
import org.lucoenergia.conluz.domain.consumption.datadis.metrics.GetDatadisConsumptionAggregateRepository;
import org.lucoenergia.conluz.domain.shared.time.ClockProvider;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Asks the store, once per supply, for the first and the last published record inside the search
 * window, then walks the months down from the latest one holding any published record, counting a
 * supply's published hours in a month only when its published records reach that month, and stops
 * at the first month published for any supply.
 *
 * <h2>Query cost</h2>
 *
 * <p>Two queries per supply for the published period, plus one count per supply and month visited.
 * When the latest month holding published records is published, that is one count in total; each
 * month that is not adds one count per supply reaching it. A supply without any published record in
 * the window costs its two queries and nothing more.
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
        YearMonth firstCandidate = currentMonth.minusMonths(SEARCH_WINDOW_MONTHS);
        Instant windowStart = startOf(firstCandidate, zone);
        // Exclusive: the current month is never a candidate.
        Instant windowEnd = startOf(currentMonth, zone);

        List<SupplyPublication> publications = supplies.stream()
                .flatMap(supply -> getDatadisConsumptionAggregateRepository
                        .findPublishedPeriod(supply, windowStart, windowEnd)
                        .map(period -> new SupplyPublication(supply, period, zone))
                        .stream())
                .toList();

        Optional<YearMonth> latest = publications.stream()
                .map(SupplyPublication::lastMonth)
                .max(Comparator.naturalOrder());
        if (latest.isEmpty()) {
            return Optional.empty();
        }

        for (YearMonth month = latest.get(); !month.isBefore(firstCandidate); month = month.minusMonths(1)) {
            for (SupplyPublication publication : publications) {
                if (publication.reaches(month) && isPublished(publication, month, zone)) {
                    return Optional.of(month);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Whether the published hours of the supply in the month reach the minimum share of the hours
     * it could have published there: from the later of the month's first hour and its first
     * published record, to the end of the month.
     */
    private boolean isPublished(SupplyPublication publication, YearMonth month, ZoneId zone) {
        Instant monthStart = startOf(month, zone);
        Instant monthEnd = startOf(month.plusMonths(1), zone);
        Instant firstPossibleHour = publication.firstRecord().isAfter(monthStart)
                ? publication.firstRecord()
                : monthStart;
        long possibleHours = Duration.between(firstPossibleHour, monthEnd).toHours();

        long publishedHours = getDatadisConsumptionAggregateRepository
                .countPublishedHours(publication.supply(), monthStart, monthEnd);

        // Integer arithmetic, so the threshold is exact rather than subject to rounding.
        return publishedHours * 100 >= possibleHours * PUBLISHED_HOURS_MIN_PERCENT;
    }

    private static Instant startOf(YearMonth month, ZoneId zone) {
        return month.atDay(1).atStartOfDay(zone).toInstant();
    }

    /**
     * A supply with the first and the last of its published records in the window, and the months
     * they fall in.
     */
    private record SupplyPublication(Supply supply, Instant firstRecord, YearMonth firstMonth, YearMonth lastMonth) {

        SupplyPublication(Supply supply, RecordedConsumptionPeriod period, ZoneId zone) {
            this(supply, period.getFirstRecord(),
                    YearMonth.from(period.getFirstRecord().atZone(zone)),
                    YearMonth.from(period.getLastRecord().atZone(zone)));
        }

        boolean reaches(YearMonth month) {
            return !month.isBefore(firstMonth) && !month.isAfter(lastMonth);
        }
    }
}

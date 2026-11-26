package org.lucoenergia.conluz.infrastructure.consumption;

import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.consumption.MonthlyPublication;
import org.lucoenergia.conluz.domain.consumption.RecordedConsumptionPeriod;
import org.lucoenergia.conluz.domain.consumption.ReferenceMonthResolver;
import org.lucoenergia.conluz.domain.consumption.datadis.metrics.GetDatadisConsumptionAggregateRepository;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Asks the store, for all the supplies at once, for the first and the last published record of each
 * inside the search window, then walks the months down from the latest one holding any published
 * record, counting the published hours in a month only of the supplies whose published records reach
 * that month, and stops at the first month published for any supply.
 *
 * <h2>Query cost</h2>
 *
 * <p>Two queries for the published periods of every supply, plus one count, of every supply reaching
 * the month, per month visited. When the latest month holding published records is published, that
 * is one count in total, whatever the number of supplies. When no supply has a published record in
 * the window, the two queries are all it costs.
 */
@Component
public class ReferenceMonthResolverImpl implements ReferenceMonthResolver {

    private final GetDatadisConsumptionAggregateRepository getDatadisConsumptionAggregateRepository;
    private final Clock clock;

    public ReferenceMonthResolverImpl(GetDatadisConsumptionAggregateRepository getDatadisConsumptionAggregateRepository,
                                      Clock clock) {
        this.getDatadisConsumptionAggregateRepository = getDatadisConsumptionAggregateRepository;
        this.clock = clock;
    }

    @Override
    public Optional<YearMonth> resolveLatestPublishedMonth(List<Supply> supplies, ZoneId zone) {
        if (supplies.isEmpty()) {
            return Optional.empty();
        }
        YearMonth currentMonth = YearMonth.from(clock.instant().atZone(zone));
        YearMonth firstCandidate = MonthlyPublication.firstCandidate(currentMonth);
        Instant windowStart = startOf(firstCandidate, zone);
        // Exclusive: the current month is never a candidate.
        Instant windowEnd = startOf(currentMonth, zone);

        Map<String, RecordedConsumptionPeriod> periods = getDatadisConsumptionAggregateRepository
                .findPublishedPeriods(supplies, windowStart, windowEnd);
        List<SupplyPublication> publications = supplies.stream()
                .filter(supply -> periods.containsKey(supply.getCode()))
                .map(supply -> new SupplyPublication(supply, periods.get(supply.getCode()), zone))
                .toList();

        Optional<YearMonth> latest = publications.stream()
                .map(SupplyPublication::lastMonth)
                .max(Comparator.naturalOrder());
        if (latest.isEmpty()) {
            return Optional.empty();
        }

        for (YearMonth month = latest.get(); !month.isBefore(firstCandidate); month = month.minusMonths(1)) {
            if (isPublishedForAnySupply(publications, month, zone)) {
                return Optional.of(month);
            }
        }
        return Optional.empty();
    }

    /**
     * Whether, for any of the supplies whose published records reach the month, the published hours
     * there reach the minimum share of the hours it could have published (ENM-001). The hours of
     * every such supply are counted in a single read; a month no supply reaches issues none.
     */
    private boolean isPublishedForAnySupply(List<SupplyPublication> publications, YearMonth month, ZoneId zone) {
        List<SupplyPublication> reaching = publications.stream()
                .filter(publication -> publication.reaches(month))
                .toList();
        if (reaching.isEmpty()) {
            return false;
        }
        Map<String, Long> publishedHours = getDatadisConsumptionAggregateRepository.countPublishedHours(
                reaching.stream().map(SupplyPublication::supply).toList(),
                startOf(month, zone), startOf(month.plusMonths(1), zone));
        return reaching.stream().anyMatch(publication -> MonthlyPublication.isPublished(month,
                publication.firstRecord(), publishedHours.getOrDefault(publication.supply().getCode(), 0L), zone));
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

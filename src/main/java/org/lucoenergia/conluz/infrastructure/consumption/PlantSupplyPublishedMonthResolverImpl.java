package org.lucoenergia.conluz.infrastructure.consumption;

import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.consumption.MonthlyPublication;
import org.lucoenergia.conluz.domain.consumption.PlantSupplyPublishedMonthResolver;
import org.lucoenergia.conluz.domain.consumption.RecordedConsumptionPeriod;
import org.lucoenergia.conluz.domain.consumption.datadis.metrics.GetDatadisConsumptionAggregateRepository;
import org.lucoenergia.conluz.domain.production.datadis.get.GetDatadisProductionAggregateRepository;
import org.lucoenergia.conluz.domain.production.datadis.get.RecordedProductionPeriod;
import org.lucoenergia.conluz.domain.production.plant.energy.PlantDailyEnergy;
import org.lucoenergia.conluz.domain.production.plant.energy.PlantEnergySources;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/**
 * Reads the plant supply's daily hours in both sources over the search window, and walks its months down with
 * {@link MonthlyPublication}.
 *
 * <h2>Query cost</h2>
 *
 * <p>Four InfluxDB requests, whatever the months visited: the supply's production period and its surplus period,
 * then its daily production and its daily consumption records over the window. A supply without any record costs
 * the first two only.</p>
 */
@Component
public class PlantSupplyPublishedMonthResolverImpl implements PlantSupplyPublishedMonthResolver {

    private final GetDatadisConsumptionAggregateRepository getDatadisConsumptionAggregateRepository;
    private final GetDatadisProductionAggregateRepository getDatadisProductionAggregateRepository;
    private final Clock clock;

    public PlantSupplyPublishedMonthResolverImpl(
            GetDatadisConsumptionAggregateRepository getDatadisConsumptionAggregateRepository,
            GetDatadisProductionAggregateRepository getDatadisProductionAggregateRepository,
            Clock clock) {
        this.getDatadisConsumptionAggregateRepository = getDatadisConsumptionAggregateRepository;
        this.getDatadisProductionAggregateRepository = getDatadisProductionAggregateRepository;
        this.clock = clock;
    }

    @Override
    public Optional<YearMonth> resolveLatestPublishedMonth(Supply plantSupply, ZoneId zone) {
        Optional<RecordedProductionPeriod> production =
                getDatadisProductionAggregateRepository.findRecordedPeriod(plantSupply.getCode());
        Optional<RecordedConsumptionPeriod> surplus =
                getDatadisConsumptionAggregateRepository.findSurplusPeriod(plantSupply);
        Optional<Instant> firstRecord = PlantEnergySources.firstRecord(production, surplus);
        if (firstRecord.isEmpty()) {
            return Optional.empty();
        }

        YearMonth currentMonth = YearMonth.from(clock.instant().atZone(zone));
        Instant windowStart = startOf(MonthlyPublication.firstCandidate(currentMonth), zone);
        Instant firstDay = firstRecord.get().atZone(zone).toLocalDate().atStartOfDay(zone).toInstant();
        Instant from = firstDay.isAfter(windowStart) ? firstDay : windowStart;
        // Exclusive: the current month is never a candidate.
        Instant to = startOf(currentMonth, zone);
        if (!from.isBefore(to)) {
            // Every record is in the current month.
            return Optional.empty();
        }

        List<PlantDailyEnergy> days = PlantEnergySources.daily(
                getDatadisProductionAggregateRepository.aggregateByLocalDay(plantSupply.getCode(), from, to, zone),
                getDatadisConsumptionAggregateRepository.aggregateByLocalDay(plantSupply, from, to, zone),
                PlantEnergySources.cutover(production, zone));
        return MonthlyPublication.latestPublished(PlantEnergySources.hoursByMonth(days), firstRecord.get(),
                currentMonth, zone);
    }

    private static Instant startOf(YearMonth month, ZoneId zone) {
        return month.atDay(1).atStartOfDay(zone).toInstant();
    }
}

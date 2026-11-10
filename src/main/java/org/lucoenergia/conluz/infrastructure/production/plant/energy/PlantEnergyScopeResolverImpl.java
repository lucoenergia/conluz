package org.lucoenergia.conluz.infrastructure.production.plant.energy;

import org.lucoenergia.conluz.domain.consumption.EnergyMetricsPeriod;
import org.lucoenergia.conluz.domain.consumption.EnergyMetricsPeriodValidator;
import org.lucoenergia.conluz.domain.production.datadis.get.DailyProductionAggregate;
import org.lucoenergia.conluz.domain.production.datadis.get.GetDatadisProductionAggregateRepository;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.plant.PlantNotFoundException;
import org.lucoenergia.conluz.domain.production.plant.energy.PlantEnergyMetricsReferencePeriod;
import org.lucoenergia.conluz.domain.production.plant.energy.PlantEnergyScope;
import org.lucoenergia.conluz.domain.production.plant.energy.PlantEnergyScopeResolver;
import org.lucoenergia.conluz.domain.production.plant.get.GetPlantRepository;
import org.lucoenergia.conluz.domain.shared.PlantId;
import org.lucoenergia.conluz.domain.shared.time.ZoneResolver;
import org.lucoenergia.conluz.infrastructure.shared.time.DateConverter;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Period;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;

/**
 * Validates the requested period before anything is read, then loads the plant and resolves the period
 * for its backing supply, as {@link org.lucoenergia.conluz.infrastructure.admin.community.energy.CommunityEnergyScopeResolverImpl}
 * does for a community. The zone is the one of the plant's community, resolved exactly as the community
 * operations resolve it.
 *
 * <h2>Latest production day</h2>
 *
 * <p>The records of the 24 months before the current local day are counted per local day in a single
 * read, and the latest day whose records cover all its hours -- 23, 24 or 25 -- is chosen. The read ends
 * at the current day's local midnight, so the current day is never a candidate, however many of its
 * hours are stored: it is not over, and its records may still change.</p>
 *
 * <h2>Query cost</h2>
 *
 * <p>One relational query (the plant). Resolving the period adds one InfluxDB query for the latest
 * production day, one for the recorded range (two statements in one request), and none for an explicit
 * period.
 */
@Component
public class PlantEnergyScopeResolverImpl implements PlantEnergyScopeResolver {

    private final GetPlantRepository getPlantRepository;
    private final GetDatadisProductionAggregateRepository getDatadisProductionAggregateRepository;
    private final ZoneResolver zoneResolver;
    private final DateConverter dateConverter;
    private final Clock clock;

    public PlantEnergyScopeResolverImpl(GetPlantRepository getPlantRepository,
                                        GetDatadisProductionAggregateRepository getDatadisProductionAggregateRepository,
                                        ZoneResolver zoneResolver,
                                        DateConverter dateConverter,
                                        Clock clock) {
        this.getPlantRepository = getPlantRepository;
        this.getDatadisProductionAggregateRepository = getDatadisProductionAggregateRepository;
        this.zoneResolver = zoneResolver;
        this.dateConverter = dateConverter;
        this.clock = clock;
    }

    @Override
    public PlantEnergyScope resolve(UUID plantId, OffsetDateTime startDate, OffsetDateTime endDate,
                                    PlantEnergyMetricsReferencePeriod referencePeriod, Period maxLength) {

        PlantId id = PlantId.of(plantId);
        Plant plant = getPlantRepository.findById(id).orElseThrow(() -> new PlantNotFoundException(id));

        ZoneId zone = zoneResolver.resolveZoneIdForCommunity(plant.getCommunity().getId());
        OffsetDateTime startOfCurrentHour = EnergyMetricsPeriod.startOfCurrentHour(clock, zone);
        EnergyMetricsPeriodValidator.validate(startDate, endDate, referencePeriod != null, startOfCurrentHour,
                zone, maxLength);

        String supplyCode = plant.getSupply().getCode();
        Optional<EnergyMetricsPeriod> period = resolvePeriod(supplyCode, zone, startOfCurrentHour, startDate,
                endDate, referencePeriod);

        return new PlantEnergyScope(supplyCode, zone, period.orElse(null));
    }

    private Optional<EnergyMetricsPeriod> resolvePeriod(String supplyCode, ZoneId zone,
                                                        OffsetDateTime startOfCurrentHour,
                                                        OffsetDateTime startDate, OffsetDateTime endDate,
                                                        PlantEnergyMetricsReferencePeriod referencePeriod) {
        if (startDate != null) {
            return Optional.of(new EnergyMetricsPeriod(startDate, endDate).endingNoLaterThan(startOfCurrentHour));
        }
        if (referencePeriod == PlantEnergyMetricsReferencePeriod.LATEST_PRODUCTION_DAY) {
            return latestProductionDay(supplyCode, zone).map(day -> EnergyMetricsPeriod.ofDay(day, zone));
        }
        return getDatadisProductionAggregateRepository.findRecordedPeriod(supplyCode)
                .map(recorded -> EnergyMetricsPeriod.ofRecords(
                        dateConverter.convertInstantToOffsetDateTime(recorded.firstRecord()),
                        dateConverter.convertInstantToOffsetDateTime(recorded.lastRecord())));
    }

    /**
     * The latest local day before today, within the 24 months before it, whose records cover all its
     * hours, or empty when there is none.
     */
    private Optional<LocalDate> latestProductionDay(String supplyCode, ZoneId zone) {
        LocalDate today = LocalDate.now(clock.withZone(zone));
        Instant from = today.minusMonths(EnergyMetricsPeriodValidator.MAX_MONTHS).atStartOfDay(zone).toInstant();
        // Exclusive: no record of the current day is read.
        Instant toExclusive = today.atStartOfDay(zone).toInstant();

        return getDatadisProductionAggregateRepository.aggregateByLocalDay(supplyCode, from, toExclusive, zone)
                .stream()
                .filter(day -> day.hoursWithData()
                        >= EnergyMetricsPeriod.ofDay(day.day(), zone).getExpectedHours())
                .map(DailyProductionAggregate::day)
                .max(Comparator.naturalOrder());
    }
}

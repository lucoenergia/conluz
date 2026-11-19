package org.lucoenergia.conluz.infrastructure.production.sharingagreement.observation;

import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.GetSupplyPartitionCoefficientRepository;
import org.lucoenergia.conluz.domain.consumption.MonthlyPublication;
import org.lucoenergia.conluz.domain.consumption.RecordedConsumptionPeriod;
import org.lucoenergia.conluz.domain.consumption.datadis.metrics.DailyPublishedAggregate;
import org.lucoenergia.conluz.domain.consumption.datadis.metrics.GetDatadisConsumptionAggregateRepository;
import org.lucoenergia.conluz.domain.production.datadis.get.GetDatadisProductionAggregateRepository;
import org.lucoenergia.conluz.domain.production.datadis.get.RecordedProductionPeriod;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.plant.PlantNotFoundException;
import org.lucoenergia.conluz.domain.production.plant.energy.PlantDailyEnergy;
import org.lucoenergia.conluz.domain.production.plant.energy.PlantEnergySources;
import org.lucoenergia.conluz.domain.production.plant.get.GetPlantRepository;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreement;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementNotFoundException;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementStatus;
import org.lucoenergia.conluz.domain.production.sharingagreement.get.GetSharingAgreementRepository;
import org.lucoenergia.conluz.domain.production.sharingagreement.observation.GetSharingObservationService;
import org.lucoenergia.conluz.domain.production.sharingagreement.observation.ObservationInput;
import org.lucoenergia.conluz.domain.production.sharingagreement.observation.ObservedAgreement;
import org.lucoenergia.conluz.domain.production.sharingagreement.observation.ObservedCoefficient;
import org.lucoenergia.conluz.domain.production.sharingagreement.observation.ObservedSharingCalculator;
import org.lucoenergia.conluz.domain.production.sharingagreement.observation.ObservedSupply;
import org.lucoenergia.conluz.domain.production.sharingagreement.observation.PlantSharingObservation;
import org.lucoenergia.conluz.domain.production.sharingagreement.observation.SharingAgreementNotObservableException;
import org.lucoenergia.conluz.domain.production.sharingagreement.observation.SharingAgreementObservation;
import org.lucoenergia.conluz.domain.production.sharingagreement.observation.SupplyDailyEnergy;
import org.lucoenergia.conluz.domain.shared.PlantId;
import org.lucoenergia.conluz.domain.shared.time.ZoneResolver;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Reads what the observed sharing of a plant is computed from, and hands it to the
 * {@link ObservedSharingCalculator}. Nothing is written (OBS-007).
 *
 * <h2>Analysed supplies</h2>
 *
 * <p>The supplies of the plant's community, without the community's plant supplies (PLT-002). A supply holding
 * coefficients in this plant and in another one is not computable: the energy it receives cannot be attributed
 * to this plant alone. A supply holding coefficients only in another plant belongs to that plant's analysis, not
 * this one's, and is left out entirely.</p>
 *
 * <h2>Analysed range</h2>
 *
 * <p>The plant supply's energy and coverage come from its production records, and from its legacy surplus on the
 * days before the first of them ({@link PlantEnergySources}, OBS-001). The range runs from the local day of its
 * first record in either source to the end of the latest month in which those days hold the hours ENM-001
 * requires. Without a record, or without such a month, the plant is not computable (OBS-006).</p>
 *
 * <h2>Query cost</h2>
 *
 * <p>Five relational queries (the plant, its agreements, its coefficients, the community's supplies and plant
 * supplies, the supplies in other plants), whatever the number of supplies. Four InfluxDB requests, whatever the
 * length of the history: the plant supply's production period and its surplus period, then the daily totals of
 * every analysed supply and the plant supply together and the plant's daily production, both up to the start of
 * the current month.</p>
 */
@Service
@Transactional(readOnly = true)
@EnableConfigurationProperties(SharingObservationProperties.class)
public class GetSharingObservationServiceImpl implements GetSharingObservationService {

    private final GetPlantRepository getPlantRepository;
    private final GetSharingAgreementRepository getSharingAgreementRepository;
    private final GetSupplyPartitionCoefficientRepository getSupplyPartitionCoefficientRepository;
    private final GetSupplyRepository getSupplyRepository;
    private final GetDatadisConsumptionAggregateRepository getDatadisConsumptionAggregateRepository;
    private final GetDatadisProductionAggregateRepository getDatadisProductionAggregateRepository;
    private final Clock clock;
    private final ZoneResolver zoneResolver;
    private final ObservedSharingCalculator calculator;

    public GetSharingObservationServiceImpl(GetPlantRepository getPlantRepository,
                                            GetSharingAgreementRepository getSharingAgreementRepository,
                                            GetSupplyPartitionCoefficientRepository getSupplyPartitionCoefficientRepository,
                                            GetSupplyRepository getSupplyRepository,
                                            GetDatadisConsumptionAggregateRepository getDatadisConsumptionAggregateRepository,
                                            GetDatadisProductionAggregateRepository getDatadisProductionAggregateRepository,
                                            Clock clock,
                                            ZoneResolver zoneResolver,
                                            SharingObservationProperties properties) {
        this.getPlantRepository = getPlantRepository;
        this.getSharingAgreementRepository = getSharingAgreementRepository;
        this.getSupplyPartitionCoefficientRepository = getSupplyPartitionCoefficientRepository;
        this.getSupplyRepository = getSupplyRepository;
        this.getDatadisConsumptionAggregateRepository = getDatadisConsumptionAggregateRepository;
        this.getDatadisProductionAggregateRepository = getDatadisProductionAggregateRepository;
        this.clock = clock;
        this.zoneResolver = zoneResolver;
        this.calculator = new ObservedSharingCalculator(properties.getConsecutiveDays());
    }

    @Override
    public PlantSharingObservation observePlant(UUID plantId) {
        Plant plant = getPlantRepository.findById(PlantId.of(plantId))
                .orElseThrow(() -> new PlantNotFoundException(PlantId.of(plantId)));
        return calculator.calculate(input(plant));
    }

    @Override
    public SharingAgreementObservation observeAgreement(UUID plantId, UUID sharingAgreementId) {
        SharingAgreement agreement = getSharingAgreementRepository.findById(sharingAgreementId)
                .filter(found -> plantId.equals(found.getPlantId()))
                .orElseThrow(() -> new SharingAgreementNotFoundException(sharingAgreementId));
        if (agreement.getStatus() == SharingAgreementStatus.DRAFT) {
            throw new SharingAgreementNotObservableException(sharingAgreementId, agreement.getStatus());
        }
        // A PUBLISHED or SUPERSEDED agreement of the plant is always analysed, so its slice exists.
        return observePlant(plantId).sliceFor(sharingAgreementId)
                .orElseThrow(() -> new IllegalStateException("The observation of plant " + plantId
                        + " has no slice for its agreement " + sharingAgreementId + ", which is not a draft"));
    }

    private ObservationInput input(Plant plant) {
        ZoneId zone = zoneResolver.resolveZoneIdForCommunity(plant.getCommunity().getId());
        Supply plantSupply = plant.getSupply();

        List<ObservedAgreement> agreements = getSharingAgreementRepository.findByPlantId(plant.getId(), null).stream()
                .map(agreement -> new ObservedAgreement(agreement.getId(), agreement.getName(), agreement.getStatus(),
                        agreement.getDistributorFileSentOn(), agreement.getCreatedAt()))
                .toList();
        List<ObservedCoefficient> coefficients = getSupplyPartitionCoefficientRepository.findAllByPlantId(plant.getId())
                .stream()
                .map(row -> new ObservedCoefficient(row.getId(), row.getSupplyId(), row.getSharingAgreementId(),
                        row.getCoefficient(), row.getValidFrom(), row.getValidTo()))
                .toList();

        Set<String> plantSupplyCodes = getPlantRepository.findSupplyCodesByCommunity(plant.getCommunity().getId());
        List<Supply> consumers = getSupplyRepository.findAllByCommunityId(plant.getCommunity().getId()).stream()
                .filter(supply -> !supply.getId().equals(plantSupply.getId())
                        && !plantSupplyCodes.contains(supply.getCode()))
                .toList();
        Set<UUID> inOtherPlants = getSupplyPartitionCoefficientRepository.findSupplyIdsWithCoefficientsInOtherPlants(
                plant.getId(), consumers.stream().map(Supply::getId).toList());
        Set<UUID> inThisPlant = coefficients.stream().map(ObservedCoefficient::supplyId).collect(Collectors.toSet());
        List<Supply> analysed = consumers.stream().filter(supply -> !inOtherPlants.contains(supply.getId())).toList();
        List<ObservedSupply> notComputable = consumers.stream()
                .filter(supply -> inOtherPlants.contains(supply.getId()) && inThisPlant.contains(supply.getId()))
                .map(GetSharingObservationServiceImpl::observed)
                .toList();
        List<ObservedSupply> supplies = analysed.stream().map(GetSharingObservationServiceImpl::observed).toList();

        Optional<RecordedProductionPeriod> production =
                getDatadisProductionAggregateRepository.findRecordedPeriod(plantSupply.getCode());
        Optional<RecordedConsumptionPeriod> surplus =
                getDatadisConsumptionAggregateRepository.findSurplusPeriod(plantSupply);
        Optional<Instant> firstRecord = PlantEnergySources.firstRecord(production, surplus);
        if (firstRecord.isEmpty()) {
            return withoutRange(zone, plant, supplies, notComputable, agreements, coefficients);
        }
        LocalDate from = firstRecord.get().atZone(zone).toLocalDate();
        YearMonth currentMonth = YearMonth.from(clock.instant().atZone(zone));
        Instant start = from.atStartOfDay(zone).toInstant();
        Instant end = currentMonth.atDay(1).atStartOfDay(zone).toInstant();
        if (!start.isBefore(end)) {
            // Every record is in the current month, which is never analysed.
            return withoutRange(zone, plant, supplies, notComputable, agreements, coefficients);
        }

        // Read up to the current month, which is never analysed: where the analysed range ends is only known
        // once the plant's days are.
        List<Supply> read = new ArrayList<>(analysed);
        read.add(plantSupply);
        Map<String, List<DailyPublishedAggregate>> daysByCode =
                getDatadisConsumptionAggregateRepository.aggregateByLocalDay(read, start, end, zone);
        List<PlantDailyEnergy> plantDays = PlantEnergySources.daily(
                getDatadisProductionAggregateRepository.aggregateByLocalDay(plantSupply.getCode(), start, end, zone),
                daysByCode.getOrDefault(plantSupply.getCode(), List.of()),
                PlantEnergySources.cutover(production, zone));

        Optional<YearMonth> published = MonthlyPublication.latestPublished(PlantEnergySources.hoursByMonth(plantDays),
                firstRecord.get(), currentMonth, zone);
        if (published.isEmpty()) {
            return withoutRange(zone, plant, supplies, notComputable, agreements, coefficients);
        }
        LocalDate to = published.get().atEndOfMonth();

        Map<UUID, List<SupplyDailyEnergy>> supplyDays = new HashMap<>();
        for (Supply supply : analysed) {
            supplyDays.put(supply.getId(), daysByCode.getOrDefault(supply.getCode(), List.of()).stream()
                    .filter(day -> !day.day().isAfter(to))
                    .map(day -> new SupplyDailyEnergy(day.day(), day.selfConsumptionKWh() + day.surplusKWh(),
                            day.publishedHours()))
                    .toList());
        }
        return new ObservationInput(zone, plant.getPeakPowerKWp(), plantSupply.getId(), from, to,
                plantDays.stream().filter(day -> !day.day().isAfter(to)).toList(),
                supplies, supplyDays, notComputable, agreements, coefficients);
    }

    /**
     * The input of a plant that is not computable: no analysed range, and no day read.
     */
    private static ObservationInput withoutRange(ZoneId zone, Plant plant, List<ObservedSupply> supplies,
                                                 List<ObservedSupply> notComputable,
                                                 List<ObservedAgreement> agreements,
                                                 List<ObservedCoefficient> coefficients) {
        return new ObservationInput(zone, plant.getPeakPowerKWp(), plant.getSupply().getId(), null, null, List.of(),
                supplies, Map.of(), notComputable, agreements, coefficients);
    }

    private static ObservedSupply observed(Supply supply) {
        return new ObservedSupply(supply.getId(), supply.getCode(), supply.getName());
    }
}

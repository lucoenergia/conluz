package org.lucoenergia.conluz.infrastructure.production.sharingagreement.observation;

import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.GetSupplyPartitionCoefficientRepository;
import org.lucoenergia.conluz.domain.consumption.ReferenceMonthResolver;
import org.lucoenergia.conluz.domain.consumption.datadis.metrics.DailyPublishedAggregate;
import org.lucoenergia.conluz.domain.consumption.datadis.metrics.GetDatadisConsumptionAggregateRepository;
import org.lucoenergia.conluz.domain.production.datadis.get.DailyProductionAggregate;
import org.lucoenergia.conluz.domain.production.datadis.get.GetDatadisProductionAggregateRepository;
import org.lucoenergia.conluz.domain.production.datadis.get.RecordedProductionPeriod;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.plant.PlantNotFoundException;
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
import org.lucoenergia.conluz.domain.production.sharingagreement.observation.PlantDailyEnergy;
import org.lucoenergia.conluz.domain.production.sharingagreement.observation.PlantSharingObservation;
import org.lucoenergia.conluz.domain.production.sharingagreement.observation.SharingAgreementNotObservableException;
import org.lucoenergia.conluz.domain.production.sharingagreement.observation.SharingAgreementObservation;
import org.lucoenergia.conluz.domain.production.sharingagreement.observation.SupplyDailyEnergy;
import org.lucoenergia.conluz.domain.shared.PlantId;
import org.lucoenergia.conluz.domain.shared.time.ZoneResolver;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
 * <p>From the local day of the plant supply's first production record to the end of the latest month published
 * for it (ENM-001). Without either, the plant is not computable.</p>
 *
 * <h2>Query cost</h2>
 *
 * <p>Five relational queries (the plant, its agreements, its coefficients, the community's supplies and plant
 * supplies, the supplies in other plants), whatever the number of supplies. On InfluxDB: one request for the
 * plant supply's recorded production period, two to four for its latest published month, then one query for the
 * daily totals of every analysed supply together and one for the plant's daily production.</p>
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
    private final ReferenceMonthResolver referenceMonthResolver;
    private final ZoneResolver zoneResolver;
    private final ObservedSharingCalculator calculator;

    public GetSharingObservationServiceImpl(GetPlantRepository getPlantRepository,
                                            GetSharingAgreementRepository getSharingAgreementRepository,
                                            GetSupplyPartitionCoefficientRepository getSupplyPartitionCoefficientRepository,
                                            GetSupplyRepository getSupplyRepository,
                                            GetDatadisConsumptionAggregateRepository getDatadisConsumptionAggregateRepository,
                                            GetDatadisProductionAggregateRepository getDatadisProductionAggregateRepository,
                                            ReferenceMonthResolver referenceMonthResolver,
                                            ZoneResolver zoneResolver,
                                            SharingObservationProperties properties) {
        this.getPlantRepository = getPlantRepository;
        this.getSharingAgreementRepository = getSharingAgreementRepository;
        this.getSupplyPartitionCoefficientRepository = getSupplyPartitionCoefficientRepository;
        this.getSupplyRepository = getSupplyRepository;
        this.getDatadisConsumptionAggregateRepository = getDatadisConsumptionAggregateRepository;
        this.getDatadisProductionAggregateRepository = getDatadisProductionAggregateRepository;
        this.referenceMonthResolver = referenceMonthResolver;
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

        Optional<LocalDate[]> range = analysedRange(plantSupply, zone);
        if (range.isEmpty()) {
            return new ObservationInput(zone, plant.getPeakPowerKWp(), plantSupply.getId(), null, null, List.of(),
                    supplies, Map.of(), notComputable, agreements, coefficients);
        }
        LocalDate from = range.get()[0];
        LocalDate to = range.get()[1];
        Instant start = from.atStartOfDay(zone).toInstant();
        Instant end = to.plusDays(1).atStartOfDay(zone).toInstant();

        List<Supply> read = new ArrayList<>(analysed);
        read.add(plantSupply);
        Map<String, List<DailyPublishedAggregate>> daysByCode =
                getDatadisConsumptionAggregateRepository.aggregateByLocalDay(read, start, end, zone);

        Map<LocalDate, Long> plantPublishedHours = daysByCode.getOrDefault(plantSupply.getCode(), List.of()).stream()
                .collect(Collectors.toMap(DailyPublishedAggregate::day, DailyPublishedAggregate::publishedHours));
        List<PlantDailyEnergy> plantDays = new ArrayList<>();
        for (DailyProductionAggregate day : getDatadisProductionAggregateRepository.aggregateByLocalDay(
                plantSupply.getCode(), start, end, zone)) {
            plantDays.add(new PlantDailyEnergy(day.day(), day.productionKWh(),
                    plantPublishedHours.getOrDefault(day.day(), 0L)));
        }

        Map<UUID, List<SupplyDailyEnergy>> supplyDays = new HashMap<>();
        for (Supply supply : analysed) {
            supplyDays.put(supply.getId(), daysByCode.getOrDefault(supply.getCode(), List.of()).stream()
                    .map(day -> new SupplyDailyEnergy(day.day(), day.selfConsumptionKWh() + day.surplusKWh(),
                            day.publishedHours()))
                    .toList());
        }
        return new ObservationInput(zone, plant.getPeakPowerKWp(), plantSupply.getId(), from, to, plantDays,
                supplies, supplyDays, notComputable, agreements, coefficients);
    }

    /**
     * From the local day of the plant supply's first production record to the last day of its latest published
     * month, or empty when it has neither, or when the month ends before the first record.
     */
    private Optional<LocalDate[]> analysedRange(Supply plantSupply, ZoneId zone) {
        Optional<RecordedProductionPeriod> recorded =
                getDatadisProductionAggregateRepository.findRecordedPeriod(plantSupply.getCode());
        if (recorded.isEmpty()) {
            return Optional.empty();
        }
        Optional<YearMonth> published = referenceMonthResolver.resolveLatestPublishedMonth(List.of(plantSupply), zone);
        if (published.isEmpty()) {
            return Optional.empty();
        }
        LocalDate from = recorded.get().firstRecord().atZone(zone).toLocalDate();
        LocalDate to = published.get().atEndOfMonth();
        return to.isBefore(from) ? Optional.empty() : Optional.of(new LocalDate[]{from, to});
    }

    private static ObservedSupply observed(Supply supply) {
        return new ObservedSupply(supply.getId(), supply.getCode(), supply.getName());
    }
}

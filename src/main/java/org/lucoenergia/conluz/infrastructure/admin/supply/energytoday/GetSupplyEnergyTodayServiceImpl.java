package org.lucoenergia.conluz.infrastructure.admin.supply.energytoday;

import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException;
import org.lucoenergia.conluz.domain.admin.supply.energytoday.AssignedProductionCalculator;
import org.lucoenergia.conluz.domain.admin.supply.energytoday.GetSupplyEnergyTodayService;
import org.lucoenergia.conluz.domain.admin.supply.energytoday.PlantProductionInput;
import org.lucoenergia.conluz.domain.admin.supply.energytoday.SupplyProductionToday;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.CoefficientResolver;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.CoefficientSegment;
import org.lucoenergia.conluz.domain.production.huawei.get.GetHuaweiConfigRepository;
import org.lucoenergia.conluz.domain.production.huawei.get.GetHuaweiProductionRepository;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.plant.get.GetPlantRepository;
import org.lucoenergia.conluz.domain.production.plant.inverter.InverterProductionDay;
import org.lucoenergia.conluz.domain.production.plant.inverter.InverterSnapshotIntervals;
import org.lucoenergia.conluz.domain.shared.PlantId;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.domain.shared.time.ZoneResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Reads what today's assigned production of a supply point is derived from: the supply's coefficient segments over
 * the current local day, in the time zone of its community, and the inverter snapshots of each plant it has a
 * coefficient in force in today, the last snapshot of the previous local day included, which only tells a carry-over
 * apart. The stations the snapshots are stored under never leave this class.
 *
 * <h2>Query cost</h2>
 *
 * <p>Two relational queries (the supply, and its coefficient segments of the day), then, per plant with a coefficient
 * in force today, one for whether its Huawei configuration is enabled, and, per enabled plant, one for the plant and
 * one InfluxDB request of two statements. A supply holds a coefficient in few plants.
 */
@Service
@Transactional(readOnly = true)
public class GetSupplyEnergyTodayServiceImpl implements GetSupplyEnergyTodayService {

    private final GetSupplyRepository getSupplyRepository;
    private final CoefficientResolver coefficientResolver;
    private final GetHuaweiConfigRepository getHuaweiConfigRepository;
    private final GetPlantRepository getPlantRepository;
    private final GetHuaweiProductionRepository getHuaweiProductionRepository;
    private final ZoneResolver zoneResolver;
    private final Clock clock;

    public GetSupplyEnergyTodayServiceImpl(GetSupplyRepository getSupplyRepository,
                                           CoefficientResolver coefficientResolver,
                                           GetHuaweiConfigRepository getHuaweiConfigRepository,
                                           GetPlantRepository getPlantRepository,
                                           GetHuaweiProductionRepository getHuaweiProductionRepository,
                                           ZoneResolver zoneResolver,
                                           Clock clock) {
        this.getSupplyRepository = getSupplyRepository;
        this.coefficientResolver = coefficientResolver;
        this.getHuaweiConfigRepository = getHuaweiConfigRepository;
        this.getPlantRepository = getPlantRepository;
        this.getHuaweiProductionRepository = getHuaweiProductionRepository;
        this.zoneResolver = zoneResolver;
        this.clock = clock;
    }

    @Override
    public SupplyProductionToday getToday(UUID supplyId) {

        SupplyId id = SupplyId.of(supplyId);
        Supply supply = getSupplyRepository.findById(id).orElseThrow(() -> new SupplyNotFoundException(id));

        ZoneId zone = zoneResolver.resolveZoneIdForCommunity(supply.getCommunity().getId());
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, zone);
        Instant todayStart = today.atStartOfDay(zone).toInstant();

        Map<UUID, List<CoefficientSegment>> segmentsInForceToday = new TreeMap<>();
        coefficientResolver.resolveSegmentsBySupply(supplyId, todayStart,
                        today.plusDays(1).atStartOfDay(zone).toInstant())
                .forEach((plantId, segments) -> {
                    if (segments.stream().anyMatch(segment -> !segment.from().isAfter(now))) {
                        segmentsInForceToday.put(plantId, segments);
                    }
                });
        if (segmentsInForceToday.isEmpty()) {
            return SupplyProductionToday.noCoefficientToday();
        }

        List<UUID> enabledPlantIds = segmentsInForceToday.keySet().stream()
                .filter(getHuaweiConfigRepository::isEnabled)
                .toList();
        if (enabledPlantIds.isEmpty()) {
            return SupplyProductionToday.notAvailable();
        }

        Instant yesterdayStart = today.minusDays(1).atStartOfDay(zone).toInstant();
        List<PlantProductionInput> plants = new ArrayList<>();
        for (UUID plantId : enabledPlantIds) {
            snapshotsOfToday(plantId, yesterdayStart, todayStart, now)
                    .ifPresent(snapshots -> plants.add(
                            new PlantProductionInput(snapshots, segmentsInForceToday.get(plantId))));
        }
        if (plants.isEmpty()) {
            return SupplyProductionToday.noDataToday();
        }

        return AssignedProductionCalculator.calculate(plants, todayStart, now, zone);
    }

    private Optional<InverterSnapshotIntervals> snapshotsOfToday(UUID plantId, Instant yesterdayStart,
                                                                 Instant todayStart, Instant now) {
        // A coefficient references its plant through a foreign key, so a missing plant is a broken invariant.
        Plant plant = getPlantRepository.findById(PlantId.of(plantId))
                .orElseThrow(() -> new IllegalStateException("Plant " + plantId + " of a coefficient not found"));
        InverterProductionDay day = getHuaweiProductionRepository.getProductionDay(plant.getProviderCode(),
                yesterdayStart, todayStart, now);
        return InverterSnapshotIntervals.of(day.previousDayLastKWh(), day.todaySnapshots());
    }
}

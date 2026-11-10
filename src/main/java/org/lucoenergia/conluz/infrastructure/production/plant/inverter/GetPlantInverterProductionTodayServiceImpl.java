package org.lucoenergia.conluz.infrastructure.production.plant.inverter;

import org.lucoenergia.conluz.domain.production.huawei.get.GetHuaweiConfigRepository;
import org.lucoenergia.conluz.domain.production.huawei.get.GetHuaweiProductionRepository;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.plant.PlantNotFoundException;
import org.lucoenergia.conluz.domain.production.plant.get.GetPlantRepository;
import org.lucoenergia.conluz.domain.production.plant.inverter.GetPlantInverterProductionTodayService;
import org.lucoenergia.conluz.domain.production.plant.inverter.InverterProductionDay;
import org.lucoenergia.conluz.domain.production.plant.inverter.InverterProductionToday;
import org.lucoenergia.conluz.domain.production.plant.inverter.PlantInverterProductionToday;
import org.lucoenergia.conluz.domain.shared.PlantId;
import org.lucoenergia.conluz.domain.shared.time.ZoneResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

/**
 * Reads the plant's real-time inverter snapshots of the current local day, in the time zone of the plant's
 * community, and the last snapshot of the previous local day, which only tells a carry-over apart. The station the
 * snapshots are stored under is the plant's provider code; it never leaves this class.
 *
 * <h2>Query cost</h2>
 *
 * <p>Two relational queries (the plant, and whether its Huawei configuration is enabled). No InfluxDB request when
 * the configuration is missing or disabled, one otherwise, of two statements.
 */
@Service
@Transactional(readOnly = true)
public class GetPlantInverterProductionTodayServiceImpl implements GetPlantInverterProductionTodayService {

    private final GetPlantRepository getPlantRepository;
    private final GetHuaweiConfigRepository getHuaweiConfigRepository;
    private final GetHuaweiProductionRepository getHuaweiProductionRepository;
    private final ZoneResolver zoneResolver;
    private final Clock clock;

    public GetPlantInverterProductionTodayServiceImpl(GetPlantRepository getPlantRepository,
                                                      GetHuaweiConfigRepository getHuaweiConfigRepository,
                                                      GetHuaweiProductionRepository getHuaweiProductionRepository,
                                                      ZoneResolver zoneResolver,
                                                      Clock clock) {
        this.getPlantRepository = getPlantRepository;
        this.getHuaweiConfigRepository = getHuaweiConfigRepository;
        this.getHuaweiProductionRepository = getHuaweiProductionRepository;
        this.zoneResolver = zoneResolver;
        this.clock = clock;
    }

    @Override
    public PlantInverterProductionToday getToday(UUID plantId) {

        PlantId id = PlantId.of(plantId);
        Plant plant = getPlantRepository.findById(id).orElseThrow(() -> new PlantNotFoundException(id));

        if (!getHuaweiConfigRepository.isEnabled(plantId)) {
            return PlantInverterProductionToday.notAvailable();
        }

        ZoneId zone = zoneResolver.resolveZoneIdForCommunity(plant.getCommunity().getId());
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, zone);
        InverterProductionDay day = getHuaweiProductionRepository.getProductionDay(plant.getProviderCode(),
                today.minusDays(1).atStartOfDay(zone).toInstant(), today.atStartOfDay(zone).toInstant(), now);

        return InverterProductionToday.of(day.previousDayLastKWh(), day.todaySnapshots())
                .map(production -> PlantInverterProductionToday.available(production, now, zone))
                .orElseGet(PlantInverterProductionToday::noDataToday);
    }
}

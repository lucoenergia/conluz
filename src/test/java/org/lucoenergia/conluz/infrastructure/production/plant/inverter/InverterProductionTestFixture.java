package org.lucoenergia.conluz.infrastructure.production.plant.inverter;

import org.influxdb.InfluxDB;
import org.influxdb.dto.Query;
import org.lucoenergia.conluz.domain.production.huawei.HuaweiConfig;
import org.lucoenergia.conluz.domain.production.huawei.RealTimeProduction;
import org.lucoenergia.conluz.domain.production.huawei.config.SetHuaweiConfigurationRepository;
import org.lucoenergia.conluz.domain.production.huawei.persist.PersistHuaweiProductionRepository;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.infrastructure.shared.db.influxdb.InfluxDbConnectionManager;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Configures a plant's Huawei integration and writes its real-time snapshots the way the Huawei sync does: through
 * the configuration repository, and through the persistence repository under the plant's provider code as the
 * station.
 */
@Profile("test")
@Component
public class InverterProductionTestFixture {

    private final SetHuaweiConfigurationRepository setHuaweiConfigurationRepository;
    private final PersistHuaweiProductionRepository persistHuaweiProductionRepository;
    private final InfluxDbConnectionManager influxDbConnectionManager;

    private final List<String> writtenStations = new ArrayList<>();

    public InverterProductionTestFixture(SetHuaweiConfigurationRepository setHuaweiConfigurationRepository,
                                         PersistHuaweiProductionRepository persistHuaweiProductionRepository,
                                         InfluxDbConnectionManager influxDbConnectionManager) {
        this.setHuaweiConfigurationRepository = setHuaweiConfigurationRepository;
        this.persistHuaweiProductionRepository = persistHuaweiProductionRepository;
        this.influxDbConnectionManager = influxDbConnectionManager;
    }

    public void configure(Plant plant, boolean enabled) {
        setHuaweiConfigurationRepository.setHuaweiConfiguration(plant.getId(), new HuaweiConfig.Builder()
                .setUsername("inverter-user")
                .setPassword("inverter-password")
                .setBaseUrl(HuaweiConfig.DEFAULT_BASE_URL)
                .setEnabled(enabled)
                .build());
    }

    /**
     * One snapshot of the plant's station at each instant, in one write, as the sync stores a batch.
     */
    public void writeSnapshots(Plant plant, Map<Instant, Double> dayPowerKWhByTime) {
        String station = plant.getProviderCode();
        if (!writtenStations.contains(station)) {
            writtenStations.add(station);
        }
        persistHuaweiProductionRepository.persistRealTimeProduction(dayPowerKWhByTime.entrySet().stream()
                .map(snapshot -> new RealTimeProduction.Builder()
                        .setTime(snapshot.getKey())
                        .setStationCode(station)
                        .setRealHealthState(3)
                        .setDayPower(snapshot.getValue())
                        .setTotalPower(84963.11)
                        .setDayIncome(0.0)
                        .setMonthPower(2630.5)
                        .setTotalIncome(0.0)
                        .build())
                .toList());
    }

    public void writeSnapshot(Plant plant, Instant time, double dayPowerKWh) {
        writeSnapshots(plant, Map.of(time, dayPowerKWh));
    }

    /**
     * Removes every snapshot written since the last call.
     */
    public void clear() {
        // No port deletes snapshots: production never does.
        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {
            writtenStations.forEach(station -> connection.query(new Query(String.format(
                    "DROP SERIES FROM \"%s\" WHERE \"station_code\" = '%s'",
                    HuaweiConfig.HUAWEI_REAL_TIME_PRODUCTION_MEASUREMENT, station))));
        }
        writtenStations.clear();
    }
}

package org.lucoenergia.conluz.infrastructure.production.get;

import org.influxdb.InfluxDB;
import org.influxdb.dto.Query;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.production.huawei.HuaweiConfig;
import org.lucoenergia.conluz.domain.production.huawei.RealTimeProduction;
import org.lucoenergia.conluz.domain.production.huawei.get.GetHuaweiProductionRepository;
import org.lucoenergia.conluz.domain.production.huawei.persist.PersistHuaweiProductionRepository;
import org.lucoenergia.conluz.domain.production.plant.inverter.InverterProductionDay;
import org.lucoenergia.conluz.domain.production.plant.inverter.InverterSnapshot;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.lucoenergia.conluz.infrastructure.shared.db.influxdb.InfluxDbConnectionManager;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Against a real InfluxDB: the half-open bounds of both days, the order of today's snapshots, their millisecond
 * instants, and the station filter. Snapshots are written through the repository the Huawei sync uses, each test
 * under its own stations.
 */
class GetHuaweiProductionRepositoryInfluxIntegrationTest extends BaseIntegrationTest {

    /** Local midnights of 2026-09-14 and 2026-09-15 in Madrid. */
    private static final Instant YESTERDAY_START = Instant.parse("2026-09-13T22:00:00Z");
    private static final Instant TODAY_START = Instant.parse("2026-09-14T22:00:00Z");
    private static final Instant NOW = Instant.parse("2026-09-15T10:00:00Z");

    @Autowired
    private GetHuaweiProductionRepository repository;
    @Autowired
    private PersistHuaweiProductionRepository persistHuaweiProductionRepository;
    @Autowired
    private InfluxDbConnectionManager influxDbConnectionManager;

    private final List<String> writtenStations = new ArrayList<>();

    @AfterEach
    void clear() {
        // No port deletes snapshots: production never does.
        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {
            writtenStations.forEach(station -> connection.query(new Query(String.format(
                    "DROP SERIES FROM \"%s\" WHERE \"station_code\" = '%s'",
                    HuaweiConfig.HUAWEI_REAL_TIME_PRODUCTION_MEASUREMENT, station.replace("'", "\\'")))));
        }
    }

    @Test
    @DisplayName("PIP-002 yesterday's last value and today's snapshots in order, each day half-open")
    void readsYesterdaysLastValueAndTodaysSnapshotsInOrder() {
        String station = station();
        write(station, YESTERDAY_START.minusSeconds(1), 999.0);
        write(station, YESTERDAY_START, 1.0);
        write(station, TODAY_START.minusSeconds(300), 323.36);
        write(station, TODAY_START.plusMillis(980), 323.36);
        write(station, TODAY_START, 323.30);
        write(station, Instant.parse("2026-09-15T08:05:00Z"), 0.04);
        write(station, Instant.parse("2026-09-14T22:05:00Z"), 0.0);
        write(station, NOW, 50.0);

        InverterProductionDay day = repository.getProductionDay(station, YESTERDAY_START, TODAY_START, NOW);

        assertEquals(323.36, day.previousDayLastKWh());
        assertEquals(List.of(
                new InverterSnapshot(TODAY_START, 323.30),
                new InverterSnapshot(TODAY_START.plusMillis(980), 323.36),
                new InverterSnapshot(Instant.parse("2026-09-14T22:05:00Z"), 0.0),
                new InverterSnapshot(Instant.parse("2026-09-15T08:05:00Z"), 0.04)), day.todaySnapshots());
    }

    @Test
    @DisplayName("PIP-005 another station's snapshots never reach the plant's")
    void anotherStationsSnapshotsNeverReachThePlants() {
        String station = station();
        String other = station();
        write(station, Instant.parse("2026-09-15T08:00:00Z"), 1.0);
        write(other, Instant.parse("2026-09-14T21:55:00Z"), 80.0);
        write(other, Instant.parse("2026-09-15T08:00:00Z"), 7.0);
        write(other, Instant.parse("2026-09-15T08:05:00Z"), 7.5);

        InverterProductionDay day = repository.getProductionDay(station, YESTERDAY_START, TODAY_START, NOW);

        assertNull(day.previousDayLastKWh());
        assertEquals(List.of(new InverterSnapshot(Instant.parse("2026-09-15T08:00:00Z"), 1.0)),
                day.todaySnapshots());
    }

    @Test
    @DisplayName("PIP-001 a station without snapshots reads nothing for either day")
    void aStationWithoutSnapshotsReadsNothing() {
        InverterProductionDay day = repository.getProductionDay(station(), YESTERDAY_START, TODAY_START, NOW);

        assertNull(day.previousDayLastKWh());
        assertEquals(List.of(), day.todaySnapshots());
    }

    @Test
    @DisplayName("PIP-005 a quote in the station code stays inside the filter")
    void aQuoteInTheStationCodeStaysInsideTheFilter() {
        String quoted = "NE='" + UUID.randomUUID();
        writtenStations.add(quoted);
        write(quoted, Instant.parse("2026-09-15T08:00:00Z"), 2.5);

        InverterProductionDay day = repository.getProductionDay(quoted, YESTERDAY_START, TODAY_START, NOW);

        assertEquals(List.of(new InverterSnapshot(Instant.parse("2026-09-15T08:00:00Z"), 2.5)),
                day.todaySnapshots());
    }

    private String station() {
        String station = "NE=" + UUID.randomUUID();
        writtenStations.add(station);
        return station;
    }

    private void write(String station, Instant time, double dayPowerKWh) {
        persistHuaweiProductionRepository.persistRealTimeProduction(List.of(new RealTimeProduction.Builder()
                .setTime(time)
                .setStationCode(station)
                .setRealHealthState(3)
                .setDayPower(dayPowerKWh)
                .setTotalPower(1000.0)
                .setDayIncome(0.0)
                .setMonthPower(100.0)
                .setTotalIncome(0.0)
                .build()));
    }
}

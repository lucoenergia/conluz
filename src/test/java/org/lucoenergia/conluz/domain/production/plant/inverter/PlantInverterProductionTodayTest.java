package org.lucoenergia.conluz.domain.production.plant.inverter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PlantInverterProductionTodayTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Madrid");
    private static final Instant LAST_SNAPSHOT = Instant.parse("2026-09-15T10:00:00.250Z");

    @ParameterizedTest
    @CsvSource({"59, false", "60, false", "61, true"})
    @DisplayName("PIP-004 AC10 the latest snapshot is stale only when it is more than 60 minutes old")
    void theLatestSnapshotIsStaleOnlyPastSixtyMinutes(long minutesOld, boolean stale) {
        InverterProductionToday production = InverterProductionToday.of(null, List.of(
                new InverterSnapshot(LAST_SNAPSHOT.minus(Duration.ofMinutes(5)), 1.0),
                new InverterSnapshot(LAST_SNAPSHOT, 1.2))).orElseThrow();

        PlantInverterProductionToday today = PlantInverterProductionToday.available(production,
                LAST_SNAPSHOT.plus(Duration.ofMinutes(minutesOld)), ZONE);

        assertEquals(stale, today.getStale());
    }

    @Test
    @DisplayName("PIP-001 AC1 with a snapshot today the status is available and every figure is reported")
    void withASnapshotTodayTheStatusIsAvailable() {
        InverterProductionToday production = InverterProductionToday.of(null, List.of(
                new InverterSnapshot(LAST_SNAPSHOT.minus(Duration.ofMinutes(5)), 1.0),
                new InverterSnapshot(LAST_SNAPSHOT, 1.2))).orElseThrow();

        PlantInverterProductionToday today = PlantInverterProductionToday.available(production, LAST_SNAPSHOT, ZONE);

        assertEquals(InverterProductionStatus.AVAILABLE, today.getStatus());
        assertEquals(1.2, today.getTodayEnergyKWh());
        assertEquals(production.getPowerCurve(), today.getPowerCurve());
        assertEquals(LAST_SNAPSHOT, today.getLastUpdatedAt());
        assertEquals(false, today.getStale());
        assertEquals(ZONE, today.getZone());
    }

    @Test
    @DisplayName("PIP-001 AC2 not available leaves every other figure null")
    void notAvailableLeavesEveryFigureNull() {
        assertOnlyTheStatus(PlantInverterProductionToday.notAvailable(), InverterProductionStatus.NOT_AVAILABLE);
    }

    @Test
    @DisplayName("PIP-001 AC3 no data today leaves every other figure null")
    void noDataTodayLeavesEveryFigureNull() {
        assertOnlyTheStatus(PlantInverterProductionToday.noDataToday(), InverterProductionStatus.NO_DATA_TODAY);
    }

    private static void assertOnlyTheStatus(PlantInverterProductionToday today, InverterProductionStatus status) {
        assertEquals(status, today.getStatus());
        assertNull(today.getTodayEnergyKWh());
        assertNull(today.getPowerCurve());
        assertNull(today.getLastUpdatedAt());
        assertNull(today.getStale());
        assertNull(today.getZone());
    }
}

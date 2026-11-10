package org.lucoenergia.conluz.infrastructure.production.plant.inverter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.production.plant.inverter.InverterProductionStatus;
import org.lucoenergia.conluz.domain.production.plant.inverter.InverterProductionToday;
import org.lucoenergia.conluz.domain.production.plant.inverter.InverterSnapshot;
import org.lucoenergia.conluz.domain.production.plant.inverter.PlantInverterProductionToday;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PlantInverterProductionTodayResponseTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Madrid");

    @Test
    @DisplayName("EVL-001 the energy and the power are rounded to 3 decimals, half-up, at response mapping")
    void theEnergyAndThePowerAreRoundedAtResponseMapping() {
        InverterProductionToday production = InverterProductionToday.of(null, List.of(
                new InverterSnapshot(Instant.parse("2026-09-15T10:00:00Z"), 1.0),
                // 0.1 kWh over 301 s: 1.19601328... kW.
                new InverterSnapshot(Instant.parse("2026-09-15T10:05:01Z"), 1.1),
                new InverterSnapshot(Instant.parse("2026-09-15T10:30:01Z"), 12.3455))).orElseThrow();

        PlantInverterProductionTodayResponse response = new PlantInverterProductionTodayResponse(
                PlantInverterProductionToday.available(production, Instant.parse("2026-09-15T10:31:00Z"), ZONE));

        assertEquals(InverterProductionStatus.AVAILABLE, response.getStatus());
        assertEquals(12.346, response.getTodayEnergyKWh());
        assertEquals(1.196, response.getPowerCurve().get(0).getPowerKW());
        assertNull(response.getPowerCurve().get(1).getPowerKW());
        assertEquals(OffsetDateTime.parse("2026-09-15T12:05:01+02:00"), response.getPowerCurve().get(0).getEndsAt());
        assertEquals(OffsetDateTime.parse("2026-09-15T12:30:01+02:00"), response.getLastUpdatedAt());
        assertEquals(false, response.getStale());
    }

    @Test
    @DisplayName("PIP-001 AC2 not available maps to null figures")
    void notAvailableMapsToNullFigures() {
        PlantInverterProductionTodayResponse response =
                new PlantInverterProductionTodayResponse(PlantInverterProductionToday.notAvailable());

        assertEquals(InverterProductionStatus.NOT_AVAILABLE, response.getStatus());
        assertNull(response.getTodayEnergyKWh());
        assertNull(response.getPowerCurve());
        assertNull(response.getLastUpdatedAt());
        assertNull(response.getStale());
    }
}

package org.lucoenergia.conluz.infrastructure.production.plant.inverter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.production.plant.inverter.InverterPowerPoint;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PlantInverterPowerPointResponseTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Madrid");

    @Test
    @DisplayName("EVL-001 the power is rounded to 3 decimals, half-up, and the instant carries the community's offset")
    void thePowerIsRoundedAndTheInstantCarriesTheCommunitysOffset() {
        PlantInverterPowerPointResponse response = new PlantInverterPowerPointResponse(
                new InverterPowerPoint(Instant.parse("2026-12-01T07:05:00Z"), 0.4805), ZONE);

        assertEquals(0.481, response.getPowerKW());
        assertEquals(OffsetDateTime.parse("2026-12-01T08:05:00+01:00"), response.getEndsAt());
    }

    @Test
    @DisplayName("PIP-003 a gap stays a null power")
    void aGapStaysANullPower() {
        PlantInverterPowerPointResponse response = new PlantInverterPowerPointResponse(
                new InverterPowerPoint(Instant.parse("2026-12-01T07:05:00Z"), null), ZONE);

        assertNull(response.getPowerKW());
    }
}

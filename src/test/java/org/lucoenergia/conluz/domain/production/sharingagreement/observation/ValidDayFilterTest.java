package org.lucoenergia.conluz.domain.production.sharingagreement.observation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.production.plant.energy.PlantDailyEnergy;
import org.lucoenergia.conluz.domain.production.plant.energy.PlantEnergySource;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ValidDayFilterTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final LocalDate FROM = LocalDate.parse("2023-06-01");
    private static final LocalDate TO = LocalDate.parse("2026-10-31");

    // A 100 kWp plant needs 50 kWh a day.
    private final ValidDayFilter filter = new ValidDayFilter(MADRID, 100);

    @Test
    @DisplayName("OBS-001 a day is plant-valid from the legacy surplus or from production when its own source covers it")
    void aDayIsPlantValidFromEitherSource() {
        List<PlantDailyEnergy> days = List.of(
                day("2024-10-15", 140, 24, PlantEnergySource.LEGACY_SURPLUS),
                day("2025-01-15", 60, 24, PlantEnergySource.PRODUCTION));

        assertEquals(Map.of(LocalDate.parse("2024-10-15"), 140.0, LocalDate.parse("2025-01-15"), 60.0),
                filter.plantValidDays(days, FROM, TO));
    }

    /**
     * The daily form of ENM-001 asks for 22 of 24 hours (21.6 rounded up).
     */
    @Test
    @DisplayName("OBS-001 a day whose own source holds fewer hours than the daily threshold is not plant-valid")
    void aDayBelowTheDailyThresholdIsNotPlantValid() {
        List<PlantDailyEnergy> days = List.of(
                day("2024-10-15", 140, 21, PlantEnergySource.LEGACY_SURPLUS),
                day("2024-10-16", 140, 22, PlantEnergySource.LEGACY_SURPLUS),
                day("2024-12-01", 140, 14, PlantEnergySource.PRODUCTION),
                day("2024-12-02", 140, 22, PlantEnergySource.PRODUCTION));

        assertEquals(Map.of(LocalDate.parse("2024-10-16"), 140.0, LocalDate.parse("2024-12-02"), 140.0),
                filter.plantValidDays(days, FROM, TO));
    }

    @Test
    @DisplayName("OBS-001 a covered day producing less than max(0.5 kWh × kWp, 12 kWh) is not plant-valid")
    void aDayBelowTheProductionFloorIsNotPlantValid() {
        List<PlantDailyEnergy> days = List.of(
                day("2024-10-15", 49.9, 24, PlantEnergySource.LEGACY_SURPLUS),
                day("2025-01-15", 49.9, 24, PlantEnergySource.PRODUCTION));

        assertEquals(Map.of(), filter.plantValidDays(days, FROM, TO));
    }

    private static PlantDailyEnergy day(String day, double kWh, long hours, PlantEnergySource source) {
        return new PlantDailyEnergy(LocalDate.parse(day), kWh, hours, source);
    }
}

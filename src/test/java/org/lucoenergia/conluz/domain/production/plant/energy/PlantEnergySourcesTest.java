package org.lucoenergia.conluz.domain.production.plant.energy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.consumption.RecordedConsumptionPeriod;
import org.lucoenergia.conluz.domain.consumption.datadis.metrics.DailyPublishedAggregate;
import org.lucoenergia.conluz.domain.production.datadis.get.DailyProductionAggregate;
import org.lucoenergia.conluz.domain.production.datadis.get.RecordedProductionPeriod;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlantEnergySourcesTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final LocalDate CUTOVER = LocalDate.parse("2024-12-01");
    private static final Instant LAST = Instant.parse("2026-10-31T22:00:00Z");

    @Test
    @DisplayName("OBS-001 the first record is the earlier of the first surplus record and the first production record")
    void theFirstRecordIsTheEarlierOfBothSources() {
        Instant legacy = Instant.parse("2023-05-31T22:00:00Z");
        Instant production = Instant.parse("2024-11-30T23:00:00Z");

        assertEquals(Optional.of(legacy), PlantEnergySources.firstRecord(productionFrom(production),
                surplusFrom(legacy)));
        assertEquals(Optional.of(legacy), PlantEnergySources.firstRecord(Optional.empty(), surplusFrom(legacy)));
        assertEquals(Optional.of(production), PlantEnergySources.firstRecord(productionFrom(production),
                Optional.empty()));
        assertEquals(Optional.of(production), PlantEnergySources.firstRecord(productionFrom(production),
                surplusFrom(production.plusSeconds(3600))));
        assertEquals(Optional.empty(), PlantEnergySources.firstRecord(Optional.empty(), Optional.empty()));
    }

    @Test
    @DisplayName("OBS-001 the cutover is the local day of the first production record, none without production")
    void theCutoverIsTheLocalDayOfTheFirstProductionRecord() {
        // 2024-11-30T23:00Z is midnight of 1 December in Madrid.
        assertEquals(Optional.of(CUTOVER),
                PlantEnergySources.cutover(productionFrom(Instant.parse("2024-11-30T23:00:00Z")), MADRID));
        assertEquals(Optional.empty(), PlantEnergySources.cutover(Optional.empty(), MADRID));
    }

    @Test
    @DisplayName("OBS-001 a day before the cutover is read from the legacy surplus, one on or after it from production")
    void eachDayIsReadFromItsOwnSource() {
        List<PlantDailyEnergy> days = PlantEnergySources.daily(
                List.of(production("2024-12-01", 90, 24), production("2024-12-02", 80, 24)),
                List.of(legacy("2024-11-30", 70, 24), legacy("2024-12-01", 0, 24), legacy("2024-12-02", 0, 24)),
                Optional.of(CUTOVER));

        assertEquals(List.of(
                new PlantDailyEnergy(LocalDate.parse("2024-11-30"), 70, 24, PlantEnergySource.LEGACY_SURPLUS),
                new PlantDailyEnergy(CUTOVER, 90, 24, PlantEnergySource.PRODUCTION),
                new PlantDailyEnergy(LocalDate.parse("2024-12-02"), 80, 24, PlantEnergySource.PRODUCTION)), days);
    }

    /**
     * The sync zeroes the legacy surplus once it writes production; a surplus left non-zero there must not be read
     * either, so its value cannot pass for the day's production.
     */
    @Test
    @DisplayName("OBS-001 a surplus stored on or after the cutover is never read, zero or not")
    void aSurplusOnOrAfterTheCutoverIsNeverRead() {
        List<PlantDailyEnergy> days = PlantEnergySources.daily(List.of(),
                List.of(legacy("2024-12-01", 55, 24), legacy("2025-01-15", 0, 24)), Optional.of(CUTOVER));

        assertEquals(List.of(), days);
    }

    /**
     * Production starts at 10:00 local: the day holds its 14 production hours, never the 10 legacy hours before.
     */
    @Test
    @DisplayName("OBS-001 a cutover inside a local day leaves that day with its production hours alone")
    void aCutoverInsideADayLeavesItsProductionHoursAlone() {
        Optional<LocalDate> cutover = PlantEnergySources.cutover(
                productionFrom(Instant.parse("2024-12-01T09:00:00Z")), MADRID);

        List<PlantDailyEnergy> days = PlantEnergySources.daily(List.of(production("2024-12-01", 40, 14)),
                List.of(legacy("2024-11-30", 70, 24), legacy("2024-12-01", 30, 24)), cutover);

        assertEquals(List.of(
                new PlantDailyEnergy(LocalDate.parse("2024-11-30"), 70, 24, PlantEnergySource.LEGACY_SURPLUS),
                new PlantDailyEnergy(CUTOVER, 40, 14, PlantEnergySource.PRODUCTION)), days);
    }

    @Test
    @DisplayName("OBS-001 without any production record, every day is read from the legacy surplus")
    void withoutProductionEveryDayIsLegacy() {
        List<PlantDailyEnergy> days = PlantEnergySources.daily(List.of(),
                List.of(legacy("2023-06-01", 50, 24), legacy("2026-10-31", 20, 23)), Optional.empty());

        assertEquals(List.of(
                new PlantDailyEnergy(LocalDate.parse("2023-06-01"), 50, 24, PlantEnergySource.LEGACY_SURPLUS),
                new PlantDailyEnergy(LocalDate.parse("2026-10-31"), 20, 23, PlantEnergySource.LEGACY_SURPLUS)),
                days);
    }

    @Test
    @DisplayName("OBS-001 a legacy day whose records carry no surplus holds no plant energy")
    void aLegacyDayWithoutSurplusRecordsIsAbsent() {
        List<PlantDailyEnergy> days = PlantEnergySources.daily(List.of(), List.of(legacy("2023-05-20", 0, 0)),
                Optional.of(CUTOVER));

        assertEquals(List.of(), days);
    }

    @Test
    @DisplayName("ENM-009 the hours are summed per month, each day from its own source")
    void theHoursAreSummedPerMonth() {
        List<PlantDailyEnergy> days = List.of(
                new PlantDailyEnergy(LocalDate.parse("2024-11-29"), 1, 24, PlantEnergySource.LEGACY_SURPLUS),
                new PlantDailyEnergy(LocalDate.parse("2024-11-30"), 1, 20, PlantEnergySource.LEGACY_SURPLUS),
                new PlantDailyEnergy(CUTOVER, 1, 14, PlantEnergySource.PRODUCTION));

        assertEquals(Map.of(YearMonth.of(2024, 11), 44L, YearMonth.of(2024, 12), 14L),
                PlantEnergySources.hoursByMonth(days));
    }

    private static Optional<RecordedProductionPeriod> productionFrom(Instant first) {
        return Optional.of(new RecordedProductionPeriod(first, LAST));
    }

    private static Optional<RecordedConsumptionPeriod> surplusFrom(Instant first) {
        return Optional.of(new RecordedConsumptionPeriod(first, LAST));
    }

    private static DailyProductionAggregate production(String day, double kWh, long hours) {
        return new DailyProductionAggregate(LocalDate.parse(day), kWh, hours);
    }

    private static DailyPublishedAggregate legacy(String day, double surplusKWh, long surplusHours) {
        return new DailyPublishedAggregate(LocalDate.parse(day), 0.1, 0, surplusKWh, 0, surplusHours, "Real");
    }
}

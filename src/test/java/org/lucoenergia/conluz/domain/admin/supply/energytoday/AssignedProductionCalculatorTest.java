package org.lucoenergia.conluz.domain.admin.supply.energytoday;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.CoefficientSegment;
import org.lucoenergia.conluz.domain.production.plant.inverter.InverterProductionToday;
import org.lucoenergia.conluz.domain.production.plant.inverter.InverterSnapshot;
import org.lucoenergia.conluz.domain.production.plant.inverter.InverterSnapshotIntervals;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hand-built snapshots and coefficients of 2026-09-15 in Madrid, unless a test says otherwise. Every figure is
 * compared unrounded.
 */
class AssignedProductionCalculatorTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Madrid");
    private static final LocalDate DAY = LocalDate.of(2026, 9, 15);
    private static final Instant TODAY_START = DAY.atStartOfDay(ZONE).toInstant();
    private static final Instant TOMORROW_START = DAY.plusDays(1).atStartOfDay(ZONE).toInstant();
    private static final double TOLERANCE = 1e-9;
    /** 5 minutes in hours: a slot's energy divided by it is the slot's average power. */
    private static final double SLOT_HOURS = 5 / 60.0;

    @Test
    @DisplayName("PIP-008 AC7 an interval takes the β in force at its start, also when β changes before its end")
    void anIntervalTakesTheCoefficientInForceAtItsStart() {
        PlantProductionInput plant = plant(null, List.of(
                        snapshot("10:00", 1.0), snapshot("10:05", 1.5), snapshot("10:10", 2.1),
                        snapshot("10:15", 2.4)),
                segment(TODAY_START, at("10:07"), "0.1"),
                segment(at("10:07"), TOMORROW_START, "0.3"));

        SupplyProductionToday today = calculate(List.of(plant), at("10:16"));

        // Leading 1.0 × 0.1, then 0.5 × 0.1, then 0.6 × 0.1 (starts at 10:05, before the change), then 0.3 × 0.3.
        assertEquals(0.1 + 0.05 + 0.06 + 0.09, today.getAssignedEnergyKWh(), TOLERANCE);
        assertEquals(0.06 / SLOT_HOURS, powerAt(today, "10:10"), TOLERANCE);
        assertEquals(0.09 / SLOT_HOURS, powerAt(today, "10:15"), TOLERANCE);
        assertEquals(0.3, today.getPartitionCoefficient(), TOLERANCE);
    }

    @Test
    @DisplayName("PIP-008 AC7 with a single plant and a constant β, the assigned energy is today's energy × β")
    void withAConstantCoefficientTheAssignedEnergyIsTodaysEnergyTimesTheCoefficient() {
        double yesterdayTotal = 323.36;
        List<InverterSnapshot> snapshots = new ArrayList<>();
        snapshots.add(snapshot("00:00:00.980", yesterdayTotal));
        double dayPower = 0;
        for (LocalTime time = LocalTime.of(0, 5); !time.isAfter(LocalTime.of(13, 0)); time = time.plusMinutes(5)) {
            if (time.isAfter(LocalTime.of(8, 0))) {
                dayPower += 0.25;
            }
            // A 40-minute gap and a decrease-free series; the gap still counts.
            if (time.isAfter(LocalTime.of(11, 0)) && time.isBefore(LocalTime.of(11, 40))) {
                continue;
            }
            snapshots.add(snapshot(time.toString(), dayPower));
        }
        PlantProductionInput plant = plant(yesterdayTotal, snapshots, segment(TODAY_START, TOMORROW_START, "0.0425"));

        SupplyProductionToday today = calculate(List.of(plant), at("13:01"));

        double todayEnergy = InverterProductionToday.of(yesterdayTotal, snapshots).orElseThrow().getEnergyKWh();
        assertEquals(todayEnergy * 0.0425, today.getAssignedEnergyKWh(), TOLERANCE);
    }

    @Test
    @DisplayName("PIP-008 AC7 the energy up to the first snapshot of today is a leading interval with β at midnight")
    void theEnergyUpToTheFirstSnapshotIsALeadingInterval() {
        PlantProductionInput plant = plant(null, List.of(snapshot("10:00", 5.0), snapshot("10:05", 5.5)),
                segment(TODAY_START, TOMORROW_START, "0.2"));

        SupplyProductionToday today = calculate(List.of(plant), at("10:06"));

        assertEquals(5.5 * 0.2, today.getAssignedEnergyKWh(), TOLERANCE);
        // The leading interval, 10 hours long, is a gap: every slot up to 10:00 is null.
        assertTrue(today.getAssignedPowerCurve().stream()
                .filter(point -> !point.endsAt().isAfter(at("10:00")))
                .allMatch(point -> point.powerKW() == null));
        assertEquals(0.5 * 0.2 / SLOT_HOURS, powerAt(today, "10:05"), TOLERANCE);
    }

    @Test
    @DisplayName("PIP-008 PIP-009 AC7 AC8 a gap longer than 15 minutes counts in the energy, and its slots are null")
    void aGapCountsInTheEnergyAndItsSlotsAreNull() {
        PlantProductionInput plant = plant(0.0, List.of(
                        snapshot("00:00:01", 0.0), snapshot("00:05", 0.0), snapshot("00:10", 0.2),
                        snapshot("00:30", 1.2), snapshot("00:35", 1.4)),
                segment(TODAY_START, TOMORROW_START, "0.5"));

        SupplyProductionToday today = calculate(List.of(plant), at("00:36"));

        assertEquals(1.4 * 0.5, today.getAssignedEnergyKWh(), TOLERANCE);
        assertEquals(0.0, powerAt(today, "00:05"), TOLERANCE);
        assertEquals(0.2 * 0.5 / SLOT_HOURS, powerAt(today, "00:10"), TOLERANCE);
        for (String slotEnd : List.of("00:15", "00:20", "00:25", "00:30")) {
            assertNull(powerAt(today, slotEnd), slotEnd);
        }
        assertEquals(0.2 * 0.5 / SLOT_HOURS, powerAt(today, "00:35"), TOLERANCE);
    }

    @Test
    @DisplayName("PIP-009 AC8 an interval of exactly 15 minutes is split over its slots, not a gap")
    void anIntervalOfExactlyFifteenMinutesIsSplitOverItsSlots() {
        PlantProductionInput plant = plant(0.0, List.of(snapshot("00:00", 0.0), snapshot("00:15", 0.3)),
                segment(TODAY_START, TOMORROW_START, "1"));

        SupplyProductionToday today = calculate(List.of(plant), at("00:16"));

        assertEquals(List.of("00:05", "00:10", "00:15"), slotEnds(today));
        for (String slotEnd : List.of("00:05", "00:10", "00:15")) {
            assertEquals(0.1 / SLOT_HOURS, powerAt(today, slotEnd), TOLERANCE, slotEnd);
        }
    }

    @Test
    @DisplayName("PIP-009 AC8 an interval's energy is split over the slots it overlaps, in proportion to the time")
    void anIntervalsEnergyIsSplitInProportionToTheTimeOverlapped() {
        PlantProductionInput plant = plant(0.0, List.of(
                        snapshot("00:00", 0.0), snapshot("00:07", 0.7), snapshot("00:10", 1.0)),
                segment(TODAY_START, TOMORROW_START, "0.5"));

        SupplyProductionToday today = calculate(List.of(plant), at("00:11"));

        // 00:00-00:05: 5/7 of 0.7. 00:05-00:10: 2/7 of 0.7 plus all of 0.3.
        assertEquals(0.5 * 0.5 / SLOT_HOURS, powerAt(today, "00:05"), TOLERANCE);
        assertEquals((0.2 + 0.3) * 0.5 / SLOT_HOURS, powerAt(today, "00:10"), TOLERANCE);
    }

    @Test
    @DisplayName("PIP-002 PIP-008 the carry-over at midnight assigns nothing and draws no negative slot")
    void theCarryOverAtMidnightAssignsNothing() {
        double yesterdayTotal = 323.36;
        PlantProductionInput plant = plant(yesterdayTotal, List.of(
                        snapshot("00:00:00.980", yesterdayTotal), snapshot("00:05", 0.0), snapshot("00:10", 0.02)),
                segment(TODAY_START, TOMORROW_START, "0.5"));

        SupplyProductionToday today = calculate(List.of(plant), at("00:11"));

        assertEquals(0.02 * 0.5, today.getAssignedEnergyKWh(), TOLERANCE);
        assertEquals(0.0, powerAt(today, "00:05"), TOLERANCE);
        assertEquals(0.02 * 0.5 / SLOT_HOURS, powerAt(today, "00:10"), TOLERANCE);
    }

    @Test
    @DisplayName("PIP-009 AC8 AC10 the curve stops at the last slot ending by the latest snapshot, which gives the current values")
    void theCurveStopsAtTheLatestSnapshot() {
        PlantProductionInput plant = plant(0.0, List.of(
                        snapshot("00:00", 0.0), snapshot("00:05", 0.1), snapshot("00:10", 0.3),
                        snapshot("00:12", 0.4)),
                segment(TODAY_START, TOMORROW_START, "0.5"));

        SupplyProductionToday today = calculate(List.of(plant), at("00:13"));

        assertEquals(List.of("00:05", "00:10"), slotEnds(today));
        assertEquals(0.2 * 0.5 / SLOT_HOURS, today.getCurrentAssignedPowerKW(), TOLERANCE);
        assertEquals(0.2 / SLOT_HOURS, today.getCurrentPlantPowerKW(), TOLERANCE);
        assertEquals(at("00:12"), today.getLastUpdatedAt());
        // The energy counts every interval, the one after the last slot included.
        assertEquals(0.4 * 0.5, today.getAssignedEnergyKWh(), TOLERANCE);
    }

    @Test
    @DisplayName("PIP-009 AC10 when the last slot is in a gap, the current values are null")
    void whenTheLastSlotIsInAGapTheCurrentValuesAreNull() {
        PlantProductionInput plant = plant(0.0, List.of(
                        snapshot("00:00", 0.0), snapshot("00:05", 0.1), snapshot("00:30", 0.9)),
                segment(TODAY_START, TOMORROW_START, "0.5"));

        SupplyProductionToday today = calculate(List.of(plant), at("00:31"));

        assertNull(today.getAssignedPowerCurve().get(today.getAssignedPowerCurve().size() - 1).powerKW());
        assertNull(today.getCurrentAssignedPowerKW());
        assertNull(today.getCurrentPlantPowerKW());
        assertEquals(0.9 * 0.5, today.getAssignedEnergyKWh(), TOLERANCE);
    }

    @Test
    @DisplayName("PIP-010 AC9 AC10 two plants are summed, plant power too, and the partition coefficient is null")
    void twoPlantsAreSummed() {
        PlantProductionInput north = plant(0.0, List.of(
                        snapshot("00:00", 0.0), snapshot("00:05", 0.6), snapshot("00:10", 1.0)),
                segment(TODAY_START, TOMORROW_START, "0.1"));
        PlantProductionInput south = plant(0.0, List.of(
                        snapshot("00:00", 0.0), snapshot("00:05", 0.3), snapshot("00:10", 1.0)),
                segment(TODAY_START, TOMORROW_START, "0.2"));

        SupplyProductionToday today = calculate(List.of(north, south), at("00:11"));

        assertEquals(1.0 * 0.1 + 1.0 * 0.2, today.getAssignedEnergyKWh(), TOLERANCE);
        assertEquals((0.6 * 0.1 + 0.3 * 0.2) / SLOT_HOURS, powerAt(today, "00:05"), TOLERANCE);
        assertEquals((0.4 * 0.1 + 0.7 * 0.2) / SLOT_HOURS, today.getCurrentAssignedPowerKW(), TOLERANCE);
        assertEquals((0.4 + 0.7) / SLOT_HOURS, today.getCurrentPlantPowerKW(), TOLERANCE);
        assertNull(today.getPartitionCoefficient());
    }

    @Test
    @DisplayName("PIP-010 AC9 a slot is null when one of the plants has a gap or no value in it")
    void aSlotIsNullWhenOneOfThePlantsHasNoValueInIt() {
        PlantProductionInput north = plant(0.0, List.of(
                        snapshot("00:00", 0.0), snapshot("00:05", 0.1), snapshot("00:10", 0.2),
                        snapshot("00:15", 0.3), snapshot("00:20", 0.4), snapshot("00:25", 0.5),
                        snapshot("00:30", 0.6)),
                segment(TODAY_START, TOMORROW_START, "0.1"));
        // A 20-minute gap from 00:00, then a feed that stops at 00:27, before the north plant's.
        PlantProductionInput south = plant(0.0, List.of(
                        snapshot("00:00", 0.0), snapshot("00:20", 0.4), snapshot("00:25", 0.5),
                        snapshot("00:27", 0.6)),
                segment(TODAY_START, TOMORROW_START, "0.2"));

        SupplyProductionToday today = calculate(List.of(north, south), at("00:31"));

        for (String slotEnd : List.of("00:05", "00:10", "00:15", "00:20", "00:30")) {
            assertNull(powerAt(today, slotEnd), slotEnd);
        }
        assertEquals((0.1 * 0.1 + 0.1 * 0.2) / SLOT_HOURS, powerAt(today, "00:25"), TOLERANCE);
        assertEquals(at("00:30"), today.getLastUpdatedAt());
        assertEquals(0.6 * 0.1 + 0.6 * 0.2, today.getAssignedEnergyKWh(), TOLERANCE);
    }

    @Test
    @DisplayName("PIP-010 AC9 with a single plant, the partition coefficient is the β in force now, 0 when none is")
    void withASinglePlantThePartitionCoefficientIsTheOneInForceNow() {
        List<InverterSnapshot> snapshots = List.of(snapshot("09:00", 1.0), snapshot("09:05", 1.2));

        SupplyProductionToday inForce = calculate(List.of(plant(0.0, snapshots,
                segment(TODAY_START, at("09:30"), "0.1"),
                segment(at("09:30"), TOMORROW_START, "0.25"))), at("09:30"));
        SupplyProductionToday ended = calculate(List.of(plant(0.0, snapshots,
                segment(TODAY_START, at("09:20"), "0.1"))), at("09:30"));

        assertEquals(0.25, inForce.getPartitionCoefficient(), TOLERANCE);
        assertEquals(0.0, ended.getPartitionCoefficient(), TOLERANCE);
    }

    @Test
    @DisplayName("PIP-004 AC11 the latest snapshot across the plants is stale only when more than 15 minutes old")
    void theLatestSnapshotIsStaleOnlyPastFifteenMinutes() {
        List<PlantProductionInput> plants = List.of(
                plant(0.0, List.of(snapshot("09:00", 1.0), snapshot("09:05", 1.2)),
                        segment(TODAY_START, TOMORROW_START, "0.1")),
                plant(0.0, List.of(snapshot("09:00", 1.0), snapshot("09:10", 1.2)),
                        segment(TODAY_START, TOMORROW_START, "0.1")));

        SupplyProductionToday exactly = calculate(plants, at("09:25"));
        SupplyProductionToday over = calculate(plants, at("09:25").plusMillis(1));

        assertEquals(at("09:10"), exactly.getLastUpdatedAt());
        assertFalse(exactly.getStale());
        assertTrue(over.getStale());
    }

    @Test
    @DisplayName("PIP-006 PIP-009 on the day the clocks go back, slots stay on the local 5-minute grid")
    void onTheDayTheClocksGoBackSlotsStayOnTheLocalGrid() {
        LocalDate day = LocalDate.of(2026, 10, 25);
        Instant todayStart = day.atStartOfDay(ZONE).toInstant();
        Instant last = LocalDateTime.of(day, LocalTime.of(4, 0)).atZone(ZONE).toInstant();
        PlantProductionInput plant = new PlantProductionInput(InverterSnapshotIntervals.of(0.0, List.of(
                new InverterSnapshot(last.minus(Duration.ofMinutes(5)), 0.0),
                new InverterSnapshot(last, 0.0))).orElseThrow(),
                List.of(new CoefficientSegment(todayStart, day.plusDays(1).atStartOfDay(ZONE).toInstant(),
                        new BigDecimal("0.1"))));

        SupplyProductionToday today = AssignedProductionCalculator.calculate(List.of(plant), todayStart, last, ZONE);

        // 00:00 +02:00 to 04:00 +01:00 is 5 hours: 60 slots, each ending on a local multiple of 5 minutes.
        assertEquals(60, today.getAssignedPowerCurve().size());
        assertTrue(today.getAssignedPowerCurve().stream()
                .allMatch(point -> point.endsAt().atZone(ZONE).getMinute() % 5 == 0));
        assertEquals(ZONE, today.getZone());
        assertEquals(SupplyProductionTodayStatus.AVAILABLE, today.getStatus());
        assertNotNull(today.getStale());
    }

    private static SupplyProductionToday calculate(List<PlantProductionInput> plants, Instant now) {
        return AssignedProductionCalculator.calculate(plants, TODAY_START, now, ZONE);
    }

    private static PlantProductionInput plant(Double previousDayLastKWh, List<InverterSnapshot> snapshots,
                                              CoefficientSegment... segments) {
        return new PlantProductionInput(InverterSnapshotIntervals.of(previousDayLastKWh, snapshots).orElseThrow(),
                List.of(segments));
    }

    private static CoefficientSegment segment(Instant from, Instant to, String coefficient) {
        return new CoefficientSegment(from, to, new BigDecimal(coefficient));
    }

    private static InverterSnapshot snapshot(String localTime, double dayPowerKWh) {
        return new InverterSnapshot(at(localTime), dayPowerKWh);
    }

    private static Instant at(String localTime) {
        return LocalDateTime.of(DAY, LocalTime.parse(localTime)).atZone(ZONE).toInstant();
    }

    private static Double powerAt(SupplyProductionToday today, String slotEnd) {
        Instant end = at(slotEnd);
        return today.getAssignedPowerCurve().stream()
                .filter(point -> point.endsAt().equals(end))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no slot ends at " + slotEnd))
                .powerKW();
    }

    private static List<String> slotEnds(SupplyProductionToday today) {
        return today.getAssignedPowerCurve().stream()
                .map(point -> point.endsAt().atZone(ZONE).toLocalTime().toString())
                .toList();
    }
}

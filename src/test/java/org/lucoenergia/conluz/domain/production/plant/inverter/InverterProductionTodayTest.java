package org.lucoenergia.conluz.domain.production.plant.inverter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hand-built snapshots of 2026-09-15 in Madrid, the day the carry-over was observed.
 */
class InverterProductionTodayTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Madrid");
    private static final double TOLERANCE = 1e-9;
    private static final double YESTERDAY_TOTAL = 323.36;

    @Test
    @DisplayName("PIP-002 AC4 the boundary snapshot carrying yesterday's total is carry-over, and today counts from 0")
    void theBoundarySnapshotIsCarryOver() {
        List<InverterSnapshot> snapshots = new ArrayList<>();
        snapshots.add(snapshot("00:00:01", YESTERDAY_TOTAL));
        for (LocalTime time = LocalTime.of(0, 5); !time.isAfter(LocalTime.of(8, 0)); time = time.plusMinutes(5)) {
            snapshots.add(snapshot(time.toString(), 0));
        }
        snapshots.add(snapshot("08:05", 0.04));
        snapshots.add(snapshot("08:10", 0.08));

        InverterProductionToday today = InverterProductionToday.of(YESTERDAY_TOTAL, snapshots).orElseThrow();

        assertEquals(0.08, today.getEnergyKWh(), TOLERANCE);
        List<InverterPowerPoint> curve = today.getPowerCurve();
        assertEquals(snapshots.size() - 1, curve.size());
        curve.stream()
                .filter(point -> !point.time().isAfter(at("08:00")))
                .forEach(point -> assertEquals(0, point.powerKW(), TOLERANCE, () -> "above 0 kW at " + point));
        assertEquals(0.04 / (5 / 60.0), powerAt(curve, "08:05"), TOLERANCE);
        assertEquals(0.04 / (5 / 60.0), powerAt(curve, "08:10"), TOLERANCE);
        assertEquals(at("08:10"), today.getLastSnapshotAt());
    }

    @Test
    @DisplayName("PIP-002 PIP-003 the first value after the carry-over is counted from 0, never as a drop")
    void theFirstValueAfterTheCarryOverIsCountedFromZero() {
        InverterProductionToday today = InverterProductionToday.of(YESTERDAY_TOTAL, List.of(
                snapshot("00:00:00.980", YESTERDAY_TOTAL),
                snapshot("00:05", 0.02),
                snapshot("00:10", 0.05))).orElseThrow();

        assertEquals(0.05, today.getEnergyKWh(), TOLERANCE);
        Duration firstInterval = Duration.between(at("00:00:00.980"), at("00:05"));
        assertEquals(0.02 / (firstInterval.toMillis() / 3_600_000.0), today.getPowerCurve().get(0).powerKW(),
                TOLERANCE);
        assertEquals(0.03 / (5 / 60.0), today.getPowerCurve().get(1).powerKW(), TOLERANCE);
    }

    @Test
    @DisplayName("PIP-002 AC5 when every snapshot of today carries yesterday's total, today's energy is 0 and the curve flat")
    void everySnapshotCarryingYesterdaysTotalIsZero() {
        InverterProductionToday today = InverterProductionToday.of(YESTERDAY_TOTAL, List.of(
                snapshot("00:00:01", YESTERDAY_TOTAL),
                snapshot("00:05", YESTERDAY_TOTAL),
                snapshot("00:10", YESTERDAY_TOTAL))).orElseThrow();

        assertEquals(0, today.getEnergyKWh(), TOLERANCE);
        assertEquals(List.of(new InverterPowerPoint(at("00:05"), 0.0), new InverterPowerPoint(at("00:10"), 0.0)),
                today.getPowerCurve());
    }

    @Test
    @DisplayName("PIP-002 the carry-over run ends at the first different value: a later equal value is today's")
    void theRunEndsAtTheFirstDifferentValue() {
        InverterProductionToday today = InverterProductionToday.of(5.0, List.of(
                snapshot("00:00:01", 5.0),
                snapshot("00:05", 0),
                snapshot("09:00", 4.5),
                snapshot("09:05", 5.0))).orElseThrow();

        assertEquals(5.0, today.getEnergyKWh(), TOLERANCE);
        assertEquals(0.5 / (5 / 60.0), powerAt(today.getPowerCurve(), "09:05"), TOLERANCE);
    }

    @Test
    @DisplayName("PIP-002 AC6 with yesterday's last value 0 there is no carry-over: today's energy is the latest value")
    void withYesterdaysLastValueZeroThereIsNoCarryOver() {
        InverterProductionToday today = InverterProductionToday.of(0.0, List.of(
                snapshot("00:00:01", 0),
                snapshot("08:00", 0.5),
                snapshot("08:05", 1.2))).orElseThrow();

        assertEquals(1.2, today.getEnergyKWh(), TOLERANCE);
        assertEquals(0.7 / (5 / 60.0), powerAt(today.getPowerCurve(), "08:05"), TOLERANCE);
    }

    @Test
    @DisplayName("PIP-002 AC6b without a snapshot from yesterday there is no carry-over: today's energy is the latest value")
    void withoutASnapshotFromYesterdayThereIsNoCarryOver() {
        InverterProductionToday today = InverterProductionToday.of(null, List.of(
                snapshot("08:00", 0.3),
                snapshot("08:05", 0.5),
                snapshot("08:10", 0.9))).orElseThrow();

        assertEquals(0.9, today.getEnergyKWh(), TOLERANCE);
        assertEquals(0.2 / (5 / 60.0), powerAt(today.getPowerCurve(), "08:05"), TOLERANCE);
    }

    @Test
    @DisplayName("PIP-002 PIP-003 AC9 a decrease within today yields no negative point")
    void aDecreaseYieldsNoNegativePoint() {
        InverterProductionToday today = InverterProductionToday.of(null, List.of(
                snapshot("10:00", 1.0),
                snapshot("10:05", 0.8),
                snapshot("10:10", 1.1))).orElseThrow();

        assertEquals(0, powerAt(today.getPowerCurve(), "10:05"), TOLERANCE);
        assertEquals(0.3 / (5 / 60.0), powerAt(today.getPowerCurve(), "10:10"), TOLERANCE);
        assertTrue(today.getEnergyKWh() >= 0);
    }

    @Test
    @DisplayName("PIP-003 AC7 the point is averaged over the real 301 s between the snapshots, not a fixed 5 minutes")
    void thePointUsesTheRealTimestamps() {
        InverterProductionToday today = InverterProductionToday.of(null, List.of(
                snapshot("12:00", 10.0),
                snapshot("12:05:01", 10.1),
                snapshot("12:10:01", 10.2))).orElseThrow();

        Double power = today.getPowerCurve().get(0).powerKW();
        assertEquals(0.1 / (301 / 3600.0), power, TOLERANCE);
        assertTrue(Math.abs(power - 0.1 / (300 / 3600.0)) > 1e-3, "averaged over a fixed 5 minutes: " + power);
        assertEquals(at("12:05:01"), today.getPowerCurve().get(0).time());
    }

    @Test
    @DisplayName("PIP-003 AC8 an interval of 13 min 20 s is averaged, one of 20 minutes is a null point")
    void aLongIntervalIsAGap() {
        InverterProductionToday today = InverterProductionToday.of(null, List.of(
                snapshot("06:41:41", 2.0),
                snapshot("06:55:01", 2.4),
                snapshot("07:15:01", 3.4),
                snapshot("07:20:01", 3.5))).orElseThrow();

        List<InverterPowerPoint> curve = today.getPowerCurve();
        assertEquals(0.4 / (800 / 3600.0), powerAt(curve, "06:55:01"), TOLERANCE);
        assertEquals(new InverterPowerPoint(at("07:15:01"), null), curve.get(1));
        assertEquals(0.1 / (5 / 60.0), powerAt(curve, "07:20:01"), TOLERANCE);
        assertEquals(3.5, today.getEnergyKWh(), TOLERANCE);
    }

    @Test
    @DisplayName("PIP-003 an interval of exactly 15 minutes is still averaged, one millisecond more is a gap")
    void fifteenMinutesIsTheLongestAveragedInterval() {
        InverterProductionToday today = InverterProductionToday.of(null, List.of(
                snapshot("09:00", 1.0),
                snapshot("09:15", 1.5),
                snapshot("09:30:00.001", 2.0))).orElseThrow();

        assertNotNull(today.getPowerCurve().get(0).powerKW());
        assertEquals(0.5 / 0.25, today.getPowerCurve().get(0).powerKW(), TOLERANCE);
        assertNull(today.getPowerCurve().get(1).powerKW());
    }

    @Test
    @DisplayName("PIP-001 without a snapshot today there is no production")
    void withoutASnapshotTodayThereIsNoProduction() {
        assertTrue(InverterProductionToday.of(YESTERDAY_TOTAL, List.of()).isEmpty());
    }

    @Test
    @DisplayName("PIP-003 a single snapshot gives an empty curve and its own value as today's energy")
    void aSingleSnapshotGivesAnEmptyCurve() {
        InverterProductionToday today = InverterProductionToday.of(null, List.of(snapshot("07:00", 0.2)))
                .orElseThrow();

        assertEquals(0.2, today.getEnergyKWh(), TOLERANCE);
        assertTrue(today.getPowerCurve().isEmpty());
        assertEquals(at("07:00"), today.getLastSnapshotAt());
    }

    private static InverterSnapshot snapshot(String localTime, double dayPowerKWh) {
        return new InverterSnapshot(at(localTime), dayPowerKWh);
    }

    private static Instant at(String localTime) {
        return LocalDateTime.of(2026, 9, 15, 0, 0).with(LocalTime.parse(localTime)).atZone(ZONE).toInstant();
    }

    private static double powerAt(List<InverterPowerPoint> curve, String localTime) {
        Instant time = at(localTime);
        return curve.stream()
                .filter(point -> point.time().equals(time))
                .findFirst()
                .map(InverterPowerPoint::powerKW)
                .orElseThrow(() -> new AssertionError("No point at " + localTime + " in " + curve));
    }
}

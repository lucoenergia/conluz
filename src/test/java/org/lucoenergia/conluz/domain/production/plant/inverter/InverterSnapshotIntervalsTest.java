package org.lucoenergia.conluz.domain.production.plant.inverter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hand-built snapshots of 2026-09-15 in Madrid, the day the carry-over was observed.
 */
class InverterSnapshotIntervalsTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Madrid");
    private static final double TOLERANCE = 1e-9;
    private static final double YESTERDAY_TOTAL = 323.36;

    @Test
    @DisplayName("PIP-002 the carry-over run counts as 0, at the first snapshot and in the intervals")
    void theCarryOverRunCountsAsZero() {
        InverterSnapshotIntervals snapshots = InverterSnapshotIntervals.of(YESTERDAY_TOTAL, List.of(
                snapshot("00:00:00.980", YESTERDAY_TOTAL),
                snapshot("00:05", YESTERDAY_TOTAL),
                snapshot("00:10", 0.02),
                snapshot("00:15", 0.05))).orElseThrow();

        assertEquals(0, snapshots.getFirstEffectiveKWh(), TOLERANCE);
        assertEquals(0.05, snapshots.getLastEffectiveKWh(), TOLERANCE);
        assertEquals(List.of(0.0, 0.02, 0.03), energies(snapshots), () -> "energies " + energies(snapshots));
        assertEquals(at("00:00:00.980"), snapshots.getFirstSnapshotAt());
        assertEquals(at("00:15"), snapshots.getLastSnapshotAt());
    }

    @Test
    @DisplayName("PIP-002 without carry-over the first snapshot counts its own value, and a decrease counts as nothing")
    void withoutCarryOverTheFirstSnapshotCountsItsValue() {
        InverterSnapshotIntervals snapshots = InverterSnapshotIntervals.of(0.0, List.of(
                snapshot("10:00", 5.0),
                snapshot("10:05", 5.4),
                snapshot("10:10", 5.1),
                snapshot("10:15", 5.3))).orElseThrow();

        assertEquals(5.0, snapshots.getFirstEffectiveKWh(), TOLERANCE);
        assertEquals(5.3, snapshots.getLastEffectiveKWh(), TOLERANCE);
        List<Double> energies = energies(snapshots);
        assertEquals(3, energies.size());
        assertEquals(0.4, energies.get(0), TOLERANCE);
        assertEquals(0.0, energies.get(1), TOLERANCE);
        assertEquals(0.2, energies.get(2), TOLERANCE);
    }

    @Test
    @DisplayName("PIP-003 an interval of exactly 15 minutes is not a gap, one millisecond more is")
    void anIntervalOfExactlyFifteenMinutesIsNotAGap() {
        InverterSnapshotIntervals snapshots = InverterSnapshotIntervals.of(null, List.of(
                snapshot("10:00", 1.0),
                snapshot("10:15", 1.2),
                snapshot("10:30:00.001", 1.5))).orElseThrow();

        List<InverterInterval> intervals = snapshots.getIntervals();
        assertEquals(Duration.ofMinutes(15), intervals.get(0).duration());
        assertFalse(intervals.get(0).isGap());
        assertTrue(intervals.get(1).isGap());
        assertEquals(0.3, intervals.get(1).energyKWh(), TOLERANCE, "a gap still carries its energy");
    }

    @Test
    @DisplayName("PIP-001 without a snapshot today there are no intervals, and a single snapshot has none either")
    void withoutSnapshotsThereIsNothing() {
        assertTrue(InverterSnapshotIntervals.of(1.0, List.of()).isEmpty());
        assertTrue(InverterSnapshotIntervals.of(null, List.of(snapshot("10:00", 1.0))).orElseThrow()
                .getIntervals().isEmpty());
    }

    private static List<Double> energies(InverterSnapshotIntervals snapshots) {
        return snapshots.getIntervals().stream()
                .map(interval -> Math.round(interval.energyKWh() * 1e9) / 1e9)
                .toList();
    }

    private static InverterSnapshot snapshot(String localTime, double dayPowerKWh) {
        return new InverterSnapshot(at(localTime), dayPowerKWh);
    }

    private static Instant at(String localTime) {
        return LocalDateTime.of(2026, 9, 15, 0, 0).with(LocalTime.parse(localTime)).atZone(ZONE).toInstant();
    }
}

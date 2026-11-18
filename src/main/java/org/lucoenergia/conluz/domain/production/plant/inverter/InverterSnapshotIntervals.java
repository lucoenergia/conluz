package org.lucoenergia.conluz.domain.production.plant.inverter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The snapshots of a plant's inverter of the current local day, read as the energy each one counts for today and the
 * intervals between consecutive ones. Every figure is unrounded.
 *
 * <h2>Carry-over</h2>
 *
 * <p>The inverter's {@code day_power} resets at local midnight, but a snapshot taken at the boundary can still carry
 * the previous day's total. The leading run of today's snapshots whose {@code day_power} equals the previous day's
 * last one, when that is greater than 0, is carry-over: it counts as 0 kWh. The run ends at the first snapshot whose
 * value differs. Without a previous-day snapshot, or with a previous-day last value of 0, there is no carry-over.
 *
 * <h2>Intervals</h2>
 *
 * <p>One interval per pair of consecutive snapshots, carrying the energy counted between them: carry-over counts as
 * 0, and a decrease as nothing.
 */
public final class InverterSnapshotIntervals {

    private final double firstEffectiveKWh;
    private final double lastEffectiveKWh;
    private final Instant firstSnapshotAt;
    private final Instant lastSnapshotAt;
    private final List<InverterInterval> intervals;

    private InverterSnapshotIntervals(double firstEffectiveKWh, double lastEffectiveKWh, Instant firstSnapshotAt,
                                      Instant lastSnapshotAt, List<InverterInterval> intervals) {
        this.firstEffectiveKWh = firstEffectiveKWh;
        this.lastEffectiveKWh = lastEffectiveKWh;
        this.firstSnapshotAt = firstSnapshotAt;
        this.lastSnapshotAt = lastSnapshotAt;
        this.intervals = intervals;
    }

    /**
     * @param previousDayLastKWh the {@code day_power} of the last snapshot of the previous local day, or null when
     *                           there is none
     * @param todaySnapshots     the snapshots of the current local day, in time order
     * @return the snapshots read as energy and intervals, or empty when there is no snapshot today
     */
    public static Optional<InverterSnapshotIntervals> of(Double previousDayLastKWh,
                                                         List<InverterSnapshot> todaySnapshots) {
        if (todaySnapshots.isEmpty()) {
            return Optional.empty();
        }
        double[] effectiveKWh = effectiveEnergy(previousDayLastKWh, todaySnapshots);

        List<InverterInterval> intervals = new ArrayList<>();
        for (int i = 1; i < todaySnapshots.size(); i++) {
            intervals.add(new InverterInterval(todaySnapshots.get(i - 1).time(), todaySnapshots.get(i).time(),
                    Math.max(0, effectiveKWh[i] - effectiveKWh[i - 1])));
        }

        int last = todaySnapshots.size() - 1;
        return Optional.of(new InverterSnapshotIntervals(effectiveKWh[0], effectiveKWh[last],
                todaySnapshots.get(0).time(), todaySnapshots.get(last).time(), List.copyOf(intervals)));
    }

    /**
     * The energy each snapshot counts for today: 0 for a carry-over snapshot, its {@code day_power} otherwise.
     */
    private static double[] effectiveEnergy(Double previousDayLastKWh, List<InverterSnapshot> todaySnapshots) {
        double[] effectiveKWh = new double[todaySnapshots.size()];
        boolean carryOver = previousDayLastKWh != null && previousDayLastKWh > 0;
        for (int i = 0; i < todaySnapshots.size(); i++) {
            double dayPowerKWh = todaySnapshots.get(i).dayPowerKWh();
            carryOver = carryOver && Double.compare(dayPowerKWh, previousDayLastKWh) == 0;
            effectiveKWh[i] = carryOver ? 0 : dayPowerKWh;
        }
        return effectiveKWh;
    }

    /**
     * The energy the first snapshot of today counts: what the inverter counted from local midnight up to it, or 0
     * when it is carry-over.
     */
    public double getFirstEffectiveKWh() {
        return firstEffectiveKWh;
    }

    /**
     * The energy the latest snapshot of today counts: its {@code day_power}, or 0 when every snapshot is carry-over.
     */
    public double getLastEffectiveKWh() {
        return lastEffectiveKWh;
    }

    public Instant getFirstSnapshotAt() {
        return firstSnapshotAt;
    }

    public Instant getLastSnapshotAt() {
        return lastSnapshotAt;
    }

    /**
     * One interval per pair of consecutive snapshots, in time order: empty with a single snapshot.
     */
    public List<InverterInterval> getIntervals() {
        return intervals;
    }
}

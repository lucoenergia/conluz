package org.lucoenergia.conluz.domain.production.plant.inverter;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Today's production of a plant's inverter, derived from the snapshots of the current local day and the last
 * snapshot of the previous one, as {@link InverterSnapshotIntervals} reads them. Every figure is unrounded.
 *
 * <h2>Power curve</h2>
 *
 * <p>One point per interval between consecutive snapshots: the energy counted in it over its real duration, at the
 * later snapshot. A gap ({@link InverterInterval#isGap()}) has a null point: nothing is interpolated across missing
 * snapshots.
 */
public final class InverterProductionToday {

    private static final double MILLIS_PER_HOUR = Duration.ofHours(1).toMillis();

    private final double energyKWh;
    private final List<InverterPowerPoint> powerCurve;
    private final Instant lastSnapshotAt;

    private InverterProductionToday(double energyKWh, List<InverterPowerPoint> powerCurve, Instant lastSnapshotAt) {
        this.energyKWh = energyKWh;
        this.powerCurve = powerCurve;
        this.lastSnapshotAt = lastSnapshotAt;
    }

    /**
     * @param previousDayLastKWh the {@code day_power} of the last snapshot of the previous local day, or null when
     *                           there is none
     * @param todaySnapshots     the snapshots of the current local day, in time order
     * @return today's production, or empty when there is no snapshot today
     */
    public static Optional<InverterProductionToday> of(Double previousDayLastKWh,
                                                       List<InverterSnapshot> todaySnapshots) {
        return InverterSnapshotIntervals.of(previousDayLastKWh, todaySnapshots).map(InverterProductionToday::of);
    }

    private static InverterProductionToday of(InverterSnapshotIntervals snapshots) {
        List<InverterPowerPoint> powerCurve = snapshots.getIntervals().stream()
                .map(interval -> new InverterPowerPoint(interval.to(), interval.isGap()
                        ? null
                        : interval.energyKWh() / (interval.duration().toMillis() / MILLIS_PER_HOUR)))
                .toList();
        return new InverterProductionToday(snapshots.getLastEffectiveKWh(), powerCurve,
                snapshots.getLastSnapshotAt());
    }

    /**
     * Today's energy: the latest {@code day_power} after the carry-over, or 0 when every snapshot is carry-over.
     */
    public double getEnergyKWh() {
        return energyKWh;
    }

    /**
     * One point per pair of consecutive snapshots, in time order: empty with a single snapshot.
     */
    public List<InverterPowerPoint> getPowerCurve() {
        return powerCurve;
    }

    public Instant getLastSnapshotAt() {
        return lastSnapshotAt;
    }
}

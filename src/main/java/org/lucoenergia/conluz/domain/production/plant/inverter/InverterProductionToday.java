package org.lucoenergia.conluz.domain.production.plant.inverter;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Today's production of a plant's inverter, derived from the snapshots of the current local day and the last
 * snapshot of the previous one. Every figure is unrounded.
 *
 * <h2>Carry-over</h2>
 *
 * <p>The inverter's {@code day_power} resets at local midnight, but a snapshot taken at the boundary can still carry
 * the previous day's total. The leading run of today's snapshots whose {@code day_power} equals the previous day's
 * last one, when that is greater than 0, is carry-over: it counts as 0 kWh. The run ends at the first snapshot whose
 * value differs. Without a previous-day snapshot, or with a previous-day last value of 0, there is no carry-over.
 *
 * <h2>Power curve</h2>
 *
 * <p>One point per pair of consecutive snapshots: the energy counted between them, carry-over counting as 0 and a
 * decrease as nothing, over the real time between them, at the later snapshot. An interval longer than
 * {@link #MAX_INTERVAL} is a gap, whose point is null: nothing is interpolated across missing snapshots.
 */
public final class InverterProductionToday {

    /**
     * The longest interval between two snapshots that is still averaged. A single missing snapshot, about 10
     * minutes, is averaged; more is a gap.
     */
    public static final Duration MAX_INTERVAL = Duration.ofMinutes(15);

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
        if (todaySnapshots.isEmpty()) {
            return Optional.empty();
        }
        double[] effectiveKWh = effectiveEnergy(previousDayLastKWh, todaySnapshots);

        List<InverterPowerPoint> powerCurve = new ArrayList<>();
        for (int i = 1; i < todaySnapshots.size(); i++) {
            Instant from = todaySnapshots.get(i - 1).time();
            Instant to = todaySnapshots.get(i).time();
            Duration interval = Duration.between(from, to);
            Double powerKW = interval.compareTo(MAX_INTERVAL) > 0
                    ? null
                    : Math.max(0, effectiveKWh[i] - effectiveKWh[i - 1]) / (interval.toMillis() / MILLIS_PER_HOUR);
            powerCurve.add(new InverterPowerPoint(to, powerKW));
        }

        int last = todaySnapshots.size() - 1;
        return Optional.of(new InverterProductionToday(effectiveKWh[last], List.copyOf(powerCurve),
                todaySnapshots.get(last).time()));
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

package org.lucoenergia.conluz.domain.admin.supply.energytoday;

import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.CoefficientSegment;
import org.lucoenergia.conluz.domain.production.plant.inverter.InverterInterval;
import org.lucoenergia.conluz.domain.production.plant.inverter.InverterSnapshotIntervals;
import org.lucoenergia.conluz.domain.production.plant.inverter.InverterStaleness;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Derives the production of today assigned to a supply point from the inverter snapshots of the plants it has a
 * coefficient in. Pure: it reads nothing and rounds nothing.
 *
 * <h2>Intervals</h2>
 *
 * <p>Per plant, the intervals are the one from local midnight to the first snapshot of today, carrying the energy that
 * snapshot counts (0 when it is carry-over), then one per pair of consecutive snapshots
 * ({@link InverterSnapshotIntervals}). Each interval's energy is multiplied by the β of the supply in that plant in
 * force at the interval's start, 0 when none is. The assigned energy is the sum over every interval of every plant,
 * gaps included.
 *
 * <h2>Slots</h2>
 *
 * <p>The curve has one point per fixed 5-minute slot from local midnight, at the slot's end, for every slot ending at
 * or before the latest snapshot. Each interval's energy is split over the slots it overlaps in proportion to the time
 * overlapped. A plant has a value in a slot only when its intervals cover the whole slot and none of them is a gap;
 * the slot's value is the sum over plants, and null when any plant has none.
 */
public final class AssignedProductionCalculator {

    /**
     * The length of a slot of the assigned power curve.
     */
    public static final Duration SLOT = Duration.ofMinutes(5);

    private static final double SLOT_HOURS = SLOT.toMillis() / (double) Duration.ofHours(1).toMillis();

    private AssignedProductionCalculator() {
    }

    /**
     * @param plants     the plants used, each with at least one snapshot of today; never empty
     * @param todayStart local midnight of the current local day
     * @param now        the current instant
     * @param zone       the time zone of the supply's community
     */
    public static SupplyProductionToday calculate(List<PlantProductionInput> plants, Instant todayStart,
                                                  Instant now, ZoneId zone) {
        if (plants.isEmpty()) {
            throw new IllegalArgumentException("Today's assigned production needs at least one plant with data");
        }
        List<PlantIntervals> plantIntervals = plants.stream()
                .map(plant -> PlantIntervals.of(plant, todayStart))
                .toList();

        double assignedEnergyKWh = plantIntervals.stream()
                .flatMap(plant -> plant.intervals().stream())
                .mapToDouble(interval -> interval.interval().energyKWh() * interval.coefficient())
                .sum();

        Instant lastUpdatedAt = plants.stream()
                .map(plant -> plant.snapshots().getLastSnapshotAt())
                .max(Comparator.naturalOrder())
                .orElseThrow();

        List<AssignedPowerPoint> curve = new ArrayList<>();
        Double currentPlantPowerKW = null;
        for (Instant slotEnd = todayStart.plus(SLOT); !slotEnd.isAfter(lastUpdatedAt); slotEnd = slotEnd.plus(SLOT)) {
            SlotEnergy slot = slotEnergy(plantIntervals, slotEnd.minus(SLOT), slotEnd);
            curve.add(new AssignedPowerPoint(slotEnd, slot == null ? null : slot.assignedKWh() / SLOT_HOURS));
            currentPlantPowerKW = slot == null ? null : slot.plantKWh() / SLOT_HOURS;
        }
        Double currentAssignedPowerKW = curve.isEmpty() ? null : curve.get(curve.size() - 1).powerKW();

        Double partitionCoefficient = plants.size() == 1
                ? coefficientAt(plants.get(0).segments(), now)
                : null;

        return SupplyProductionToday.available(assignedEnergyKWh, curve, currentAssignedPowerKW, currentPlantPowerKW,
                partitionCoefficient, lastUpdatedAt, InverterStaleness.isStale(lastUpdatedAt, now), zone);
    }

    /**
     * The energy of the slot, assigned and of the plants, summed over plants, or null when any plant has no value in
     * it.
     */
    private static SlotEnergy slotEnergy(List<PlantIntervals> plants, Instant slotStart, Instant slotEnd) {
        double assignedKWh = 0;
        double plantKWh = 0;
        for (PlantIntervals plant : plants) {
            Duration covered = Duration.ZERO;
            for (WeightedInterval weighted : plant.intervals()) {
                InverterInterval interval = weighted.interval();
                Duration overlap = overlap(interval, slotStart, slotEnd);
                if (overlap.isZero()) {
                    continue;
                }
                if (interval.isGap()) {
                    return null;
                }
                double share = interval.energyKWh() * overlap.toMillis() / interval.duration().toMillis();
                plantKWh += share;
                assignedKWh += share * weighted.coefficient();
                covered = covered.plus(overlap);
            }
            if (covered.compareTo(SLOT) < 0) {
                return null;
            }
        }
        return new SlotEnergy(assignedKWh, plantKWh);
    }

    private static Duration overlap(InverterInterval interval, Instant slotStart, Instant slotEnd) {
        Instant from = interval.from().isAfter(slotStart) ? interval.from() : slotStart;
        Instant to = interval.to().isBefore(slotEnd) ? interval.to() : slotEnd;
        return from.isBefore(to) ? Duration.between(from, to) : Duration.ZERO;
    }

    /**
     * The β of the segment in force at {@code instant}, half-open, or 0 when none is.
     */
    private static double coefficientAt(List<CoefficientSegment> segments, Instant instant) {
        return segments.stream()
                .filter(segment -> !segment.from().isAfter(instant) && instant.isBefore(segment.to()))
                .findFirst()
                .map(segment -> segment.coefficient().doubleValue())
                .orElse(0.0);
    }

    private record WeightedInterval(InverterInterval interval, double coefficient) {
    }

    private record PlantIntervals(List<WeightedInterval> intervals) {

        static PlantIntervals of(PlantProductionInput plant, Instant todayStart) {
            InverterSnapshotIntervals snapshots = plant.snapshots();
            List<InverterInterval> intervals = new ArrayList<>();
            intervals.add(new InverterInterval(todayStart, snapshots.getFirstSnapshotAt(),
                    snapshots.getFirstEffectiveKWh()));
            intervals.addAll(snapshots.getIntervals());
            return new PlantIntervals(intervals.stream()
                    .map(interval -> new WeightedInterval(interval, coefficientAt(plant.segments(), interval.from())))
                    .toList());
        }
    }

    private record SlotEnergy(double assignedKWh, double plantKWh) {
    }
}

package org.lucoenergia.conluz.domain.admin.supply.energytoday;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

/**
 * Today's production assigned to a supply point: a status and, only when it is
 * {@link SupplyProductionTodayStatus#AVAILABLE}, the figures derived from the inverter snapshots of the plants it has
 * a coefficient in. Every other status leaves those null. Every figure is unrounded.
 */
public final class SupplyProductionToday {

    private final SupplyProductionTodayStatus status;
    private final Double assignedEnergyKWh;
    private final List<AssignedPowerPoint> assignedPowerCurve;
    private final Double currentAssignedPowerKW;
    private final Double currentPlantPowerKW;
    private final Double partitionCoefficient;
    private final Instant lastUpdatedAt;
    private final Boolean stale;
    private final ZoneId zone;

    private SupplyProductionToday(SupplyProductionTodayStatus status, Double assignedEnergyKWh,
                                  List<AssignedPowerPoint> assignedPowerCurve, Double currentAssignedPowerKW,
                                  Double currentPlantPowerKW, Double partitionCoefficient, Instant lastUpdatedAt,
                                  Boolean stale, ZoneId zone) {
        this.status = status;
        this.assignedEnergyKWh = assignedEnergyKWh;
        this.assignedPowerCurve = assignedPowerCurve;
        this.currentAssignedPowerKW = currentAssignedPowerKW;
        this.currentPlantPowerKW = currentPlantPowerKW;
        this.partitionCoefficient = partitionCoefficient;
        this.lastUpdatedAt = lastUpdatedAt;
        this.stale = stale;
        this.zone = zone;
    }

    public static SupplyProductionToday noCoefficientToday() {
        return withoutFigures(SupplyProductionTodayStatus.NO_COEFFICIENT_TODAY);
    }

    public static SupplyProductionToday notAvailable() {
        return withoutFigures(SupplyProductionTodayStatus.NOT_AVAILABLE);
    }

    public static SupplyProductionToday noDataToday() {
        return withoutFigures(SupplyProductionTodayStatus.NO_DATA_TODAY);
    }

    private static SupplyProductionToday withoutFigures(SupplyProductionTodayStatus status) {
        return new SupplyProductionToday(status, null, null, null, null, null, null, null, null);
    }

    /**
     * @param partitionCoefficient the β in force now, or null when the figures sum several plants
     * @param zone                 the time zone of the supply's community, which today is the local day of
     */
    static SupplyProductionToday available(double assignedEnergyKWh, List<AssignedPowerPoint> assignedPowerCurve,
                                           Double currentAssignedPowerKW, Double currentPlantPowerKW,
                                           Double partitionCoefficient, Instant lastUpdatedAt, boolean stale,
                                           ZoneId zone) {
        return new SupplyProductionToday(SupplyProductionTodayStatus.AVAILABLE, assignedEnergyKWh,
                List.copyOf(assignedPowerCurve), currentAssignedPowerKW, currentPlantPowerKW, partitionCoefficient,
                lastUpdatedAt, stale, zone);
    }

    public SupplyProductionTodayStatus getStatus() {
        return status;
    }

    /**
     * The energy assigned to the supply today, in kWh, curve gaps included, or null unless the status is available.
     */
    public Double getAssignedEnergyKWh() {
        return assignedEnergyKWh;
    }

    /**
     * One point per fixed 5-minute slot of today up to the latest snapshot, or null unless the status is available.
     */
    public List<AssignedPowerPoint> getAssignedPowerCurve() {
        return assignedPowerCurve;
    }

    /**
     * The assigned power of the last slot of the curve, null when that slot has none or the status is not available.
     */
    public Double getCurrentAssignedPowerKW() {
        return currentAssignedPowerKW;
    }

    /**
     * The power of the plants, without β, in the last slot of the curve, null when that slot has none or the status
     * is not available.
     */
    public Double getCurrentPlantPowerKW() {
        return currentPlantPowerKW;
    }

    /**
     * The β in force now in the single plant used, on the 0-1 scale, or null when several plants are summed or the
     * status is not available.
     */
    public Double getPartitionCoefficient() {
        return partitionCoefficient;
    }

    /**
     * The instant of the latest snapshot of today across the plants used, or null unless the status is available.
     */
    public Instant getLastUpdatedAt() {
        return lastUpdatedAt;
    }

    /**
     * Whether the latest snapshot is stale, or null unless the status is available.
     */
    public Boolean getStale() {
        return stale;
    }

    /**
     * The time zone of the supply's community, the one the instants are reported in, or null unless the status is
     * available.
     */
    public ZoneId getZone() {
        return zone;
    }
}

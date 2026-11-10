package org.lucoenergia.conluz.domain.production.plant.inverter;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

/**
 * Today's inverter production of a plant, as reported to its community's admins: a status, and, only when it is
 * {@link InverterProductionStatus#AVAILABLE}, today's energy, the power curve, the instant of the latest snapshot and
 * whether that snapshot is stale. Every other status leaves those null.
 */
public final class PlantInverterProductionToday {

    /**
     * The age past which the latest snapshot is stale. A snapshot exactly this old is not.
     */
    public static final Duration STALE_AFTER = Duration.ofMinutes(60);

    private final InverterProductionStatus status;
    private final InverterProductionToday production;
    private final Boolean stale;
    private final ZoneId zone;

    private PlantInverterProductionToday(InverterProductionStatus status, InverterProductionToday production,
                                         Boolean stale, ZoneId zone) {
        this.status = status;
        this.production = production;
        this.stale = stale;
        this.zone = zone;
    }

    public static PlantInverterProductionToday notAvailable() {
        return new PlantInverterProductionToday(InverterProductionStatus.NOT_AVAILABLE, null, null, null);
    }

    public static PlantInverterProductionToday noDataToday() {
        return new PlantInverterProductionToday(InverterProductionStatus.NO_DATA_TODAY, null, null, null);
    }

    /**
     * @param now  the current instant, which the age of the latest snapshot is measured against
     * @param zone the time zone of the plant's community, which today is the local day of
     */
    public static PlantInverterProductionToday available(InverterProductionToday production, Instant now,
                                                         ZoneId zone) {
        boolean stale = Duration.between(production.getLastSnapshotAt(), now).compareTo(STALE_AFTER) > 0;
        return new PlantInverterProductionToday(InverterProductionStatus.AVAILABLE, production, stale, zone);
    }

    public InverterProductionStatus getStatus() {
        return status;
    }

    /**
     * Today's energy in kWh, unrounded, or null unless the status is available.
     */
    public Double getTodayEnergyKWh() {
        return production == null ? null : production.getEnergyKWh();
    }

    /**
     * The power curve, or null unless the status is available.
     */
    public List<InverterPowerPoint> getPowerCurve() {
        return production == null ? null : production.getPowerCurve();
    }

    /**
     * The instant of the latest snapshot of today, or null unless the status is available.
     */
    public Instant getLastUpdatedAt() {
        return production == null ? null : production.getLastSnapshotAt();
    }

    /**
     * Whether the latest snapshot is older than {@link #STALE_AFTER}, or null unless the status is available.
     */
    public Boolean getStale() {
        return stale;
    }

    /**
     * The time zone of the plant's community, the one the instants are reported in, or null unless the status is
     * available.
     */
    public ZoneId getZone() {
        return zone;
    }
}

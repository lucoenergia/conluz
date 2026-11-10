package org.lucoenergia.conluz.infrastructure.production.plant.inverter;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.production.plant.inverter.InverterProductionStatus;
import org.lucoenergia.conluz.domain.production.plant.inverter.PlantInverterProductionToday;
import org.lucoenergia.conluz.infrastructure.shared.web.KWhRounding;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Today's inverter production of a plant. The energy and the power are rounded here to 3 decimals, once, from the
 * unrounded values.
 */
@Schema(requiredProperties = {"status", "todayEnergyKWh", "powerCurve", "lastUpdatedAt", "stale"})
public class PlantInverterProductionTodayResponse {

    @Schema(description = "Whether today's inverter production can be shown. AVAILABLE: the plant has an enabled " +
            "Huawei configuration and at least one inverter snapshot of the current local day. NO_DATA_TODAY: " +
            "the configuration is enabled, but no snapshot of the current local day is stored. NOT_AVAILABLE: the " +
            "plant has no Huawei configuration, or it is disabled. Every other field is null unless the status " +
            "is AVAILABLE.",
            example = "AVAILABLE")
    private final InverterProductionStatus status;
    @Schema(description = "Energy the plant's inverter produced today, from local midnight in the community's time " +
            "zone, in kWh, rounded to 3 decimals: the latest inverter day counter of today, a snapshot still " +
            "carrying the previous day's total counting as 0. This is gross inverter output, which may differ " +
            "from the distributor's figures and is never summed with the energy metrics. Null unless the status " +
            "is AVAILABLE.",
            example = "152.4", types = {"number", "null"})
    private final Double todayEnergyKWh;
    @Schema(description = "The inverter's average power between each pair of consecutive snapshots of today, in " +
            "time order. Empty with a single snapshot. Null unless the status is AVAILABLE.",
            types = {"array", "null"})
    private final List<PlantInverterPowerPointResponse> powerCurve;
    @Schema(description = "Instant of the latest inverter snapshot of today, with the offset in force in the " +
            "community's time zone at that instant. Null unless the status is AVAILABLE.",
            example = "2026-09-15T13:05:00+02:00", format = "date-time", types = {"string", "null"})
    private final OffsetDateTime lastUpdatedAt;
    @Schema(description = "True when the latest inverter snapshot of today is more than 60 minutes old; one exactly " +
            "60 minutes old is not stale. Null unless the status is AVAILABLE.",
            example = "false", types = {"boolean", "null"})
    private final Boolean stale;

    public PlantInverterProductionTodayResponse(PlantInverterProductionToday today) {
        this.status = today.getStatus();
        this.todayEnergyKWh = round(today.getTodayEnergyKWh());
        this.powerCurve = today.getPowerCurve() == null
                ? null
                : today.getPowerCurve().stream()
                .map(point -> new PlantInverterPowerPointResponse(point, today.getZone()))
                .toList();
        this.lastUpdatedAt = today.getLastUpdatedAt() == null
                ? null
                : today.getLastUpdatedAt().atZone(today.getZone()).toOffsetDateTime();
        this.stale = today.getStale();
    }

    static Double round(Double value) {
        return value == null ? null : KWhRounding.round(value);
    }

    public InverterProductionStatus getStatus() {
        return status;
    }

    public Double getTodayEnergyKWh() {
        return todayEnergyKWh;
    }

    public List<PlantInverterPowerPointResponse> getPowerCurve() {
        return powerCurve;
    }

    public OffsetDateTime getLastUpdatedAt() {
        return lastUpdatedAt;
    }

    public Boolean getStale() {
        return stale;
    }
}

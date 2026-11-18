package org.lucoenergia.conluz.infrastructure.admin.supply.energytoday;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.admin.supply.energytoday.SupplyProductionToday;
import org.lucoenergia.conluz.domain.admin.supply.energytoday.SupplyProductionTodayStatus;
import org.lucoenergia.conluz.infrastructure.shared.web.KWhRounding;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Today's production assigned to a supply point. The energy and the power are rounded here to 3 decimals, once, from
 * the unrounded values.
 */
@Schema(requiredProperties = {"status", "assignedPowerCurve", "assignedEnergyKWh", "currentAssignedPowerKW",
        "currentPlantPowerKW", "partitionCoefficient", "lastUpdatedAt", "stale"})
public class SupplyProductionTodayResponse {

    @Schema(description = "Whether today's assigned production can be shown, the first that applies of: " +
            "NO_COEFFICIENT_TODAY: the supply has no partition coefficient in force at any instant of today so far, " +
            "in any plant. NOT_AVAILABLE: none of the plants where it has one has an enabled Huawei configuration. " +
            "NO_DATA_TODAY: at least one of them has an enabled configuration, but none has an inverter snapshot of " +
            "today. AVAILABLE otherwise. Every other field is null unless the status is AVAILABLE.",
            example = "AVAILABLE")
    private final SupplyProductionTodayStatus status;
    @Schema(description = "The power assigned to the supply point in each fixed 5-minute slot of today, from local " +
            "midnight, in time order, up to the last slot that ends at or before lastUpdatedAt. Null unless the " +
            "status is AVAILABLE.",
            types = {"array", "null"})
    private final List<SupplyAssignedPowerPointResponse> assignedPowerCurve;
    @Schema(description = "Energy assigned to the supply point today, from local midnight in the community's time " +
            "zone, in kWh, rounded to 3 decimals: the energy the inverter counted in each interval between " +
            "snapshots multiplied by the supply's partition coefficient in force at the interval's start, summed. " +
            "Intervals longer than 15 minutes, the gaps of assignedPowerCurve, are included. This is derived from " +
            "gross inverter output, which may differ from the distributor's figures. Null unless the status is " +
            "AVAILABLE.",
            example = "12.345", types = {"number", "null"})
    private final Double assignedEnergyKWh;
    @Schema(description = "The power of the last point of assignedPowerCurve, in kW, rounded to 3 decimals; null " +
            "when that point is null or the curve is empty. Null unless the status is AVAILABLE.",
            example = "0.48", types = {"number", "null"})
    private final Double currentAssignedPowerKW;
    @Schema(description = "The power the plants produced in the same slot as currentAssignedPowerKW, before " +
            "applying any partition coefficient, in kW, rounded to 3 decimals, summed over the plants used; null " +
            "when that slot has no value or the curve is empty. Null unless the status is AVAILABLE.",
            example = "12.5", types = {"number", "null"})
    private final Double currentPlantPowerKW;
    @Schema(description = "With a single plant, the supply's partition coefficient in force now in that plant, on " +
            "the 0-1 scale, 0 when none is in force now. With several plants, null, because the figures are " +
            "summed over them. Null unless the status is AVAILABLE.",
            example = "0.0425", types = {"number", "null"})
    private final Double partitionCoefficient;
    @Schema(description = "Instant of the latest inverter snapshot of today across the plants used, with the offset " +
            "in force in the community's time zone at that instant. Null unless the status is AVAILABLE.",
            example = "2026-09-15T13:05:00+02:00", format = "date-time", types = {"string", "null"})
    private final OffsetDateTime lastUpdatedAt;
    @Schema(description = "True when lastUpdatedAt is more than 15 minutes old; one exactly 15 minutes old is not " +
            "stale. Null unless the status is AVAILABLE.",
            example = "false", types = {"boolean", "null"})
    private final Boolean stale;

    public SupplyProductionTodayResponse(SupplyProductionToday today) {
        this.status = today.getStatus();
        this.assignedPowerCurve = today.getAssignedPowerCurve() == null
                ? null
                : today.getAssignedPowerCurve().stream()
                .map(point -> new SupplyAssignedPowerPointResponse(point, today.getZone()))
                .toList();
        this.assignedEnergyKWh = round(today.getAssignedEnergyKWh());
        this.currentAssignedPowerKW = round(today.getCurrentAssignedPowerKW());
        this.currentPlantPowerKW = round(today.getCurrentPlantPowerKW());
        this.partitionCoefficient = today.getPartitionCoefficient();
        this.lastUpdatedAt = today.getLastUpdatedAt() == null
                ? null
                : today.getLastUpdatedAt().atZone(today.getZone()).toOffsetDateTime();
        this.stale = today.getStale();
    }

    static Double round(Double value) {
        return value == null ? null : KWhRounding.round(value);
    }

    public SupplyProductionTodayStatus getStatus() {
        return status;
    }

    public List<SupplyAssignedPowerPointResponse> getAssignedPowerCurve() {
        return assignedPowerCurve;
    }

    public Double getAssignedEnergyKWh() {
        return assignedEnergyKWh;
    }

    public Double getCurrentAssignedPowerKW() {
        return currentAssignedPowerKW;
    }

    public Double getCurrentPlantPowerKW() {
        return currentPlantPowerKW;
    }

    public Double getPartitionCoefficient() {
        return partitionCoefficient;
    }

    public OffsetDateTime getLastUpdatedAt() {
        return lastUpdatedAt;
    }

    public Boolean getStale() {
        return stale;
    }
}

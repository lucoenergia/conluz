package org.lucoenergia.conluz.infrastructure.admin.supply.energytoday;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.admin.supply.energytoday.AssignedPowerPoint;

import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * One 5-minute slot of the power assigned to a supply point. The power is rounded once, to 3 decimals, on the
 * unrounded value.
 */
@Schema(requiredProperties = {"endsAt", "powerKW"})
public class SupplyAssignedPowerPointResponse {

    @Schema(description = "Instant the 5-minute slot ends, with the offset in force in the community's time zone at " +
            "that instant. Slots are fixed: they start at local midnight and every 5 minutes after it.",
            example = "2026-09-15T08:05:00+02:00", format = "date-time")
    private final OffsetDateTime endsAt;
    @Schema(description = "Average power assigned to the supply point over the slot, in kW, rounded to 3 decimals: " +
            "the assigned energy of the slot divided by its 5 minutes. Never negative. Null when the slot is " +
            "overlapped by an interval between snapshots longer than 15 minutes, a gap in the snapshots, or is not " +
            "fully covered by the snapshots of every plant summed.",
            example = "0.48", types = {"number", "null"})
    private final Double powerKW;

    public SupplyAssignedPowerPointResponse(AssignedPowerPoint point, ZoneId zone) {
        this.endsAt = point.endsAt().atZone(zone).toOffsetDateTime();
        this.powerKW = SupplyProductionTodayResponse.round(point.powerKW());
    }

    public OffsetDateTime getEndsAt() {
        return endsAt;
    }

    public Double getPowerKW() {
        return powerKW;
    }
}

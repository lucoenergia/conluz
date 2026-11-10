package org.lucoenergia.conluz.infrastructure.production.plant.inverter;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.production.plant.inverter.InverterPowerPoint;

import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * One point of the inverter's power curve. The power is rounded once, to 3 decimals, on the unrounded average.
 */
@Schema(requiredProperties = {"endsAt", "powerKW"})
public class PlantInverterPowerPointResponse {

    @Schema(description = "Instant of the later of the two consecutive inverter snapshots the point is averaged " +
            "between, where its interval ends, with the offset in force in the community's time zone at that " +
            "instant.",
            example = "2026-09-15T08:05:00+02:00", format = "date-time")
    private final OffsetDateTime endsAt;
    @Schema(description = "Average power of the inverter over the interval between the two snapshots, in kW, " +
            "rounded to 3 decimals: the energy the inverter counted in the interval divided by the interval's " +
            "real duration. Never negative. Null when the interval is longer than 15 minutes: a gap in the " +
            "snapshots, never drawn as production.",
            example = "0.48", types = {"number", "null"})
    private final Double powerKW;

    public PlantInverterPowerPointResponse(InverterPowerPoint point, ZoneId zone) {
        this.endsAt = point.time().atZone(zone).toOffsetDateTime();
        this.powerKW = PlantInverterProductionTodayResponse.round(point.powerKW());
    }

    public OffsetDateTime getEndsAt() {
        return endsAt;
    }

    public Double getPowerKW() {
        return powerKW;
    }
}

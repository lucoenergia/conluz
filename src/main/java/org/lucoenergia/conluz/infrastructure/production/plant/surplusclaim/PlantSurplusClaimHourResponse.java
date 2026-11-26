package org.lucoenergia.conluz.infrastructure.production.plant.surplusclaim;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.production.plant.surplusclaim.SurplusClaimHour;
import org.lucoenergia.conluz.infrastructure.shared.web.KWhRounding;

import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * One hour of a plant's surplus claim. The instant the hour starts at identifies it: on the day the clocks go back,
 * two consecutive hours share their local time.
 */
@Schema(requiredProperties = {"startsAt", "surplusKWh"})
public class PlantSurplusClaimHourResponse {

    @Schema(description = "Instant the hour starts at, with the offset in force in the community's time zone at " +
            "that instant. Unique within the month, including on the day the clocks go back.",
            example = "2026-10-25T02:00:00+01:00", format = "date-time")
    private final OffsetDateTime startsAt;
    @Schema(description = "Surplus of the supplies in scope over the hour, summed, in kWh, rounded to 3 decimals. " +
            "A record carrying 0 counts as a measured zero. Null when none of the supplies in scope stored a " +
            "surplus record for the hour.",
            example = "12.5", types = {"number", "null"})
    private final Double surplusKWh;

    public PlantSurplusClaimHourResponse(SurplusClaimHour hour, ZoneId zone) {
        this.startsAt = hour.startsAt().atZone(zone).toOffsetDateTime();
        this.surplusKWh = round(hour.surplusKWh());
    }

    static Double round(Double kWh) {
        return kWh == null ? null : KWhRounding.round(kWh);
    }

    public OffsetDateTime getStartsAt() {
        return startsAt;
    }

    public Double getSurplusKWh() {
        return surplusKWh;
    }
}

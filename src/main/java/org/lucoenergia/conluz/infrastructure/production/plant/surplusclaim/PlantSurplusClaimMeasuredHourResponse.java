package org.lucoenergia.conluz.infrastructure.production.plant.surplusclaim;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.production.plant.surplusclaim.SurplusClaimHour;
import org.lucoenergia.conluz.infrastructure.shared.web.KWhRounding;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Objects;

/**
 * An hour of a plant's surplus claim singled out for its surplus: a day's peak hour, or an hour above the plant's
 * nominal power. Such an hour always has a surplus, unlike an hour of {@link PlantSurplusClaimHourResponse}.
 */
@Schema(requiredProperties = {"startsAt", "surplusKWh"})
public class PlantSurplusClaimMeasuredHourResponse {

    @Schema(description = "Instant the hour starts at, with the offset in force in the community's time zone at " +
            "that instant. Unique within the month, including on the day the clocks go back.",
            example = "2026-10-25T02:00:00+01:00", format = "date-time")
    private final OffsetDateTime startsAt;
    @Schema(description = "Surplus of the supplies in scope over the hour, summed, in kWh, rounded to 3 decimals.",
            example = "12.5")
    private final double surplusKWh;

    public PlantSurplusClaimMeasuredHourResponse(SurplusClaimHour hour, ZoneId zone) {
        this.startsAt = hour.startsAt().atZone(zone).toOffsetDateTime();
        this.surplusKWh = KWhRounding.round(Objects.requireNonNull(hour.surplusKWh(),
                "An hour singled out for its surplus always has one"));
    }

    public OffsetDateTime getStartsAt() {
        return startsAt;
    }

    public double getSurplusKWh() {
        return surplusKWh;
    }
}

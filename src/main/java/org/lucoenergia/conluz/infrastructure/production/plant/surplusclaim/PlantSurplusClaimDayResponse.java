package org.lucoenergia.conluz.infrastructure.production.plant.surplusclaim;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.production.plant.surplusclaim.SurplusClaimDay;
import org.lucoenergia.conluz.domain.production.plant.surplusclaim.SurplusClaimDayState;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * One local day of a plant's surplus claim.
 */
@Schema(requiredProperties = {"date", "state", "surplusKWh", "peakHour", "hours"})
public class PlantSurplusClaimDayResponse {

    @Schema(description = "Local calendar date of the day, in the community's time zone.", example = "2026-06-10")
    private final LocalDate date;
    @Schema(description = "REAL when every supply in scope stored a surplus record for every hour of the day; " +
            "ESTIMATED otherwise, and on every day when no supply is in scope.", example = "REAL")
    private final SurplusClaimDayState state;
    @Schema(description = "Surplus of the day's hours, summed, in kWh, rounded to 3 decimals. Null when every hour " +
            "of the day is null.", example = "152.375", types = {"number", "null"})
    private final Double surplusKWh;
    @Schema(description = "The hour with the largest surplus of the day, the earliest one on a tie. Null when the " +
            "day's surplus is null or 0.", types = {"object", "null"})
    private final PlantSurplusClaimHourResponse peakHour;
    @Schema(description = "Every hour of the day, in order: 23, 24 or 25 on the days the clocks change.")
    private final List<PlantSurplusClaimHourResponse> hours;

    public PlantSurplusClaimDayResponse(SurplusClaimDay day, ZoneId zone) {
        this.date = day.date();
        this.state = day.state();
        this.surplusKWh = PlantSurplusClaimHourResponse.round(day.surplusKWh());
        this.peakHour = day.peakHour() == null ? null : new PlantSurplusClaimHourResponse(day.peakHour(), zone);
        this.hours = day.hours().stream().map(hour -> new PlantSurplusClaimHourResponse(hour, zone)).toList();
    }

    public LocalDate getDate() {
        return date;
    }

    public SurplusClaimDayState getState() {
        return state;
    }

    public Double getSurplusKWh() {
        return surplusKWh;
    }

    public PlantSurplusClaimHourResponse getPeakHour() {
        return peakHour;
    }

    public List<PlantSurplusClaimHourResponse> getHours() {
        return hours;
    }
}

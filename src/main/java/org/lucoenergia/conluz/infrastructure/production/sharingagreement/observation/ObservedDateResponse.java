package org.lucoenergia.conluz.infrastructure.production.sharingagreement.observation;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.production.sharingagreement.observation.ObservedDate;

import java.time.LocalDate;

/**
 * A local day observed as precisely as the data allows: exact when {@code from} equals {@code to}, a range
 * otherwise, and open ("on or before {@code to}") when {@code from} is null.
 */
@Schema(requiredProperties = {"from", "to"})
public class ObservedDateResponse {

    @Schema(description = "Earliest possible local day. Null when open: the observation starts on the first " +
            "valid day of the data, so it may have started earlier.", example = "2024-05-08",
            types = {"string", "null"})
    private final LocalDate from;

    @Schema(description = "Latest possible local day: the first day the observation rests on.",
            example = "2024-05-10")
    private final LocalDate to;

    public ObservedDateResponse(ObservedDate date) {
        this.from = date.from();
        this.to = date.to();
    }

    static ObservedDateResponse of(ObservedDate date) {
        return date == null ? null : new ObservedDateResponse(date);
    }

    public LocalDate getFrom() {
        return from;
    }

    public LocalDate getTo() {
        return to;
    }
}

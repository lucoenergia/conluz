package org.lucoenergia.conluz.infrastructure.production.sharingagreement.observation;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.production.sharingagreement.observation.UnallocatedShare;

import java.time.LocalDate;

/**
 * The share of the plant's production no supply with data was observed to receive while an agreement was
 * applied, next to the share the agreement registers for the supplies without data.
 */
@Schema(requiredProperties = {"unallocated", "registeredShareWithoutData", "days", "from", "to"})
public class UnallocatedShareResponse {

    @Schema(description = "1 − Σβ: the production no supply with data received, on a 0-1 scale, weighted by the " +
            "plant's daily production", example = "0.0565")
    private final double unallocated;

    @Schema(description = "The share the agreement registers for the supplies without data and for the plant's own " +
            "supply on each day, on a 0-1 scale, weighted by the plant's daily production", example = "0.0565")
    private final double registeredShareWithoutData;

    @Schema(description = "Number of the plant's valid days the figures rest on", example = "184")
    private final int days;

    @Schema(description = "First local day of the agreement's applied window, which the figures are computed over: " +
            "its most frequent observed application date (the latest day of a range or of an open date)",
            example = "2024-02-01")
    private final LocalDate from;

    @Schema(description = "Last local day of the agreement's applied window: the day before the next agreement's " +
            "most frequent observed application date, or the end of the analysed range", example = "2025-11-30")
    private final LocalDate to;

    public UnallocatedShareResponse(UnallocatedShare share) {
        this.unallocated = share.unallocated();
        this.registeredShareWithoutData = share.registeredShareWithoutData();
        this.days = share.days();
        this.from = share.from();
        this.to = share.to();
    }

    public double getUnallocated() {
        return unallocated;
    }

    public double getRegisteredShareWithoutData() {
        return registeredShareWithoutData;
    }

    public int getDays() {
        return days;
    }

    public LocalDate getFrom() {
        return from;
    }

    public LocalDate getTo() {
        return to;
    }
}

package org.lucoenergia.conluz.infrastructure.price.sync;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

@Schema(requiredProperties = {"startDate", "endDate"})
public class SyncPricesBody {

    @Schema(description = "First market day to synchronise, included: a calendar day in Europe/Madrid.",
            example = "2026-03-28")
    @NotNull
    private LocalDate startDate;

    @Schema(description = "Market day the range ends at, excluded: a calendar day in Europe/Madrid.",
            example = "2026-03-30")
    @NotNull
    private LocalDate endDate;

    public SyncPricesBody() {
    }

    public SyncPricesBody(LocalDate startDate, LocalDate endDate) {
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }
}

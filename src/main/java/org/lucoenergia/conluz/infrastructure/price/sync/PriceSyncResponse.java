package org.lucoenergia.conluz.infrastructure.price.sync;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.price.sync.PriceSyncResult;

import java.time.LocalDate;

@Schema(description = "What the synchronisation did with one market day.",
        requiredProperties = {"date", "outcome", "storedPrices", "skipReason"})
public class PriceSyncResponse {

    @Schema(description = "The market day, a calendar day in Europe/Madrid.", example = "2026-03-29")
    private final LocalDate date;

    @Schema(description = "STORED when the day's prices were written, replacing those stored at the same "
            + "instants; SKIPPED when they were not, leaving whatever was stored for the day.", example = "STORED")
    private final PriceSyncResult.Outcome outcome;

    @Schema(description = "Number of prices written for the day; 0 when it was skipped.", example = "92")
    private final int storedPrices;

    @Schema(description = "Why the day was skipped; null when it was stored. FILE_NOT_FOUND: OMIE has no file for "
            + "the day, which is expected for tomorrow before around 13:00 Europe/Madrid. FILE_REJECTED: the file "
            + "does not match the format expected for the day. FETCH_FAILED: the file could not be fetched, because "
            + "the connection failed or OMIE answered a server error; sending the same request again may succeed.",
            example = "FILE_NOT_FOUND", types = {"string", "null"})
    private final PriceSyncResult.SkipReason skipReason;

    public PriceSyncResponse(PriceSyncResult result) {
        this.date = result.date();
        this.outcome = result.outcome();
        this.storedPrices = result.storedPrices();
        this.skipReason = result.skipReason();
    }

    public LocalDate getDate() {
        return date;
    }

    public PriceSyncResult.Outcome getOutcome() {
        return outcome;
    }

    public int getStoredPrices() {
        return storedPrices;
    }

    public PriceSyncResult.SkipReason getSkipReason() {
        return skipReason;
    }
}

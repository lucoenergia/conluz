package org.lucoenergia.conluz.domain.price.sync;

import java.time.LocalDate;

/**
 * What a synchronisation did with one market day: stored its prices, or skipped it and why.
 *
 * @param date         the market day, in Europe/Madrid
 * @param outcome      whether the day's prices were stored
 * @param storedPrices the number of prices written; 0 when the day was skipped
 * @param skipReason   why the day was skipped; null when it was stored
 */
public record PriceSyncResult(LocalDate date, Outcome outcome, int storedPrices, SkipReason skipReason) {

    public enum Outcome {
        STORED,
        SKIPPED
    }

    public enum SkipReason {
        /**
         * OMIE has no file for the day. Before around 13:00 this is expected for tomorrow.
         */
        FILE_NOT_FOUND,
        /**
         * The day's file does not match the format expected for the day (PRC-002, PRC-003).
         */
        FILE_REJECTED,
        /**
         * The day's file could not be fetched: the connection failed or OMIE answered a server error.
         */
        FETCH_FAILED
    }

    public static PriceSyncResult of(OmieDayPrices day) {
        return switch (day.status()) {
            case SERVED -> new PriceSyncResult(day.day(), Outcome.STORED, day.prices().size(), null);
            case NOT_FOUND -> skipped(day.day(), SkipReason.FILE_NOT_FOUND);
            case REJECTED -> skipped(day.day(), SkipReason.FILE_REJECTED);
            case FETCH_FAILED -> skipped(day.day(), SkipReason.FETCH_FAILED);
        };
    }

    private static PriceSyncResult skipped(LocalDate date, SkipReason reason) {
        return new PriceSyncResult(date, Outcome.SKIPPED, 0, reason);
    }
}

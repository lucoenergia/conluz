package org.lucoenergia.conluz.domain.price.sync;

import org.lucoenergia.conluz.domain.price.PriceByHour;

import java.time.LocalDate;
import java.util.List;

/**
 * The prices OMIE serves for one market day, or why there are none.
 *
 * @param day    the market day, in Europe/Madrid
 * @param status whether the day's file was served, and if not, why
 * @param prices the day's prices when it was served; empty otherwise
 */
public record OmieDayPrices(LocalDate day, Status status, List<PriceByHour> prices) {

    public enum Status {
        /**
         * The day's file was fetched and parsed.
         */
        SERVED,
        /**
         * OMIE has no file for the day: it answered a client error, or an empty file.
         */
        NOT_FOUND,
        /**
         * The day's file was fetched but its content does not match the format expected for the day.
         */
        REJECTED,
        /**
         * The day's file could not be fetched: the connection failed or OMIE answered a server error.
         */
        FETCH_FAILED
    }

    public OmieDayPrices {
        prices = List.copyOf(prices);
    }

    public static OmieDayPrices served(LocalDate day, List<PriceByHour> prices) {
        return new OmieDayPrices(day, Status.SERVED, prices);
    }

    public static OmieDayPrices notServed(LocalDate day, Status status) {
        if (status == Status.SERVED) {
            throw new IllegalArgumentException("A served day carries its prices");
        }
        return new OmieDayPrices(day, status, List.of());
    }
}

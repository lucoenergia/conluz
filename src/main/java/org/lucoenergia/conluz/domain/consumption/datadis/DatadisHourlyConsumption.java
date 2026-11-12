package org.lucoenergia.conluz.domain.consumption.datadis;

import java.time.Instant;
import java.util.Objects;

/**
 * One hour of a supply's stored consumption, together with the instant the hour starts at.
 *
 * <p>The instant is kept beside {@link DatadisConsumption} rather than in it: that class is the shape
 * the external Datadis API speaks, and its local {@code date} and {@code time} labels cannot tell
 * apart the two hours a daylight saving fall-back repeats. The instant can.
 */
public class DatadisHourlyConsumption {

    private final Instant startsAt;
    private final DatadisConsumption consumption;
    private final long publishedHours;

    public DatadisHourlyConsumption(Instant startsAt, DatadisConsumption consumption, long publishedHours) {
        this.startsAt = Objects.requireNonNull(startsAt);
        this.consumption = Objects.requireNonNull(consumption);
        this.publishedHours = publishedHours;
    }

    /**
     * The instant the hour starts at, inclusive.
     */
    public Instant getStartsAt() {
        return startsAt;
    }

    public DatadisConsumption getConsumption() {
        return consumption;
    }

    /**
     * The number of the hour's records carrying self-consumption, zero included: 0 when the hour's
     * self-consumption has not been published, or nothing was stored.
     */
    public long getPublishedHours() {
        return publishedHours;
    }

    @Override
    public String toString() {
        return "DatadisHourlyConsumption{" +
                "startsAt=" + startsAt +
                ", consumption=" + consumption +
                ", publishedHours=" + publishedHours +
                '}';
    }
}

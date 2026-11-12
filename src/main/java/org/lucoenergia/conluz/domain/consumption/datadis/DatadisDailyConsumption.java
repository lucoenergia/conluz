package org.lucoenergia.conluz.domain.consumption.datadis;

import java.util.Objects;

/**
 * One local day of a supply's stored consumption, together with the number of its records whose
 * self-consumption has been published.
 *
 * <p>The count is kept beside {@link DatadisConsumption} rather than in it: that class is the shape
 * the external Datadis API speaks, and the summed figures it carries cannot tell a record without
 * self-consumption from one whose self-consumption is zero. The count can.
 */
public class DatadisDailyConsumption {

    private final DatadisConsumption consumption;
    private final long publishedHours;

    public DatadisDailyConsumption(DatadisConsumption consumption, long publishedHours) {
        this.consumption = Objects.requireNonNull(consumption);
        this.publishedHours = publishedHours;
    }

    public DatadisConsumption getConsumption() {
        return consumption;
    }

    /**
     * The number of the day's records carrying self-consumption, zero included.
     */
    public long getPublishedHours() {
        return publishedHours;
    }

    @Override
    public String toString() {
        return "DatadisDailyConsumption{" +
                "consumption=" + consumption +
                ", publishedHours=" + publishedHours +
                '}';
    }
}

package org.lucoenergia.conluz.domain.datadis;

import java.time.Instant;
import java.util.Objects;

/**
 * An hourly record read from Datadis together with the instant it is stored at. Datadis labels a record with a
 * local date and time only, so the instant is resolved once, from all the records of the supply, before anything is
 * persisted; the persistence ports store the record at exactly this instant.
 *
 * @param instant the instant the record is stored at
 * @param record  the record's values
 * @param <T>     the kind of record, consumption or production
 */
public record DatadisHourlyRecord<T>(Instant instant, T record) {

    public DatadisHourlyRecord {
        Objects.requireNonNull(instant, "instant");
        Objects.requireNonNull(record, "record");
    }
}

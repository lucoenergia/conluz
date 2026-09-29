package org.lucoenergia.conluz.domain.consumption.datadis.metrics;

import org.lucoenergia.conluz.domain.admin.supply.Supply;

import java.time.OffsetDateTime;
import java.util.function.Consumer;

/**
 * Reads a supply's stored hourly consumption records one by one, for aggregations the store cannot
 * compute itself, without holding the whole period in memory.
 */
public interface GetDatadisHourlyRecordsRepository {

    /**
     * Hands every hourly record the supply stored between the two instants, both bounds inclusive,
     * to {@code consumer}, in no guaranteed order. Returns once every record has been handed over.
     *
     * <p>The consumer may be invoked on a thread other than the caller's; everything it did is
     * visible to the caller once this method returns.</p>
     */
    void forEachRecord(Supply supply, OffsetDateTime startDate, OffsetDateTime endDate,
                       Consumer<HourlyEnergyRecord> consumer);
}

package org.lucoenergia.conluz.infrastructure.consumption.datadis.metrics;

import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBException;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.consumption.datadis.metrics.GetDatadisHourlyRecordsRepository;
import org.lucoenergia.conluz.domain.consumption.datadis.metrics.HourlyEnergyRecord;
import org.lucoenergia.conluz.infrastructure.datadis.config.DatadisConfigEntity;
import org.lucoenergia.conluz.infrastructure.shared.db.influxdb.InfluxDbConnectionManager;
import org.lucoenergia.conluz.infrastructure.shared.time.DateConverter;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Streams a supply's hourly consumption records out of InfluxDB in chunks, so the memory cost of a
 * read is bounded by the chunk size, whatever the length of the period.
 *
 * <p>The client delivers chunks asynchronously on its own thread; the read blocks until the stream
 * completes or fails, and rethrows any failure on the caller's thread.</p>
 */
@Repository
public class GetDatadisHourlyRecordsRepositoryInflux implements GetDatadisHourlyRecordsRepository {

    /**
     * Records per chunk. A month of one supply's hourly records fits in a single chunk.
     */
    static final int CHUNK_SIZE = 10_000;

    /**
     * The error the client attaches to the synthetic result it emits when the stream ends.
     */
    private static final String END_OF_STREAM = "DONE";

    private static final String COLUMN_TIME = "time";
    private static final String COLUMN_CONSUMPTION_KWH = "consumption_kwh";
    private static final String COLUMN_SELF_CONSUMPTION_ENERGY_KWH = "self_consumption_energy_kwh";
    private static final String COLUMN_SURPLUS_ENERGY_KWH = "surplus_energy_kwh";

    private final InfluxDbConnectionManager influxDbConnectionManager;
    private final DateConverter dateConverter;

    public GetDatadisHourlyRecordsRepositoryInflux(InfluxDbConnectionManager influxDbConnectionManager,
                                                   DateConverter dateConverter) {
        this.influxDbConnectionManager = influxDbConnectionManager;
        this.dateConverter = dateConverter;
    }

    @Override
    public void forEachRecord(Supply supply, OffsetDateTime startDate, OffsetDateTime endDate,
                              Consumer<HourlyEnergyRecord> consumer) {
        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {

            // Both bounds are inclusive, matching the aggregate over the same period.
            Query query = new Query(String.format(
                    """
                            SELECT "consumption_kwh", "self_consumption_energy_kwh", "surplus_energy_kwh"
                            FROM "%s"
                            WHERE cups = '%s'
                                AND time >= '%s'
                                AND time <= '%s'
                            """,
                    DatadisConfigEntity.CONSUMPTION_KWH_MEASUREMENT,
                    supply.getCode(),
                    dateConverter.convertToString(startDate),
                    dateConverter.convertToString(endDate)));

            CountDownLatch finished = new CountDownLatch(1);
            AtomicReference<Throwable> failure = new AtomicReference<>();

            connection.query(query, CHUNK_SIZE,
                    (cancellable, chunk) -> {
                        String error = chunk.getError();
                        if (error == null) {
                            accept(chunk, consumer);
                        } else if (!END_OF_STREAM.equals(error)) {
                            failure.compareAndSet(null, new InfluxDBException(error));
                            cancellable.cancel();
                            finished.countDown();
                        }
                    },
                    finished::countDown,
                    throwable -> {
                        failure.compareAndSet(null, throwable);
                        finished.countDown();
                    });

            awaitCompletion(finished);
            rethrow(failure.get());
        }
    }

    private void accept(QueryResult chunk, Consumer<HourlyEnergyRecord> consumer) {
        if (chunk.getResults() == null) {
            return;
        }
        for (QueryResult.Result result : chunk.getResults()) {
            if (result.getError() != null) {
                throw new InfluxDBException(result.getError());
            }
            if (result.getSeries() == null) {
                continue;
            }
            for (QueryResult.Series series : result.getSeries()) {
                if (series.getValues() == null) {
                    continue;
                }
                List<String> columns = series.getColumns();
                int time = columns.indexOf(COLUMN_TIME);
                int gridImport = columns.indexOf(COLUMN_CONSUMPTION_KWH);
                int selfConsumption = columns.indexOf(COLUMN_SELF_CONSUMPTION_ENERGY_KWH);
                int surplus = columns.indexOf(COLUMN_SURPLUS_ENERGY_KWH);
                for (List<Object> row : series.getValues()) {
                    consumer.accept(new HourlyEnergyRecord(
                            Instant.parse((String) row.get(time)),
                            readDouble(row, gridImport),
                            readDouble(row, selfConsumption),
                            readDouble(row, surplus)));
                }
            }
        }
    }

    /**
     * Reads a numeric column, keeping an absent column and a null value as null: unlike a sum, a
     * single record's missing field is not a zero.
     */
    private static Double readDouble(List<Object> row, int index) {
        if (index < 0 || index >= row.size()) {
            return null;
        }
        return row.get(index) instanceof Number number ? number.doubleValue() : null;
    }

    private static void awaitCompletion(CountDownLatch finished) {
        try {
            // The client's own read timeout bounds the wait: a stalled stream ends in a failure.
            finished.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InfluxDBException(e);
        }
    }

    private static void rethrow(Throwable failure) {
        if (failure == null) {
            return;
        }
        if (failure instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        throw new InfluxDBException(failure);
    }
}

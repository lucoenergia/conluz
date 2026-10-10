package org.lucoenergia.conluz.infrastructure.datadis.metrics;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.consumption.datadis.metrics.GetDatadisHourlyRecordsRepository;
import org.lucoenergia.conluz.domain.consumption.datadis.metrics.HourlyEnergyRecord;
import org.lucoenergia.conluz.infrastructure.datadis.DatadisConsumptionInfluxFixture;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for the hourly record stream against a real InfluxDB started by Testcontainers
 * (see {@link BaseIntegrationTest}).
 *
 * <p>These pin what only the engine and the client can show: that a field a record does not carry
 * comes back as null while a stored zero comes back as zero, that both bounds are inclusive, and
 * that a period longer than one chunk is handed over in full.</p>
 */
class GetDatadisHourlyRecordsRepositoryInfluxIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private GetDatadisHourlyRecordsRepository repository;
    @Autowired
    private DatadisConsumptionInfluxFixture fixture;

    private final List<String> createdCupsCodes = new ArrayList<>();

    @AfterEach
    void afterEach() {
        createdCupsCodes.forEach(fixture::clear);
    }

    @Test
    void anAbsentFieldIsReadAsNullAndAStoredZeroAsZero() {
        Supply supply = supply();
        fixture.writeAt(supply.getCode(), Instant.parse("2024-02-01T10:00:00Z"), 1.5f, 0.0f, 0.0f);
        fixture.writeAt(supply.getCode(), Instant.parse("2024-02-01T11:00:00Z"), 2.5f, null, null);

        List<HourlyEnergyRecord> records = read(supply, "2024-02-01T10:00:00Z", "2024-02-01T11:00:00Z");

        assertEquals(2, records.size());
        HourlyEnergyRecord zeros = records.get(0);
        assertEquals(Instant.parse("2024-02-01T10:00:00Z"), zeros.getTime());
        assertEquals(1.5d, zeros.getGridImportKWh(), 1e-6);
        assertEquals(0d, zeros.getSelfConsumptionKWh());
        assertEquals(0d, zeros.getSurplusKWh());
        HourlyEnergyRecord absent = records.get(1);
        assertEquals(2.5d, absent.getGridImportKWh(), 1e-6);
        assertNull(absent.getSelfConsumptionKWh());
        assertNull(absent.getSurplusKWh());
    }

    @Test
    void includesTheRecordsSittingExactlyOnEitherBoundAndExcludesTheRest() {
        Supply supply = supply();
        fixture.writeAt(supply.getCode(), List.of(
                Instant.parse("2024-02-01T09:00:00Z"),
                Instant.parse("2024-02-01T10:00:00Z"),
                Instant.parse("2024-02-01T11:00:00Z"),
                Instant.parse("2024-02-01T12:00:00Z"),
                Instant.parse("2024-02-01T13:00:00Z")), 1.0f, 1.0f, 1.0f);

        List<HourlyEnergyRecord> records = read(supply, "2024-02-01T10:00:00Z", "2024-02-01T12:00:00Z");

        assertEquals(List.of(
                        Instant.parse("2024-02-01T10:00:00Z"),
                        Instant.parse("2024-02-01T11:00:00Z"),
                        Instant.parse("2024-02-01T12:00:00Z")),
                records.stream().map(HourlyEnergyRecord::getTime).toList());
    }

    @Test
    void anotherSuppliesRecordsAreNeverHandedOver() {
        Supply supply = supply();
        Supply neighbour = supply();
        fixture.writeAt(supply.getCode(), Instant.parse("2024-02-01T10:00:00Z"), 1.0f, null, null);
        fixture.writeAt(neighbour.getCode(), Instant.parse("2024-02-01T11:00:00Z"), 9.0f, null, null);

        List<HourlyEnergyRecord> records = read(supply, "2024-02-01T00:00:00Z", "2024-02-02T00:00:00Z");

        assertEquals(1, records.size());
        assertEquals(1.0d, records.get(0).getGridImportKWh(), 1e-6);
    }

    @Test
    void aSupplyWithoutRecordsInThePeriodHandsOverNothing() {
        assertTrue(read(supply(), "2024-02-01T00:00:00Z", "2024-02-02T00:00:00Z").isEmpty());
    }

    /**
     * More records than fit in one chunk: every one of them must still reach the consumer, once.
     */
    @Test
    void aPeriodLongerThanOneChunkIsHandedOverInFull() {
        Supply supply = supply();
        Instant first = Instant.parse("2023-01-01T00:00:00Z");
        int count = GetDatadisHourlyRecordsRepositoryInflux.CHUNK_SIZE + 500;
        List<Instant> times = LongStream.range(0, count).mapToObj(hour -> first.plus(Duration.ofHours(hour))).toList();
        fixture.writeAt(supply.getCode(), times, 1.0f, 0.5f, 0.25f);

        List<HourlyEnergyRecord> records = read(supply, first.toString(), times.get(count - 1).toString());

        assertEquals(count, records.size());
        assertEquals(times, records.stream().map(HourlyEnergyRecord::getTime).sorted().toList());
    }

    private List<HourlyEnergyRecord> read(Supply supply, String startDate, String endDate) {
        List<HourlyEnergyRecord> records = new ArrayList<>();
        repository.forEachRecord(supply, OffsetDateTime.parse(startDate), OffsetDateTime.parse(endDate),
                records::add);
        records.sort(Comparator.comparing(HourlyEnergyRecord::getTime));
        return records;
    }

    private Supply supply() {
        String code = "ES" + RandomStringUtils.random(20, false, true);
        createdCupsCodes.add(code);
        return SupplyMother.random().withCode(code).build();
    }
}

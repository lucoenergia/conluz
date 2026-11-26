package org.lucoenergia.conluz.infrastructure.datadis.get;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.consumption.datadis.DatadisConsumption;
import org.lucoenergia.conluz.domain.consumption.datadis.get.GetDatadisConsumptionRepository;
import org.lucoenergia.conluz.domain.datadis.DatadisHourlyRecord;
import org.lucoenergia.conluz.infrastructure.datadis.DatadisConsumptionInfluxFixture;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.lucoenergia.conluz.infrastructure.datadis.DatadisConsumptionInfluxFixture.hourlyRecord;

/**
 * The supply's hourly records read as stored. Records are written through the production persistence port, by the
 * fixture.
 */
class GetDatadisConsumptionRepositoryInfluxHourlyRecordsTest extends BaseIntegrationTest {

    private static final String CUPS = "ES0031406912345678HR0F";
    private static final String OTHER_CUPS = "ES0031406912345678HS0F";
    private static final OffsetDateTime FROM = OffsetDateTime.parse("2026-09-01T00:00+02:00");
    private static final OffsetDateTime TO = OffsetDateTime.parse("2026-10-01T00:00+02:00");

    @Autowired
    @Qualifier("getDatadisConsumptionRepositoryInflux")
    private GetDatadisConsumptionRepository repository;
    @Autowired
    private DatadisConsumptionInfluxFixture fixture;

    private final Supply supply = SupplyMother.random().withCode(CUPS).build();

    @AfterEach
    void tearDown() {
        fixture.clear(CUPS);
        fixture.clear(OTHER_CUPS);
    }

    @Test
    @DisplayName("DCX-004 one record per stored point, in chronological order, and none for the hours between them")
    void storedRecordsOnlyInChronologicalOrder() {
        fixture.write(List.of(
                hourlyRecord(CUPS, "2026/09/20", "06:00", 3f, 0.3f, 0f),
                hourlyRecord(CUPS, "2026/09/02", "01:00", 1f, 0.1f, 0f),
                hourlyRecord(CUPS, "2026/09/10", "13:00", 2f, 0.2f, 0f),
                hourlyRecord(OTHER_CUPS, "2026/09/10", "14:00", 9f, 0.9f, 0f)));

        List<DatadisHourlyRecord<DatadisConsumption>> records = repository.getHourlyRecordsByPeriod(supply, FROM, TO);

        assertEquals(List.of(Instant.parse("2026-09-01T22:00:00Z"), Instant.parse("2026-09-10T10:00:00Z"),
                Instant.parse("2026-09-20T03:00:00Z")), records.stream().map(DatadisHourlyRecord::instant).toList());
        assertEquals(List.of(1f, 2f, 3f),
                records.stream().map(r -> r.record().getConsumptionKWh()).toList());
        records.forEach(r -> assertEquals(CUPS, r.record().getCups()));
        records.forEach(r -> assertEquals("Real", r.record().getObtainMethod()));
    }

    @Test
    @DisplayName("DCX-003 the period is half-open: a record at its start is read, one at its end is not")
    void thePeriodIsHalfOpen() {
        fixture.writeAt(CUPS, List.of(FROM.toInstant().minusSeconds(3600), FROM.toInstant(),
                TO.toInstant().minusSeconds(3600), TO.toInstant()), 1f, 0f, 0f);

        assertEquals(List.of(FROM.toInstant(), TO.toInstant().minusSeconds(3600)),
                repository.getHourlyRecordsByPeriod(supply, FROM, TO).stream()
                        .map(DatadisHourlyRecord::instant).toList());
    }

    @Test
    @DisplayName("DCX-001 a field the record does not carry is null, not zero, and the values are the stored floats")
    void absentFieldsAreNullAndValuesAreTheStoredFloats() {
        DatadisConsumption withGeneration = hourlyRecord(CUPS, "2026/09/10", "13:00", 0.0005f, null, 12.345f);
        withGeneration.setGenerationEnergyKWh(1f);
        fixture.write(List.of(withGeneration, hourlyRecord(CUPS, "2026/09/11", "13:00", 0.25f, 0f, null)));

        List<DatadisHourlyRecord<DatadisConsumption>> records = repository.getHourlyRecordsByPeriod(supply, FROM, TO);

        DatadisConsumption first = records.get(0).record();
        assertEquals(0.0005f, first.getConsumptionKWh());
        assertNull(first.getSelfConsumptionEnergyKWh());
        assertEquals(12.345f, first.getSurplusEnergyKWh());
        assertEquals(1f, first.getGenerationEnergyKWh());
        DatadisConsumption second = records.get(1).record();
        assertEquals(0f, second.getSelfConsumptionEnergyKWh());
        assertNull(second.getSurplusEnergyKWh());
        assertNull(second.getGenerationEnergyKWh());
        assertNull(second.getDate());
        assertNull(second.getTime());
    }

    @Test
    @DisplayName("DCX-004 an hour ingested again is still a single record, carrying the values written last")
    void aReingestedHourIsASingleRecord() {
        fixture.write(List.of(hourlyRecord(CUPS, "2026/09/10", "13:00", 1f, 0f, 0f),
                hourlyRecord(CUPS, "2026/09/10", "14:00", 5f, 0f, 0f)));
        fixture.write(List.of(hourlyRecord(CUPS, "2026/09/10", "13:00", 2f, 0.5f, 0f)));

        List<DatadisHourlyRecord<DatadisConsumption>> records = repository.getHourlyRecordsByPeriod(supply, FROM, TO);

        assertEquals(2, records.size());
        assertEquals(Instant.parse("2026-09-10T10:00:00Z"), records.get(0).instant());
        assertEquals(2f, records.get(0).record().getConsumptionKWh());
        assertEquals(0.5f, records.get(0).record().getSelfConsumptionEnergyKWh());
    }
}

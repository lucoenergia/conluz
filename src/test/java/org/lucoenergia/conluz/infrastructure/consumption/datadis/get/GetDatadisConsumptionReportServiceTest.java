package org.lucoenergia.conluz.infrastructure.consumption.datadis.get;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.consumption.datadis.DatadisConsumption;
import org.lucoenergia.conluz.domain.consumption.datadis.get.GetDatadisConsumptionRepository;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetDatadisConsumptionReportServiceTest {

    @Mock
    private GetDatadisConsumptionRepository getDatadisConsumptionRepository;

    @Mock
    private GetSupplyRepository getSupplyRepository;

    @InjectMocks
    private GetDatadisConsumptionReportServiceImpl service;

    private static final OffsetDateTime START = OffsetDateTime.parse("2023-04-01T00:00:00Z");
    private static final OffsetDateTime END = OffsetDateTime.parse("2023-04-30T23:59:59Z");

    @Test
    void testWithNoSuppliesReturnsOnlyHeaderRow() {
        // when
        when(getSupplyRepository.findAll()).thenReturn(Collections.emptyList());

        ByteArrayOutputStream result = service.getHourlyConsumptionReportAsCsv(START, END);
        String csv = result.toString(StandardCharsets.UTF_8);

        // then
        assertTrue(csv.contains("\"cups\""));
        assertTrue(csv.contains("\"date\""));
        assertTrue(csv.contains("\"time\""));
        assertTrue(csv.contains("\"consumptionKWh\""));
        assertTrue(csv.contains("\"obtainMethod\""));
        assertTrue(csv.contains("\"surplusEnergyKWh\""));
        assertTrue(csv.contains("\"generationEnergyKWh\""));
        assertTrue(csv.contains("\"selfConsumptionEnergyKWh\""));
        assertEquals(1, csv.lines().filter(line -> !line.isBlank()).count());
    }

    @Test
    void testWithSingleSupplyAndConsumptionsIncludesDataRows() {
        // given
        Supply supply = SupplyMother.random().build();
        DatadisConsumption consumption = buildConsumption("ES0031406912345678JN0F", "2023/04/01", "10:00",
                0.75f, "Real", 0.10f, 0.20f, 0.15f);

        // when
        when(getSupplyRepository.findAll()).thenReturn(List.of(supply));
        when(getDatadisConsumptionRepository.getHourlyConsumptionsByRangeOfDates(supply, START, END))
                .thenReturn(List.of(consumption));

        ByteArrayOutputStream result = service.getHourlyConsumptionReportAsCsv(START, END);
        String csv = result.toString(StandardCharsets.UTF_8);

        // then
        assertTrue(csv.contains("\"ES0031406912345678JN0F\""));
        assertTrue(csv.contains("\"2023/04/01\""));
        assertTrue(csv.contains("\"10:00\""));
        assertTrue(csv.contains("\"0.75\""));
        assertTrue(csv.contains("\"Real\""));
        assertTrue(csv.contains("\"0.1\""));
        assertTrue(csv.contains("\"0.2\""));
        assertTrue(csv.contains("\"0.15\""));
        assertEquals(2, csv.lines().filter(line -> !line.isBlank()).count());
    }

    @Test
    void testWithMultipleSuppliesAggregatesAllConsumptions() {
        // given
        Supply supply1 = SupplyMother.random().build();
        Supply supply2 = SupplyMother.random().build();
        DatadisConsumption c1 = buildConsumption("CUPS1", "2023/04/01", "10:00", 0.5f, "Real", 0f, 0f, 0f);
        DatadisConsumption c2 = buildConsumption("CUPS2", "2023/04/01", "11:00", 0.6f, "Real", 0f, 0f, 0f);

        // when
        when(getSupplyRepository.findAll()).thenReturn(List.of(supply1, supply2));
        when(getDatadisConsumptionRepository.getHourlyConsumptionsByRangeOfDates(supply1, START, END))
                .thenReturn(List.of(c1));
        when(getDatadisConsumptionRepository.getHourlyConsumptionsByRangeOfDates(supply2, START, END))
                .thenReturn(List.of(c2));

        ByteArrayOutputStream result = service.getHourlyConsumptionReportAsCsv(START, END);
        String csv = result.toString(StandardCharsets.UTF_8);

        // then
        assertTrue(csv.contains("\"CUPS1\""));
        assertTrue(csv.contains("\"CUPS2\""));
        assertEquals(3, csv.lines().filter(line -> !line.isBlank()).count());
    }

    @Test
    void testNullNumericFieldsAreWrittenAsEmptyStrings() {
        // given
        Supply supply = SupplyMother.random().build();
        DatadisConsumption consumption = new DatadisConsumption();
        consumption.setCups("ES0031406912345678JN0F");
        consumption.setDate("2023/04/01");
        consumption.setTime("10:00");
        // consumptionKWh, surplusEnergyKWh, generationEnergyKWh, selfConsumptionEnergyKWh left null

        // when
        when(getSupplyRepository.findAll()).thenReturn(List.of(supply));
        when(getDatadisConsumptionRepository.getHourlyConsumptionsByRangeOfDates(supply, START, END))
                .thenReturn(List.of(consumption));

        ByteArrayOutputStream result = service.getHourlyConsumptionReportAsCsv(START, END);
        String csv = result.toString(StandardCharsets.UTF_8);

        // then
        String dataRow = csv.lines().skip(1).findFirst().orElseThrow();
        // Empty numeric fields appear as consecutive empty quoted values
        assertTrue(dataRow.contains(",\"\","));
    }

    @Test
    void testRepositoryIsQueriedForEachSupply() {
        // given
        Supply supply1 = SupplyMother.random().build();
        Supply supply2 = SupplyMother.random().build();
        Supply supply3 = SupplyMother.random().build();

        // when
        when(getSupplyRepository.findAll()).thenReturn(List.of(supply1, supply2, supply3));
        when(getDatadisConsumptionRepository.getHourlyConsumptionsByRangeOfDates(any(Supply.class), eq(START), eq(END)))
                .thenReturn(Collections.emptyList());

        service.getHourlyConsumptionReportAsCsv(START, END);

        // then
        verify(getDatadisConsumptionRepository, times(3))
                .getHourlyConsumptionsByRangeOfDates(any(Supply.class), eq(START), eq(END));
    }

    private DatadisConsumption buildConsumption(String cups, String date, String time,
                                                Float consumptionKWh, String obtainMethod,
                                                Float surplusEnergyKWh, Float generationEnergyKWh,
                                                Float selfConsumptionEnergyKWh) {
        DatadisConsumption c = new DatadisConsumption();
        c.setCups(cups);
        c.setDate(date);
        c.setTime(time);
        c.setConsumptionKWh(consumptionKWh);
        c.setObtainMethod(obtainMethod);
        c.setSurplusEnergyKWh(surplusEnergyKWh);
        c.setGenerationEnergyKWh(generationEnergyKWh);
        c.setSelfConsumptionEnergyKWh(selfConsumptionEnergyKWh);
        return c;
    }
}

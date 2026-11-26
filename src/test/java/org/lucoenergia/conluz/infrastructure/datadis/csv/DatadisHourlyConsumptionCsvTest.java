package org.lucoenergia.conluz.infrastructure.datadis.csv;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.consumption.datadis.DatadisConsumption;
import org.lucoenergia.conluz.domain.datadis.DatadisHourlyRecord;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class DatadisHourlyConsumptionCsvTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final String HEADER = "cups;date;time;consumptionKWh;obtainMethod;surplusEnergyKWh;"
            + "generationEnergyKWh;selfConsumptionEnergyKWh";

    @Test
    @DisplayName("DCX-001 the file is UTF-8 with a BOM, ; separated, CRLF ended, under the Datadis field names, one line per record in the order given")
    void theFileShape() {
        byte[] csv = DatadisHourlyConsumptionCsv.write(List.of(
                record("2026-09-01T10:00:00Z", 0.5f, "Real", 0.25f, 1.5f, 0.125f),
                record("2026-09-01T11:00:00Z", 2f, "Estimada", 0f, 0f, 0f)), MADRID);

        assertArrayEquals(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF}, Arrays.copyOf(csv, 3));
        assertEquals("﻿" + HEADER + "\r\n"
                        + "ES0031;2026/09/01;12:00;0,5;Real;0,25;1,5;0,125\r\n"
                        + "ES0031;2026/09/01;13:00;2;Estimada;0;0;0\r\n",
                new String(csv, StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("DCX-004 no record writes the header line only")
    void noRecordIsTheHeaderOnly() {
        assertEquals("﻿" + HEADER + "\r\n",
                new String(DatadisHourlyConsumptionCsv.write(List.of(), MADRID), StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("DCX-001 a number is the stored float unrounded, in plain notation with a decimal comma")
    void numbersArePlainUnroundedWithADecimalComma() {
        assertEquals("0,0005", DatadisHourlyConsumptionCsv.number(0.0005f));
        assertEquals("1", DatadisHourlyConsumptionCsv.number(1.0f));
        assertEquals("12,345", DatadisHourlyConsumptionCsv.number(12.345f));
        assertEquals("0,1", DatadisHourlyConsumptionCsv.number(0.1f));
        assertEquals("0", DatadisHourlyConsumptionCsv.number(0f));
        assertEquals("12345678", DatadisHourlyConsumptionCsv.number(1.2345678E7f));
        assertEquals("0,00000123", DatadisHourlyConsumptionCsv.number(1.23E-6f));
        assertEquals("-0,25", DatadisHourlyConsumptionCsv.number(-0.25f));
    }

    @Test
    @DisplayName("DCX-001 a null value is an empty field and its record is still written")
    void aNullIsAnEmptyField() {
        byte[] csv = DatadisHourlyConsumptionCsv.write(List.of(
                record("2026-09-01T10:00:00Z", 0.5f, null, null, null, null)), MADRID);

        assertEquals("﻿" + HEADER + "\r\nES0031;2026/09/01;12:00;0,5;;;;\r\n",
                new String(csv, StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("DCX-001 a text field holding ;, a quote, CR or LF is quoted with its quotes doubled, any other unchanged")
    void textIsQuotedOnlyWhenNeeded() {
        assertEquals("Real", DatadisHourlyConsumptionCsv.text("Real"));
        assertEquals("\"a;b\"", DatadisHourlyConsumptionCsv.text("a;b"));
        assertEquals("\"say \"\"hi\"\"\"", DatadisHourlyConsumptionCsv.text("say \"hi\""));
        assertEquals("\"a\rb\"", DatadisHourlyConsumptionCsv.text("a\rb"));
        assertEquals("\"a\nb\"", DatadisHourlyConsumptionCsv.text("a\nb"));
        assertEquals("a,b", DatadisHourlyConsumptionCsv.text("a,b"));
    }

    @Test
    @DisplayName("DCX-002 date and time are the local start of the hour; the hour the clocks repeat is two lines with the same date and time, in the order given")
    void theRepeatedHourKeepsItsOrder() {
        byte[] csv = DatadisHourlyConsumptionCsv.write(List.of(
                record("2026-10-25T00:00:00Z", 1f, "Real", 0f, 0f, 0f),
                record("2026-10-25T01:00:00Z", 2f, "Real", 0f, 0f, 0f),
                record("2026-10-25T02:00:00Z", 3f, "Real", 0f, 0f, 0f)), MADRID);

        List<String> lines = List.of(new String(csv, StandardCharsets.UTF_8).split("\r\n"));
        assertEquals("ES0031;2026/10/25;02:00;1;Real;0;0;0", lines.get(1));
        assertEquals("ES0031;2026/10/25;02:00;2;Real;0;0;0", lines.get(2));
        assertEquals("ES0031;2026/10/25;03:00;3;Real;0;0;0", lines.get(3));
    }

    private static DatadisHourlyRecord<DatadisConsumption> record(String instant, Float consumption,
                                                                  String obtainMethod, Float surplus,
                                                                  Float generation, Float selfConsumption) {
        DatadisConsumption values = new DatadisConsumption();
        values.setCups("ES0031");
        values.setConsumptionKWh(consumption);
        values.setObtainMethod(obtainMethod);
        values.setSurplusEnergyKWh(surplus);
        values.setGenerationEnergyKWh(generation);
        values.setSelfConsumptionEnergyKWh(selfConsumption);
        return new DatadisHourlyRecord<>(Instant.parse(instant), values);
    }
}

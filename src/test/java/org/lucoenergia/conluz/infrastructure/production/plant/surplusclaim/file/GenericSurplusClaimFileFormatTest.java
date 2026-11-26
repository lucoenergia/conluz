package org.lucoenergia.conluz.infrastructure.production.plant.surplusclaim.file;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.production.plant.surplusclaim.PlantSurplusClaim;
import org.lucoenergia.conluz.domain.production.plant.surplusclaim.file.SurplusClaimFile;
import org.lucoenergia.conluz.domain.production.plant.surplusclaim.file.SurplusClaimFileType;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lucoenergia.conluz.infrastructure.production.plant.surplusclaim.file.SurplusClaimFileTestClaims.PLANT_ID;
import static org.lucoenergia.conluz.infrastructure.production.plant.surplusclaim.file.SurplusClaimFileTestClaims.month;

class GenericSurplusClaimFileFormatTest {

    private static final YearMonth MARCH = YearMonth.of(2026, 3);
    private static final YearMonth OCTOBER = YearMonth.of(2026, 10);
    private static final String HEADER = "fecha;estado;HOR1;HOR2;HOR3;HOR4;HOR5;HOR6;HOR7;HOR8;HOR9;HOR10;HOR11;HOR12;"
            + "HOR13;HOR14;HOR15;HOR16;HOR17;HOR18;HOR19;HOR20;HOR21;HOR22;HOR23;HOR24;HOR25";

    private final GenericSurplusClaimFileFormat format = new GenericSurplusClaimFileFormat();

    @Test
    @DisplayName("SCL-019 a real day and an estimated day with 23 hours, byte by byte: BOM, CRLF, 3 decimals with a comma, empty fields")
    void twoDaysByteByByte() {
        LocalDate real = LocalDate.of(2026, 3, 28);
        LocalDate springForward = LocalDate.of(2026, 3, 29);
        PlantSurplusClaim march = month(MARCH)
                .estimated(springForward)
                .hour(springForward, 0, null)
                .hour(springForward, 1, 12.3456)
                .hour(springForward, 2, 0.0005)
                .hour(springForward, 3, 1234567.5)
                .hour(springForward, 4, 1e-7)
                .hour(springForward, 5, 0d)
                .build();
        PlantSurplusClaim claim = new PlantSurplusClaim(march.month(), march.zone(), march.plant(), march.cil(),
                march.regulatoryCode(), null, null, march.days().subList(27, 29), null);

        SurplusClaimFile file = format.write(claim);

        String expected = "﻿" + HEADER + "\r\n"
                + "28/03/2026;R;28,000;28,010;28,020;28,030;28,040;28,050;28,060;28,070;28,080;28,090;28,100;28,110;"
                + "28,120;28,130;28,140;28,150;28,160;28,170;28,180;28,190;28,200;28,210;28,220;28,230;\r\n"
                + "29/03/2026;E;;12,346;0,001;1234567,500;0,000;0,000;29,060;29,070;29,080;29,090;29,100;29,110;"
                + "29,120;29,130;29,140;29,150;29,160;29,170;29,180;29,190;29,200;29,210;29,220;;\r\n";
        assertArrayEquals(expected.getBytes(StandardCharsets.UTF_8), file.content());
        assertArrayEquals(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF}, Arrays.copyOf(file.content(), 3));
    }

    @Test
    @DisplayName("SCL-019 every day of the month is a row of 27 fields, the 25 hours of the day the clocks go back all filled")
    void everyDayIsARowOf27Fields() {
        LocalDate fallBack = LocalDate.of(2026, 10, 25);

        List<String> lines = lines(format.write(month(OCTOBER).build()));

        assertEquals(HEADER, lines.get(0));
        assertEquals(32, lines.size());
        for (int day = 1; day <= 31; day++) {
            String[] fields = lines.get(day).split(";", -1);
            assertEquals(27, fields.length, lines.get(day));
            assertEquals(String.format("%02d/10/2026", day), fields[0]);
            assertEquals("R", fields[1]);
            boolean twentyFiveHours = OCTOBER.atDay(day).equals(fallBack);
            assertEquals(twentyFiveHours ? "25,240" : "", fields[26], lines.get(day));
            assertEquals(String.format("%d,230", day), fields[25]);
        }
    }

    @Test
    @DisplayName("SCL-019 the file is text/csv named after the plant's regulatory code and the month, else after the plant's id")
    void theFileIsNamedAfterTheRegulatoryCode() {
        SurplusClaimFile withCode = format.write(month(MARCH).regulatoryCode("ES0031300000000000000AB1FA000").build());
        SurplusClaimFile withoutCode = format.write(month(MARCH).regulatoryCode(null).cil(null).build());
        SurplusClaimFile blankCode = format.write(month(MARCH).regulatoryCode(" ").build());

        assertEquals(SurplusClaimFileType.GENERIC, format.type());
        assertEquals("excedentes_ES0031300000000000000AB1FA000_2026-03.csv", withCode.filename());
        assertEquals("excedentes_" + PLANT_ID + "_2026-03.csv", withoutCode.filename());
        assertEquals("excedentes_" + PLANT_ID + "_2026-03.csv", blankCode.filename());
        assertEquals("text/csv;charset=UTF-8", withCode.contentType());
    }

    @Test
    @DisplayName("SCL-019 every line ends in CRLF, never in a bare LF")
    void everyLineEndsInCrlf() {
        String content = new String(format.write(month(MARCH).build()).content(), StandardCharsets.UTF_8);

        assertTrue(content.endsWith("\r\n"));
        assertEquals(content.split("\n", -1).length, content.split("\r\n", -1).length);
    }

    private static List<String> lines(SurplusClaimFile file) {
        String content = new String(file.content(), StandardCharsets.UTF_8).substring(1);
        return Arrays.asList(content.split("\r\n"));
    }
}

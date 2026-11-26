package org.lucoenergia.conluz.infrastructure.production.plant.surplusclaim.file;

import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFDataValidation;
import org.apache.poi.xssf.usermodel.XSSFName;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.production.plant.surplusclaim.file.SurplusClaimCilRequiredException;
import org.lucoenergia.conluz.domain.production.plant.surplusclaim.file.SurplusClaimFile;
import org.lucoenergia.conluz.domain.production.plant.surplusclaim.file.SurplusClaimFileType;
import org.springframework.core.io.ClassPathResource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lucoenergia.conluz.infrastructure.production.plant.surplusclaim.file.SurplusClaimFileTestClaims.CIL;
import static org.lucoenergia.conluz.infrastructure.production.plant.surplusclaim.file.SurplusClaimFileTestClaims.defaultValue;
import static org.lucoenergia.conluz.infrastructure.production.plant.surplusclaim.file.SurplusClaimFileTestClaims.month;

/**
 * The Endesa file read back with POI: unzipped, opened, and compared with the bundled template.
 */
class EndesaSurplusClaimFileFormatTest {

    private static final YearMonth MARCH = YearMonth.of(2026, 3);
    private static final YearMonth SEPTEMBER = YearMonth.of(2026, 9);
    private static final YearMonth OCTOBER = YearMonth.of(2026, 10);
    private static final LocalDate SPRING_FORWARD = LocalDate.of(2026, 3, 29);
    private static final LocalDate FALL_BACK = LocalDate.of(2026, 10, 25);
    private static final int DATE_COLUMN = 3;
    private static final int FIRST_HOUR_COLUMN = 4;
    private static final int LAST_COLUMN = 28;

    private final EndesaSurplusClaimFileFormat format = new EndesaSurplusClaimFileFormat();

    @Test
    @DisplayName("SCL-018 the file is a ZIP named after the CIL and the month, holding only the workbook")
    void theFileIsAZipHoldingOnlyTheWorkbook() throws IOException {
        SurplusClaimFile file = format.write(month(SEPTEMBER).build());

        assertEquals(SurplusClaimFileType.ENDESA, format.type());
        assertEquals("reclamacionMedidas_" + CIL + "_2026-09.zip", file.filename());
        assertEquals("application/zip", file.contentType());
        List<String> entries = new ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(file.content()))) {
            for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                entries.add(entry.getName());
            }
        }
        assertEquals(List.of("reclamacionMedidas_" + CIL + "_2026-09.xlsx"), entries);
    }

    @Test
    @DisplayName("SCL-016 a plant without a CIL is refused")
    void aPlantWithoutACilIsRefused() {
        assertThrows(SurplusClaimCilRequiredException.class, () -> format.write(month(SEPTEMBER).cil(null).build()));
    }

    @Test
    @DisplayName("SCL-017 the template keeps its sheets, and Carga gets one row per day from row 2 with the CIL, AS, the state and the date")
    void cargaGetsOneRowPerDay() throws IOException {
        LocalDate estimated = SEPTEMBER.atDay(10);

        try (XSSFWorkbook workbook = workbook(format.write(month(SEPTEMBER).estimated(estimated).build()))) {
            assertEquals(List.of("Instrucciones", "Carga", "Hoja3"), sheetNames(workbook));
            XSSFSheet carga = workbook.getSheet("Carga");
            assertEquals("CIL", carga.getRow(0).getCell(0).getStringCellValue());
            assertEquals("HOR25", carga.getRow(0).getCell(LAST_COLUMN).getStringCellValue());
            for (int day = 1; day <= 30; day++) {
                XSSFRow row = carga.getRow(day);
                LocalDate date = SEPTEMBER.atDay(day);
                assertEquals(CIL, row.getCell(0).getStringCellValue());
                assertEquals("AS", row.getCell(1).getStringCellValue());
                assertEquals(date.equals(estimated) ? "E" : "R", row.getCell(2).getStringCellValue(), date.toString());
                XSSFCell dateCell = row.getCell(DATE_COLUMN);
                assertEquals(CellType.NUMERIC, dateCell.getCellType());
                assertTrue(DateUtil.isCellDateFormatted(dateCell));
                assertEquals(date.atStartOfDay(), dateCell.getLocalDateTimeCellValue());
                assertEquals("dd/mm/yyyy", dateCell.getCellStyle().getDataFormatString());
            }
            assertEmpty(carga.getRow(31));
        }
    }

    @Test
    @DisplayName("SCL-017 the hours are numbers rounded half-up to 2 decimals, a null hour 0 and an hour the day does not have empty")
    void theHoursAreRoundedAndOnlyTheExistingOnesWritten() throws IOException {
        LocalDate first = MARCH.atDay(1);
        LocalDate second = MARCH.atDay(2);

        try (XSSFWorkbook workbook = workbook(format.write(month(MARCH)
                .hour(first, 12, 12.3456)
                .hour(first, 13, 0.005)
                .hour(first, 14, 0.0049)
                .estimated(second)
                .hour(second, 0, null)
                .hour(second, 1, 0d)
                .build()))) {
            XSSFSheet carga = workbook.getSheet("Carga");

            assertEquals(12.35, hour(carga, first, 12).getNumericCellValue());
            assertEquals(0.01, hour(carga, first, 13).getNumericCellValue());
            assertEquals(0.0, hour(carga, first, 14).getNumericCellValue());
            assertEquals(1.23, hour(carga, first, 23).getNumericCellValue());
            assertEquals(CellType.BLANK, hour(carga, first, 24).getCellType());

            assertEquals("E", carga.getRow(2).getCell(2).getStringCellValue());
            assertEquals(CellType.NUMERIC, hour(carga, second, 0).getCellType());
            assertEquals(0.0, hour(carga, second, 0).getNumericCellValue());
            assertEquals(CellType.NUMERIC, hour(carga, second, 1).getCellType());
            assertEquals(0.0, hour(carga, second, 1).getNumericCellValue());
            assertEquals(2.02, hour(carga, second, 2).getNumericCellValue());
        }
    }

    @Test
    @DisplayName("SCL-017 the days the clocks change get 23 and 25 hours, the others 24")
    void theDaysTheClocksChangeGetTheirHours() throws IOException {
        try (XSSFWorkbook march = workbook(format.write(month(MARCH).build()));
             XSSFWorkbook october = workbook(format.write(month(OCTOBER).build()))) {
            XSSFSheet springForward = march.getSheet("Carga");
            for (int hour = 0; hour < 23; hour++) {
                assertEquals(round2(defaultValue(SPRING_FORWARD, hour)),
                        hour(springForward, SPRING_FORWARD, hour).getNumericCellValue());
            }
            assertEquals(CellType.BLANK, hour(springForward, SPRING_FORWARD, 23).getCellType());
            assertEquals(CellType.BLANK, hour(springForward, SPRING_FORWARD, 24).getCellType());

            XSSFSheet fallBack = october.getSheet("Carga");
            for (int hour = 0; hour < 25; hour++) {
                assertEquals(round2(defaultValue(FALL_BACK, hour)), hour(fallBack, FALL_BACK, hour).getNumericCellValue());
            }
            assertEquals(CellType.NUMERIC, hour(fallBack, FALL_BACK.minusDays(1), 23).getCellType());
            assertEquals(CellType.BLANK, hour(fallBack, FALL_BACK.minusDays(1), 24).getCellType());
        }
    }

    @Test
    @DisplayName("SCL-017 the date shows as dd/mm/yyyy and the hours with 2 decimals, every other attribute of the template's cell kept")
    void theWrittenCellsKeepTheTemplateStyleWithTheirFormat() throws IOException {
        try (XSSFWorkbook template = template();
             XSSFWorkbook workbook = workbook(format.write(month(MARCH).build()))) {
            XSSFSheet carga = workbook.getSheet("Carga");
            XSSFCellStyle templateStyle = template.getSheet("Carga").getRow(1).getCell(DATE_COLUMN).getCellStyle();
            assertEquals("General", templateStyle.getDataFormatString());
            assertEquals(templateStyle.getDataFormatString(),
                    template.getSheet("Carga").getRow(1).getCell(FIRST_HOUR_COLUMN).getCellStyle()
                            .getDataFormatString());

            for (int row = 1; row <= 31; row++) {
                XSSFCellStyle date = carga.getRow(row).getCell(DATE_COLUMN).getCellStyle();
                assertEquals("dd/mm/yyyy", date.getDataFormatString());
                assertSameLook(templateStyle, date);
                for (int column = FIRST_HOUR_COLUMN; column <= LAST_COLUMN; column++) {
                    XSSFCell cell = carga.getRow(row).getCell(column);
                    if (cell.getCellType() == CellType.BLANK) {
                        assertEquals("General", cell.getCellStyle().getDataFormatString());
                    } else {
                        assertEquals("0.00", cell.getCellStyle().getDataFormatString());
                    }
                    assertSameLook(templateStyle, cell.getCellStyle());
                }
                assertSameLook(templateStyle, carga.getRow(row).getCell(0).getCellStyle());
                assertEquals("General", carga.getRow(row).getCell(0).getCellStyle().getDataFormatString());
            }
        }
    }

    @Test
    @DisplayName("SCL-017 the measure type and state validations cover rows 2 to 31 in a 30-day month and 2 to 32 in a 31-day one")
    void theValidationsCoverEveryWrittenRow() throws IOException {
        try (XSSFWorkbook september = workbook(format.write(month(SEPTEMBER).build()));
             XSSFWorkbook march = workbook(format.write(month(MARCH).build()));
             XSSFWorkbook february = workbook(format.write(month(YearMonth.of(2026, 2)).build()))) {
            assertEquals(List.of("B2:B31=lista1", "C2:C31=lista2"), validations(september));
            assertEquals(List.of("B2:B32=lista1", "C2:C32=lista2"), validations(march));
            assertEquals(List.of("B2:B31=lista1", "C2:C31=lista2"), validations(february));

            XSSFDataValidation measureType = (XSSFDataValidation) march.getSheet("Carga").getDataValidations().get(0);
            assertTrue(measureType.getEmptyCellAllowed());
            assertEquals("lista1", measureType.getValidationConstraint().getFormula1());
        }
    }

    @Test
    @DisplayName("SCL-017 only Carga changes: Instrucciones, Hoja3 and the named lists are the template's")
    void theOtherSheetsAreTheTemplates() throws IOException {
        try (XSSFWorkbook template = template();
             XSSFWorkbook workbook = workbook(format.write(month(MARCH).build()))) {
            for (String sheet : List.of("Instrucciones", "Hoja3")) {
                assertEquals(describe(template.getSheet(sheet)), describe(workbook.getSheet(sheet)), sheet);
            }
            assertEquals(names(template), names(workbook));
        }
    }

    private static XSSFCell hour(XSSFSheet carga, LocalDate date, int hour) {
        return carga.getRow(date.getDayOfMonth()).getCell(FIRST_HOUR_COLUMN + hour);
    }

    private static double round2(double value) {
        return SurplusClaimFileValues.round(value, 2).doubleValue();
    }

    private static void assertEmpty(XSSFRow row) {
        if (row == null) {
            return;
        }
        row.forEach(cell -> assertEquals(CellType.BLANK, cell.getCellType(), cell.getAddress().formatAsString()));
    }

    private static void assertSameLook(XSSFCellStyle expected, XSSFCellStyle actual) {
        assertEquals(expected.getBorderTop(), actual.getBorderTop());
        assertEquals(expected.getBorderBottom(), actual.getBorderBottom());
        assertEquals(expected.getBorderLeft(), actual.getBorderLeft());
        assertEquals(expected.getBorderRight(), actual.getBorderRight());
        assertEquals(expected.getFontIndex(), actual.getFontIndex());
        assertEquals(expected.getFillPattern(), actual.getFillPattern());
        assertEquals(expected.getAlignment(), actual.getAlignment());
    }

    private static List<String> validations(XSSFWorkbook workbook) {
        return workbook.getSheet("Carga").getDataValidations().stream()
                .map(validation -> rangesOf(validation.getRegions().getCellRangeAddresses()) + "="
                        + validation.getValidationConstraint().getFormula1())
                .sorted()
                .toList();
    }

    private static String rangesOf(CellRangeAddress[] ranges) {
        return String.join(" ", Arrays.stream(ranges).map(CellRangeAddress::formatAsString).toList());
    }

    private static List<String> sheetNames(XSSFWorkbook workbook) {
        List<String> names = new ArrayList<>();
        workbook.sheetIterator().forEachRemaining(sheet -> names.add(sheet.getSheetName()));
        return names;
    }

    private static List<String> names(XSSFWorkbook workbook) {
        return workbook.getAllNames().stream()
                .map(name -> ((XSSFName) name).getNameName() + "=" + name.getRefersToFormula())
                .sorted()
                .toList();
    }

    /**
     * Every cell of the sheet with its value and the look of its style, its merged regions and its column widths.
     */
    private static List<String> describe(XSSFSheet sheet) {
        List<String> description = new ArrayList<>();
        sheet.forEach(row -> row.forEach(cell -> {
            XSSFCellStyle style = (XSSFCellStyle) cell.getCellStyle();
            description.add(cell.getAddress().formatAsString() + "|" + cell.getCellType() + "|" + cell + "|"
                    + style.getDataFormatString() + "|" + style.getFont().getFontName() + "|"
                    + style.getFont().getFontHeightInPoints() + "|" + style.getFont().getBold() + "|"
                    + style.getFillPattern() + "|" + style.getFillForegroundColor() + "|" + style.getBorderBottom()
                    + "|" + style.getAlignment() + "|" + style.getWrapText() + "|" + row.getHeight());
        }));
        sheet.getMergedRegions().stream()
                .sorted(Comparator.comparing(CellRangeAddress::formatAsString))
                .forEach(region -> description.add("merged " + region.formatAsString()));
        for (int column = 0; column < 30; column++) {
            description.add("width " + column + "=" + sheet.getColumnWidth(column));
        }
        return description;
    }

    private static XSSFWorkbook template() throws IOException {
        try (InputStream template = new ClassPathResource(EndesaSurplusClaimFileFormat.TEMPLATE).getInputStream()) {
            return new XSSFWorkbook(template);
        }
    }

    private static XSSFWorkbook workbook(SurplusClaimFile file) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(file.content()))) {
            ZipEntry entry = zip.getNextEntry();
            assertNotNull(entry);
            ByteArrayOutputStream workbook = new ByteArrayOutputStream();
            zip.transferTo(workbook);
            assertNull(zip.getNextEntry());
            assertArrayEquals(new byte[]{'P', 'K'}, Arrays.copyOf(workbook.toByteArray(), 2));
            return new XSSFWorkbook(new ByteArrayInputStream(workbook.toByteArray()));
        }
    }
}

package org.lucoenergia.conluz.infrastructure.production.plant.surplusclaim.file;

import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.lucoenergia.conluz.domain.production.plant.surplusclaim.PlantSurplusClaim;
import org.lucoenergia.conluz.domain.production.plant.surplusclaim.SurplusClaimDay;
import org.lucoenergia.conluz.domain.production.plant.surplusclaim.SurplusClaimHour;
import org.lucoenergia.conluz.domain.production.plant.surplusclaim.file.SurplusClaimCilRequiredException;
import org.lucoenergia.conluz.domain.production.plant.surplusclaim.file.SurplusClaimFile;
import org.lucoenergia.conluz.domain.production.plant.surplusclaim.file.SurplusClaimFileFormat;
import org.lucoenergia.conluz.domain.production.plant.surplusclaim.file.SurplusClaimFileType;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.CTDataValidation;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.CTDataValidations;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * The claim in Endesa's own template, {@code reclamacionMedidas.xlsx}, zipped as its instructions ask.
 *
 * <p>The template is read from the classpath and filled in memory on every call; the resource is never written. Only
 * the {@code Carga} sheet changes: one row per local day from row 2, with the CIL, the measure type {@code AS}, the
 * day's state, the date and its hours. An hour without any record is written as 0, because the cell must be
 * numeric, and an hour the day does not have is left empty. The validations of the measure type and the state are
 * extended to every written row, since the template only validates 30.
 */
@Component
public class EndesaSurplusClaimFileFormat implements SurplusClaimFileFormat {

    static final String TEMPLATE = "surplus-claim/endesa/reclamacionMedidas.xlsx";
    static final String CONTENT_TYPE = "application/zip";

    private static final String SHEET = "Carga";
    private static final String MEASURE_TYPE = "AS";
    private static final String DATE_FORMAT = "dd/mm/yyyy";
    private static final String HOUR_FORMAT = "0.00";
    private static final int DECIMALS = 2;
    private static final int FIRST_ROW = 1;
    private static final int TEMPLATE_LAST_ROW = 30;
    private static final int CIL_COLUMN = 0;
    private static final int MEASURE_TYPE_COLUMN = 1;
    private static final int STATE_COLUMN = 2;
    private static final int DATE_COLUMN = 3;
    private static final int FIRST_HOUR_COLUMN = 4;
    private static final int HOURS = 25;
    private static final Set<Integer> VALIDATED_COLUMNS = Set.of(MEASURE_TYPE_COLUMN, STATE_COLUMN);

    @Override
    public SurplusClaimFileType type() {
        return SurplusClaimFileType.ENDESA;
    }

    @Override
    public SurplusClaimFile write(PlantSurplusClaim claim) {
        String cil = claim.cil();
        if (cil == null) {
            throw new SurplusClaimCilRequiredException();
        }
        String name = "reclamacionMedidas_" + cil + "_" + claim.month();
        return new SurplusClaimFile(name + ".zip", CONTENT_TYPE, zip(name + ".xlsx", fill(claim, cil)));
    }

    private static byte[] fill(PlantSurplusClaim claim, String cil) {
        try (InputStream template = new ClassPathResource(TEMPLATE).getInputStream();
             XSSFWorkbook workbook = new XSSFWorkbook(template);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XSSFSheet sheet = workbook.getSheet(SHEET);
            Styles styles = new Styles(workbook, sheet.getRow(TEMPLATE_LAST_ROW));
            List<SurplusClaimDay> days = claim.days();
            for (int i = 0; i < days.size(); i++) {
                writeDay(sheet, FIRST_ROW + i, cil, days.get(i), styles);
            }
            extendValidations(sheet, FIRST_ROW + days.size() - 1);
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void writeDay(XSSFSheet sheet, int rowIndex, String cil, SurplusClaimDay day, Styles styles) {
        XSSFRow row = sheet.getRow(rowIndex) == null ? sheet.createRow(rowIndex) : sheet.getRow(rowIndex);
        cell(row, CIL_COLUMN, styles).setCellValue(cil);
        cell(row, MEASURE_TYPE_COLUMN, styles).setCellValue(MEASURE_TYPE);
        cell(row, STATE_COLUMN, styles).setCellValue(SurplusClaimFileValues.state(day.state()));
        XSSFCell date = cell(row, DATE_COLUMN, styles);
        date.setCellValue(day.date());
        date.setCellStyle(styles.date(date.getCellStyle()));

        List<SurplusClaimHour> hours = day.hours();
        for (int hour = 0; hour < HOURS; hour++) {
            XSSFCell cell = cell(row, FIRST_HOUR_COLUMN + hour, styles);
            if (hour < hours.size()) {
                Double surplusKWh = hours.get(hour).surplusKWh();
                cell.setCellValue(surplusKWh == null ? 0 : SurplusClaimFileValues.round(surplusKWh, DECIMALS)
                        .doubleValue());
                cell.setCellStyle(styles.hour(cell.getCellStyle()));
            }
        }
    }

    /**
     * The cell of the template, or a new one styled as the template's last row styles that column.
     */
    private static XSSFCell cell(XSSFRow row, int column, Styles styles) {
        XSSFCell cell = row.getCell(column);
        if (cell == null) {
            cell = row.createCell(column);
            cell.setCellStyle(styles.template(column));
        }
        return cell;
    }

    /**
     * Extends the measure type and state validations down to the last written row, keeping everything else they
     * declare. They never shrink below the template's own rows.
     */
    private static void extendValidations(XSSFSheet sheet, int lastRow) {
        CTDataValidations validations = sheet.getCTWorksheet().getDataValidations();
        if (validations == null) {
            return;
        }
        for (CTDataValidation validation : validations.getDataValidationArray()) {
            List<String> extended = ((List<?>) validation.getSqref()).stream()
                    .map(Object::toString)
                    .map(reference -> extend(reference, lastRow))
                    .toList();
            validation.setSqref(extended);
        }
    }

    private static String extend(String reference, int lastRow) {
        CellRangeAddress range = CellRangeAddress.valueOf(reference);
        boolean dataColumn = range.getFirstColumn() == range.getLastColumn()
                && VALIDATED_COLUMNS.contains(range.getFirstColumn());
        if (dataColumn && range.getFirstRow() == FIRST_ROW && range.getLastRow() < lastRow) {
            range.setLastRow(lastRow);
        }
        return range.formatAsString();
    }

    private static byte[] zip(String entryName, byte[] content) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry(entryName));
            zip.write(content);
            zip.closeEntry();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    /**
     * The styles of the written cells: the template's own, read before anything is written, and one copy of each
     * carrying the date or the hour format, created once per workbook.
     */
    private static final class Styles {

        private final XSSFWorkbook workbook;
        private final Map<Integer, XSSFCellStyle> templateByColumn = new HashMap<>();
        private final Map<Short, XSSFCellStyle> dateBySource = new HashMap<>();
        private final Map<Short, XSSFCellStyle> hourBySource = new HashMap<>();

        private Styles(XSSFWorkbook workbook, XSSFRow templateLastRow) {
            this.workbook = workbook;
            for (int column = CIL_COLUMN; column < FIRST_HOUR_COLUMN + HOURS; column++) {
                XSSFCell cell = templateLastRow.getCell(column);
                templateByColumn.put(column, cell == null ? workbook.getCellStyleAt(0) : cell.getCellStyle());
            }
        }

        private XSSFCellStyle template(int column) {
            return templateByColumn.get(column);
        }

        private XSSFCellStyle date(XSSFCellStyle source) {
            return dateBySource.computeIfAbsent(source.getIndex(), index -> withFormat(source, DATE_FORMAT));
        }

        private XSSFCellStyle hour(XSSFCellStyle source) {
            return hourBySource.computeIfAbsent(source.getIndex(), index -> withFormat(source, HOUR_FORMAT));
        }

        private XSSFCellStyle withFormat(XSSFCellStyle source, String format) {
            XSSFCellStyle style = workbook.createCellStyle();
            style.cloneStyleFrom(source);
            style.setDataFormat(workbook.createDataFormat().getFormat(format));
            return style;
        }
    }
}

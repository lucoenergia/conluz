package org.lucoenergia.conluz.infrastructure.datadis.csv;

import org.lucoenergia.conluz.domain.consumption.datadis.DatadisConsumption;
import org.lucoenergia.conluz.domain.datadis.DatadisHourlyRecord;
import org.lucoenergia.conluz.infrastructure.shared.time.DateConverter;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.List;

/**
 * Stored hourly Datadis consumption records as a CSV a spreadsheet opens as it is in a Spanish locale: UTF-8 with
 * a BOM, {@code ;} as separator and CRLF line ends, one line per record in the order given, under a header of the
 * Datadis field names. It knows nothing of the scope the records were read for.
 *
 * <p>{@code date} and {@code time} are the local date and start time of the record's hour, derived from its stored
 * instant as every hourly read labels an hour ({@link DateConverter#formatDate}, {@link DateConverter#formatTime}):
 * Datadis's own labels, which name the end of the hour, are not stored. Every other field is the stored value: text
 * unchanged, a number unrounded as the shortest decimal that identifies the stored float, in plain notation with a
 * decimal comma, and a null as an empty field.
 */
public final class DatadisHourlyConsumptionCsv {

    public static final String CONTENT_TYPE = "text/csv;charset=UTF-8";
    static final String HEADER = "cups;date;time;consumptionKWh;obtainMethod;surplusEnergyKWh;"
            + "generationEnergyKWh;selfConsumptionEnergyKWh";

    private static final String BOM = "﻿";
    private static final char SEPARATOR = ';';
    private static final String LINE_END = "\r\n";

    private DatadisHourlyConsumptionCsv() {
    }

    /**
     * Writes the records, in the order given, with their date and time in {@code zone}.
     */
    public static byte[] write(List<DatadisHourlyRecord<DatadisConsumption>> records, ZoneId zone) {
        StringBuilder csv = new StringBuilder(BOM).append(HEADER).append(LINE_END);
        for (DatadisHourlyRecord<DatadisConsumption> hourlyRecord : records) {
            DatadisConsumption consumption = hourlyRecord.record();
            csv.append(text(consumption.getCups())).append(SEPARATOR)
                    .append(DateConverter.formatDate(hourlyRecord.instant(), zone)).append(SEPARATOR)
                    .append(DateConverter.formatTime(hourlyRecord.instant(), zone)).append(SEPARATOR)
                    .append(number(consumption.getConsumptionKWh())).append(SEPARATOR)
                    .append(text(consumption.getObtainMethod())).append(SEPARATOR)
                    .append(number(consumption.getSurplusEnergyKWh())).append(SEPARATOR)
                    .append(number(consumption.getGenerationEnergyKWh())).append(SEPARATOR)
                    .append(number(consumption.getSelfConsumptionEnergyKWh()))
                    .append(LINE_END);
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * {@link Float#toString} gives the shortest decimal that identifies the float, possibly in exponential
     * notation; {@link BigDecimal#toPlainString} writes it out without the exponent.
     */
    static String number(Float value) {
        if (value == null) {
            return "";
        }
        return new BigDecimal(Float.toString(value)).stripTrailingZeros().toPlainString().replace('.', ',');
    }

    /**
     * A text field unchanged, quoted with its quotes doubled only when it holds a separator, a quote or a line
     * end.
     */
    static String text(String value) {
        if (value == null) {
            return "";
        }
        if (value.indexOf(SEPARATOR) < 0 && value.indexOf('"') < 0 && value.indexOf('\r') < 0
                && value.indexOf('\n') < 0) {
            return value;
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }
}

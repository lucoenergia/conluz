package org.lucoenergia.conluz.infrastructure.production.plant.surplusclaim.file;

import org.lucoenergia.conluz.domain.production.plant.surplusclaim.PlantSurplusClaim;
import org.lucoenergia.conluz.domain.production.plant.surplusclaim.SurplusClaimDay;
import org.lucoenergia.conluz.domain.production.plant.surplusclaim.SurplusClaimHour;
import org.lucoenergia.conluz.domain.production.plant.surplusclaim.file.SurplusClaimFile;
import org.lucoenergia.conluz.domain.production.plant.surplusclaim.file.SurplusClaimFileFormat;
import org.lucoenergia.conluz.domain.production.plant.surplusclaim.file.SurplusClaimFileType;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * The claim as a CSV matrix, one row per local day: UTF-8 with a BOM, {@code ;} as separator and CRLF line ends, so
 * a spreadsheet opens it as it is in a Spanish locale.
 *
 * <p>Every row has 27 fields: the date, the day's state and 25 hours. An hour is written with 3 decimals and a
 * decimal comma. An hour without any record and an hour the day does not have are both left empty: the state tells
 * them apart, since a real day has a record for every hour it has.
 */
@Component
public class GenericSurplusClaimFileFormat implements SurplusClaimFileFormat {

    static final String CONTENT_TYPE = "text/csv;charset=UTF-8";

    private static final int HOURS = 25;
    private static final int DECIMALS = 3;
    private static final String SEPARATOR = ";";
    private static final String LINE_END = "\r\n";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Override
    public SurplusClaimFileType type() {
        return SurplusClaimFileType.GENERIC;
    }

    @Override
    public SurplusClaimFile write(PlantSurplusClaim claim) {
        StringBuilder csv = new StringBuilder("﻿").append("fecha").append(SEPARATOR).append("estado");
        for (int hour = 1; hour <= HOURS; hour++) {
            csv.append(SEPARATOR).append("HOR").append(hour);
        }
        csv.append(LINE_END);
        for (SurplusClaimDay day : claim.days()) {
            csv.append(DATE.format(day.date())).append(SEPARATOR).append(SurplusClaimFileValues.state(day.state()));
            List<SurplusClaimHour> hours = day.hours();
            for (int hour = 0; hour < HOURS; hour++) {
                csv.append(SEPARATOR);
                Double surplusKWh = hour < hours.size() ? hours.get(hour).surplusKWh() : null;
                if (surplusKWh != null) {
                    csv.append(SurplusClaimFileValues.round(surplusKWh, DECIMALS).toPlainString().replace('.', ','));
                }
            }
            csv.append(LINE_END);
        }

        String code = claim.regulatoryCode() == null || claim.regulatoryCode().isBlank()
                ? claim.plant().id().toString()
                : claim.regulatoryCode();
        return new SurplusClaimFile("excedentes_" + code + "_" + claim.month() + ".csv", CONTENT_TYPE,
                csv.toString().getBytes(StandardCharsets.UTF_8));
    }
}

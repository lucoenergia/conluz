package org.lucoenergia.conluz.infrastructure.shared.time;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class StringToLocalDateConverter {

    public static LocalDate convert(String dateString) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd");
        return LocalDate.parse(dateString, formatter);
    }
}

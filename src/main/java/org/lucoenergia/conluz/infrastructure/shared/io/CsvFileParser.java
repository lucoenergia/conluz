package org.lucoenergia.conluz.infrastructure.shared.io;

import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;
import org.springframework.stereotype.Component;

import java.io.*;
import java.util.List;

@Component
public class CsvFileParser {

    /**
     * Parses the content of the given InputStream as CSV and maps each row to an instance of the specified class type.
     *
     * @param <T> the type of the objects to parse from the CSV
     * @param inputStream the InputStream containing the CSV data
     * @param clazzToParse the class type to map each row of CSV data to
     * @return a list of objects parsed and mapped from the CSV data
     * @throws IOException if an I/O error occurs while reading from the InputStream
     */
    public <T> List<T> parse(InputStream inputStream, Class<T> clazzToParse) throws IOException {
        try (Reader reader = new BufferedReader(new InputStreamReader(inputStream))) {

            // create csv bean reader
            CsvToBean<T> csvToBean = new CsvToBeanBuilder<T>(reader)
                    .withType(clazzToParse)
                    .withIgnoreLeadingWhiteSpace(true)
                    .build();

            return csvToBean.parse();
        }
    }
}

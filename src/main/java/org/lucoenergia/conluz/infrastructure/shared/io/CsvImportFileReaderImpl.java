package org.lucoenergia.conluz.infrastructure.shared.io;

import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.RFC4180ParserBuilder;
import com.opencsv.exceptions.CsvValidationException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The checks run in this order, and the first that fails refuses the file: the encoding, the separator, the CSV
 * syntax, unknown headers, duplicated headers, missing required headers, and the number of rows. The cells are kept as
 * they are, untrimmed; headers are trimmed and matched case-sensitively.
 *
 * <p>The encoding is told by a fixed rule, without guessing from the text: a file starting with the UTF-8 byte order
 * mark is UTF-8; one starting with a UTF-16 byte order mark is refused; any other is UTF-8 if it is valid UTF-8, and
 * Windows-1252 otherwise. Spanish Excel saves "CSV UTF-8" with the byte order mark, and its default "CSV" in
 * Windows-1252, without one. Windows-1252 text with accents is practically never valid UTF-8: UTF-8 would require
 * each accented letter to be followed by one or more of the bytes Windows-1252 uses for symbols such as € or ™.</p>
 */
@Component
public class CsvImportFileReaderImpl implements CsvImportFileReader {

    private static final byte[] UTF_8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private static final byte[] UTF_16_BE_BOM = {(byte) 0xFE, (byte) 0xFF};
    private static final byte[] UTF_16_LE_BOM = {(byte) 0xFF, (byte) 0xFE};

    @Override
    public CsvImportFile read(byte[] content, CsvImportLayout layout) {
        CsvImportEncoding encoding = encodingOf(content);
        String text = decode(content, encoding);
        char separator = separatorOf(firstLine(text));

        try (CSVReader reader = new CSVReaderBuilder(new StringReader(text))
                .withCSVParser(new RFC4180ParserBuilder().withSeparator(separator).build())
                .build()) {
            String[] header = reader.readNext();
            if (header == null) {
                throw new CsvImportFileRefusedException(CsvImportFileProblem.MISSING_HEADERS,
                        layout.requiredColumns());
            }
            List<String> columns = columnsOf(header, layout);

            List<CsvImportRow> rows = new ArrayList<>();
            long linesBefore = reader.getLinesRead();
            String[] cells;
            while ((cells = reader.readNext()) != null) {
                int line = (int) linesBefore + 1;
                linesBefore = reader.getLinesRead();
                if (allBlank(cells)) {
                    continue;
                }
                // Refused as soon as the limit is passed, without reading the rest of the file
                if (rows.size() == layout.maxRows()) {
                    throw new CsvImportFileRefusedException(CsvImportFileProblem.TOO_MANY_ROWS);
                }
                rows.add(rowOf(line, columns, cells));
            }
            return new CsvImportFile(encoding, rows);
        } catch (CsvValidationException | IOException e) {
            // Reading from a string fails only on the CSV syntax, for example a quoted value that is never closed
            throw new CsvImportFileRefusedException(CsvImportFileProblem.MALFORMED_FILE);
        }
    }

    private static CsvImportEncoding encodingOf(byte[] content) {
        if (startsWith(content, UTF_8_BOM)) {
            return CsvImportEncoding.UTF_8;
        }
        // Excel's "Unicode text" is UTF-16 and separated by tabs: it is not a CSV file
        if (startsWith(content, UTF_16_BE_BOM) || startsWith(content, UTF_16_LE_BOM)) {
            throw new CsvImportFileRefusedException(CsvImportFileProblem.INVALID_ENCODING);
        }
        return isValid(content, CsvImportEncoding.UTF_8) ? CsvImportEncoding.UTF_8 : CsvImportEncoding.WINDOWS_1252;
    }

    /**
     * Strict: a byte the encoding does not define, such as {@code 0x81} in Windows-1252, refuses the file rather
     * than being replaced.
     */
    private static String decode(byte[] content, CsvImportEncoding encoding) {
        // The byte order mark is not part of the first header
        int offset = startsWith(content, UTF_8_BOM) ? UTF_8_BOM.length : 0;
        try {
            return decoderOf(encoding).decode(ByteBuffer.wrap(content, offset, content.length - offset)).toString();
        } catch (CharacterCodingException e) {
            throw new CsvImportFileRefusedException(CsvImportFileProblem.INVALID_ENCODING);
        }
    }

    private static boolean isValid(byte[] content, CsvImportEncoding encoding) {
        try {
            decoderOf(encoding).decode(ByteBuffer.wrap(content));
            return true;
        } catch (CharacterCodingException e) {
            return false;
        }
    }

    private static CharsetDecoder decoderOf(CsvImportEncoding encoding) {
        return encoding.charset().newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
    }

    private static boolean startsWith(byte[] content, byte[] prefix) {
        return content.length >= prefix.length
                && Arrays.equals(content, 0, prefix.length, prefix, 0, prefix.length);
    }

    private static String firstLine(String text) {
        int end = text.indexOf('\n');
        return end < 0 ? text : text.substring(0, end);
    }

    /**
     * No column name holds either separator, so a header line with only one of them is separated by it, and one with
     * both cannot be told apart. A header line with neither holds a single column, which the header checks refuse.
     */
    private static char separatorOf(String headerLine) {
        boolean comma = headerLine.indexOf(',') >= 0;
        boolean semicolon = headerLine.indexOf(';') >= 0;
        if (comma && semicolon) {
            throw new CsvImportFileRefusedException(CsvImportFileProblem.AMBIGUOUS_SEPARATOR);
        }
        return semicolon ? ';' : ',';
    }

    private static List<String> columnsOf(String[] header, CsvImportLayout layout) {
        List<String> columns = new ArrayList<>();
        for (String name : header) {
            columns.add(name.strip());
        }

        Set<String> unknown = new LinkedHashSet<>();
        for (String column : columns) {
            if (!layout.columns().contains(column)) {
                unknown.add(column);
            }
        }
        if (!unknown.isEmpty()) {
            throw new CsvImportFileRefusedException(CsvImportFileProblem.UNKNOWN_HEADERS, List.copyOf(unknown));
        }

        Set<String> seen = new LinkedHashSet<>();
        Set<String> duplicated = new LinkedHashSet<>();
        for (String column : columns) {
            if (!seen.add(column)) {
                duplicated.add(column);
            }
        }
        if (!duplicated.isEmpty()) {
            throw new CsvImportFileRefusedException(CsvImportFileProblem.DUPLICATED_HEADERS,
                    List.copyOf(duplicated));
        }

        List<String> missing = layout.requiredColumns().stream().filter(column -> !columns.contains(column)).toList();
        if (!missing.isEmpty()) {
            throw new CsvImportFileRefusedException(CsvImportFileProblem.MISSING_HEADERS, missing);
        }
        return columns;
    }

    private static boolean allBlank(String[] cells) {
        for (String cell : cells) {
            if (!cell.isBlank()) {
                return false;
            }
        }
        return true;
    }

    private static CsvImportRow rowOf(int line, List<String> columns, String[] cells) {
        if (cells.length != columns.size()) {
            return CsvImportRow.malformed(line);
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (int i = 0; i < cells.length; i++) {
            values.put(columns.get(i), cells[i]);
        }
        return CsvImportRow.of(line, values);
    }
}

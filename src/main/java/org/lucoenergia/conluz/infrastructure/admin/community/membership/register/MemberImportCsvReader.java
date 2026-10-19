package org.lucoenergia.conluz.infrastructure.admin.community.membership.register;

import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.RFC4180ParserBuilder;
import com.opencsv.exceptions.CsvValidationException;
import org.lucoenergia.conluz.domain.admin.community.membership.register.ImportMembersService;
import org.lucoenergia.conluz.domain.admin.community.membership.register.MemberImportFileProblem;
import org.lucoenergia.conluz.domain.admin.community.membership.register.MemberImportFileRefusedException;
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
 * Reads a file of members into its data rows, refusing the file as a whole if it cannot be imported.
 *
 * <p>The columns are the fields of {@link RegisterMemberBody}, so that a row is bound as a registration body is. The
 * checks run in this order, and the first that fails refuses the file: the encoding, the separator, the CSV syntax,
 * unknown headers, duplicated headers, missing required headers, and the number of rows. The cells are kept as they
 * are, untrimmed, as a registration body's values are.</p>
 *
 * <p>The encoding is told by a fixed rule, without guessing from the text: a file starting with the UTF-8 byte order
 * mark is UTF-8; one starting with a UTF-16 byte order mark is refused; any other is UTF-8 if it is valid UTF-8, and
 * Windows-1252 otherwise. Spanish Excel saves "CSV UTF-8" with the byte order mark, and its default "CSV" in
 * Windows-1252, without one. Windows-1252 text with accents is practically never valid UTF-8: UTF-8 would require
 * each accented letter to be followed by one or more of the bytes Windows-1252 uses for symbols such as € or ™.</p>
 */
@Component
public class MemberImportCsvReader {

    static final List<String> COLUMNS = List.of("personalId", "fullName", "memberNumber", "contactEmail", "address",
            "phoneNumber");
    static final List<String> REQUIRED_COLUMNS = List.of("personalId", "fullName");

    private static final byte[] UTF_8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private static final byte[] UTF_16_BE_BOM = {(byte) 0xFE, (byte) 0xFF};
    private static final byte[] UTF_16_LE_BOM = {(byte) 0xFF, (byte) 0xFE};

    /**
     * @return the file's encoding, and its data rows, in the order of the file, without the rows whose cells are all
     *         blank, such as the {@code ;;;;;} lines a spreadsheet exports after its last row
     * @throws MemberImportFileRefusedException if the file cannot be imported
     */
    public MemberImportCsv read(byte[] content) {
        MemberImportEncoding encoding = encodingOf(content);
        String text = decode(content, encoding);
        char separator = separatorOf(firstLine(text));

        try (CSVReader reader = new CSVReaderBuilder(new StringReader(text))
                .withCSVParser(new RFC4180ParserBuilder().withSeparator(separator).build())
                .build()) {
            String[] header = reader.readNext();
            if (header == null) {
                throw new MemberImportFileRefusedException(MemberImportFileProblem.MISSING_HEADERS, REQUIRED_COLUMNS);
            }
            List<String> columns = columnsOf(header);

            List<MemberImportCsvRow> rows = new ArrayList<>();
            long linesBefore = reader.getLinesRead();
            String[] cells;
            while ((cells = reader.readNext()) != null) {
                int line = (int) linesBefore + 1;
                linesBefore = reader.getLinesRead();
                if (allBlank(cells)) {
                    continue;
                }
                // Refused as soon as the limit is passed, without reading the rest of the file
                if (rows.size() == ImportMembersService.MAX_ROWS) {
                    throw new MemberImportFileRefusedException(MemberImportFileProblem.TOO_MANY_ROWS);
                }
                rows.add(rowOf(line, columns, cells));
            }
            return new MemberImportCsv(encoding, rows);
        } catch (CsvValidationException | IOException e) {
            // Reading from a string fails only on the CSV syntax, for example a quoted value that is never closed
            throw new MemberImportFileRefusedException(MemberImportFileProblem.MALFORMED_FILE);
        }
    }

    private static MemberImportEncoding encodingOf(byte[] content) {
        if (startsWith(content, UTF_8_BOM)) {
            return MemberImportEncoding.UTF_8;
        }
        // Excel's "Unicode text" is UTF-16 and separated by tabs: it is not a CSV file
        if (startsWith(content, UTF_16_BE_BOM) || startsWith(content, UTF_16_LE_BOM)) {
            throw new MemberImportFileRefusedException(MemberImportFileProblem.INVALID_ENCODING);
        }
        return isValid(content, MemberImportEncoding.UTF_8)
                ? MemberImportEncoding.UTF_8 : MemberImportEncoding.WINDOWS_1252;
    }

    /**
     * Strict: a byte the encoding does not define, such as {@code 0x81} in Windows-1252, refuses the file rather
     * than being replaced.
     */
    private static String decode(byte[] content, MemberImportEncoding encoding) {
        // The byte order mark is not part of the first header
        int offset = startsWith(content, UTF_8_BOM) ? UTF_8_BOM.length : 0;
        try {
            return decoderOf(encoding).decode(ByteBuffer.wrap(content, offset, content.length - offset)).toString();
        } catch (CharacterCodingException e) {
            throw new MemberImportFileRefusedException(MemberImportFileProblem.INVALID_ENCODING);
        }
    }

    private static boolean isValid(byte[] content, MemberImportEncoding encoding) {
        try {
            decoderOf(encoding).decode(ByteBuffer.wrap(content));
            return true;
        } catch (CharacterCodingException e) {
            return false;
        }
    }

    private static CharsetDecoder decoderOf(MemberImportEncoding encoding) {
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
            throw new MemberImportFileRefusedException(MemberImportFileProblem.AMBIGUOUS_SEPARATOR);
        }
        return semicolon ? ';' : ',';
    }

    private static List<String> columnsOf(String[] header) {
        List<String> columns = new ArrayList<>();
        for (String name : header) {
            columns.add(name.strip());
        }

        Set<String> unknown = new LinkedHashSet<>();
        for (String column : columns) {
            if (!COLUMNS.contains(column)) {
                unknown.add(column);
            }
        }
        if (!unknown.isEmpty()) {
            throw new MemberImportFileRefusedException(MemberImportFileProblem.UNKNOWN_HEADERS, List.copyOf(unknown));
        }

        Set<String> seen = new LinkedHashSet<>();
        Set<String> duplicated = new LinkedHashSet<>();
        for (String column : columns) {
            if (!seen.add(column)) {
                duplicated.add(column);
            }
        }
        if (!duplicated.isEmpty()) {
            throw new MemberImportFileRefusedException(MemberImportFileProblem.DUPLICATED_HEADERS,
                    List.copyOf(duplicated));
        }

        List<String> missing = REQUIRED_COLUMNS.stream().filter(column -> !columns.contains(column)).toList();
        if (!missing.isEmpty()) {
            throw new MemberImportFileRefusedException(MemberImportFileProblem.MISSING_HEADERS, missing);
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

    private static MemberImportCsvRow rowOf(int line, List<String> columns, String[] cells) {
        if (cells.length != columns.size()) {
            return MemberImportCsvRow.malformed(line);
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (int i = 0; i < cells.length; i++) {
            values.put(columns.get(i), cells[i]);
        }
        return MemberImportCsvRow.of(line, values);
    }
}

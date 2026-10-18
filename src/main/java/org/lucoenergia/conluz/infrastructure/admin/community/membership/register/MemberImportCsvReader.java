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
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
 */
@Component
public class MemberImportCsvReader {

    static final List<String> COLUMNS = List.of("personalId", "fullName", "memberNumber", "contactEmail", "address",
            "phoneNumber");
    static final List<String> REQUIRED_COLUMNS = List.of("personalId", "fullName");

    private static final char BYTE_ORDER_MARK = '﻿';

    /**
     * @return the data rows, in the order of the file, without the rows whose cells are all blank, such as the
     *         {@code ;;;;;} lines a spreadsheet exports after its last row
     * @throws MemberImportFileRefusedException if the file cannot be imported
     */
    public List<MemberImportCsvRow> read(byte[] content) {
        String text = decode(content);
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
            return rows;
        } catch (CsvValidationException | IOException e) {
            // Reading from a string fails only on the CSV syntax, for example a quoted value that is never closed
            throw new MemberImportFileRefusedException(MemberImportFileProblem.MALFORMED_FILE);
        }
    }

    private static String decode(byte[] content) {
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content))
                    .toString();
        } catch (CharacterCodingException e) {
            throw new MemberImportFileRefusedException(MemberImportFileProblem.INVALID_ENCODING);
        }
        // Spreadsheets save UTF-8 with a byte order mark; it is not part of the first header
        return !text.isEmpty() && text.charAt(0) == BYTE_ORDER_MARK ? text.substring(1) : text;
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

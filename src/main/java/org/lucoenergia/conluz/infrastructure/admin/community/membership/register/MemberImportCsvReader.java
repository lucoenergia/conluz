package org.lucoenergia.conluz.infrastructure.admin.community.membership.register;

import org.lucoenergia.conluz.domain.admin.community.membership.register.ImportMembersService;
import org.lucoenergia.conluz.domain.admin.community.membership.register.MemberImportFileProblem;
import org.lucoenergia.conluz.domain.admin.community.membership.register.MemberImportFileRefusedException;
import org.lucoenergia.conluz.infrastructure.shared.io.CsvImportFile;
import org.lucoenergia.conluz.infrastructure.shared.io.CsvImportFileReader;
import org.lucoenergia.conluz.infrastructure.shared.io.CsvImportFileRefusedException;
import org.lucoenergia.conluz.infrastructure.shared.io.CsvImportLayout;
import org.lucoenergia.conluz.infrastructure.shared.io.CsvImportRow;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Reads a file of members into its data rows, refusing the file as a whole if it cannot be imported, with the rules
 * of {@link CsvImportFileReader}.
 *
 * <p>The columns are the fields of {@link RegisterMemberBody}, so that a row is bound as a registration body is. The
 * cells are kept as they are, untrimmed, as a registration body's values are.</p>
 */
@Component
public class MemberImportCsvReader {

    static final List<String> COLUMNS = List.of("personalId", "fullName", "memberNumber", "contactEmail", "address",
            "phoneNumber");
    static final List<String> REQUIRED_COLUMNS = List.of("personalId", "fullName");

    private static final CsvImportLayout LAYOUT = new CsvImportLayout(COLUMNS, REQUIRED_COLUMNS,
            ImportMembersService.MAX_ROWS);

    private final CsvImportFileReader reader;

    public MemberImportCsvReader(CsvImportFileReader reader) {
        this.reader = reader;
    }

    /**
     * @return the file's encoding, and its data rows, in the order of the file, without the rows whose cells are all
     *         blank, such as the {@code ;;;;;} lines a spreadsheet exports after its last row
     * @throws MemberImportFileRefusedException if the file cannot be imported
     */
    public MemberImportCsv read(byte[] content) {
        CsvImportFile file;
        try {
            file = reader.read(content, LAYOUT);
        } catch (CsvImportFileRefusedException e) {
            throw new MemberImportFileRefusedException(problemOf(e), e.getHeaders());
        }
        return new MemberImportCsv(encodingOf(file), file.rows().stream().map(MemberImportCsvReader::rowOf).toList());
    }

    private static MemberImportFileProblem problemOf(CsvImportFileRefusedException e) {
        return switch (e.getProblem()) {
            case INVALID_ENCODING -> MemberImportFileProblem.INVALID_ENCODING;
            case AMBIGUOUS_SEPARATOR -> MemberImportFileProblem.AMBIGUOUS_SEPARATOR;
            case MALFORMED_FILE -> MemberImportFileProblem.MALFORMED_FILE;
            case UNKNOWN_HEADERS -> MemberImportFileProblem.UNKNOWN_HEADERS;
            case DUPLICATED_HEADERS -> MemberImportFileProblem.DUPLICATED_HEADERS;
            case MISSING_HEADERS -> MemberImportFileProblem.MISSING_HEADERS;
            case TOO_MANY_ROWS -> MemberImportFileProblem.TOO_MANY_ROWS;
        };
    }

    private static MemberImportEncoding encodingOf(CsvImportFile file) {
        return switch (file.encoding()) {
            case UTF_8 -> MemberImportEncoding.UTF_8;
            case WINDOWS_1252 -> MemberImportEncoding.WINDOWS_1252;
        };
    }

    private static MemberImportCsvRow rowOf(CsvImportRow row) {
        return row.isMalformed() ? MemberImportCsvRow.malformed(row.line()) : MemberImportCsvRow.of(row.line(),
                row.values());
    }
}

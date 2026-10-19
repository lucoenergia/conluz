package org.lucoenergia.conluz.infrastructure.admin.community.membership.register;

import java.util.List;

/**
 * A file of members, read.
 *
 * @param encoding the encoding the file was read in
 * @param rows     the data rows, in the order of the file
 */
public record MemberImportCsv(MemberImportEncoding encoding, List<MemberImportCsvRow> rows) {

    public MemberImportCsv {
        rows = List.copyOf(rows);
    }
}

package org.lucoenergia.conluz.infrastructure.admin.community.membership.register;

import java.util.Map;

/**
 * One data row of a file of members.
 *
 * @param line   the line of the file the row starts on, the header being line 1
 * @param values the row's cells by column name, untrimmed; null if the row is malformed
 */
public record MemberImportCsvRow(int line, Map<String, String> values) {

    public static MemberImportCsvRow of(int line, Map<String, String> values) {
        return new MemberImportCsvRow(line, Map.copyOf(values));
    }

    /**
     * A row that does not have as many cells as the header, so its cells cannot be told apart.
     */
    public static MemberImportCsvRow malformed(int line) {
        return new MemberImportCsvRow(line, null);
    }

    public boolean isMalformed() {
        return values == null;
    }
}

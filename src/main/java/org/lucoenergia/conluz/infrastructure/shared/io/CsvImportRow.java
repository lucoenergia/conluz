package org.lucoenergia.conluz.infrastructure.shared.io;

import java.util.Map;

/**
 * One data row of an imported CSV file.
 *
 * @param line   the line of the file the row starts on, the header being line 1
 * @param values the row's cells by column name, untrimmed; null if the row is malformed
 */
public record CsvImportRow(int line, Map<String, String> values) {

    public static CsvImportRow of(int line, Map<String, String> values) {
        return new CsvImportRow(line, Map.copyOf(values));
    }

    /**
     * A row that does not have as many cells as the header, so its cells cannot be told apart.
     */
    public static CsvImportRow malformed(int line) {
        return new CsvImportRow(line, null);
    }

    public boolean isMalformed() {
        return values == null;
    }
}

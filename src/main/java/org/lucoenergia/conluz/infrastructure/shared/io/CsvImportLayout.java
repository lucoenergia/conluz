package org.lucoenergia.conluz.infrastructure.shared.io;

import java.util.List;

/**
 * The columns an imported CSV file may hold, and how many data rows it may have.
 *
 * @param columns         every column the file may name, in the order a row's broken rules are reported in
 * @param requiredColumns the columns the header must name; a subset of {@code columns}
 * @param maxRows         the most data rows the file may hold, so that one request stays bounded
 */
public record CsvImportLayout(List<String> columns, List<String> requiredColumns, int maxRows) {

    public CsvImportLayout {
        columns = List.copyOf(columns);
        requiredColumns = List.copyOf(requiredColumns);
    }
}

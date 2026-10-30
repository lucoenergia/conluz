package org.lucoenergia.conluz.infrastructure.shared.io;

import java.util.List;

/**
 * An imported CSV file, read.
 *
 * @param encoding the encoding the file was read in
 * @param rows     the data rows, in the order of the file
 */
public record CsvImportFile(CsvImportEncoding encoding, List<CsvImportRow> rows) {

    public CsvImportFile {
        rows = List.copyOf(rows);
    }
}

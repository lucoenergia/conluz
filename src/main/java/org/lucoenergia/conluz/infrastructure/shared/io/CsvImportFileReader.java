package org.lucoenergia.conluz.infrastructure.shared.io;

/**
 * Reads an imported CSV file into its data rows, refusing the file as a whole if it cannot be imported. Shared by the
 * imports, each of which gives the columns it accepts.
 */
public interface CsvImportFileReader {

    /**
     * @return the file's encoding, and its data rows, in the order of the file, without the rows whose cells are all
     *         blank, such as the {@code ;;;;;} lines a spreadsheet exports after its last row
     * @throws CsvImportFileRefusedException if the file cannot be imported
     */
    CsvImportFile read(byte[] content, CsvImportLayout layout);
}

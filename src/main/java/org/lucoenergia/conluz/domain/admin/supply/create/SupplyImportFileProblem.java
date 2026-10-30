package org.lucoenergia.conluz.domain.admin.supply.create;

/**
 * Why a file of supplies is refused as a whole, before any of its rows is created.
 */
public enum SupplyImportFileProblem {

    /**
     * The file is neither UTF-8 nor Windows-1252.
     */
    INVALID_ENCODING,
    /**
     * The header line holds both {@code ,} and {@code ;}, so the separator cannot be told.
     */
    AMBIGUOUS_SEPARATOR,
    /**
     * The file cannot be read as CSV, for example because a quoted value is never closed.
     */
    MALFORMED_FILE,
    /**
     * The header names a column that is not imported.
     */
    UNKNOWN_HEADERS,
    /**
     * The header names a column more than once.
     */
    DUPLICATED_HEADERS,
    /**
     * The header lacks a required column, or the file has no header at all.
     */
    MISSING_HEADERS,
    /**
     * The file holds more than {@link ImportSuppliesService#MAX_ROWS} data rows.
     */
    TOO_MANY_ROWS
}

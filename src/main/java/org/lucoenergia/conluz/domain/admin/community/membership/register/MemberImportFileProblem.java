package org.lucoenergia.conluz.domain.admin.community.membership.register;

/**
 * Why a file of members is refused as a whole, before any of its rows is registered.
 */
public enum MemberImportFileProblem {

    /**
     * The file is neither UTF-8 nor Windows-1252: it starts with a UTF-16 byte order mark, it holds invalid UTF-8
     * after a UTF-8 byte order mark, or, without one, a byte that Windows-1252 does not define.
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
     * The header names a column that is not imported, such as a password, community or role column.
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
     * The file holds more than {@link ImportMembersService#MAX_ROWS} data rows.
     */
    TOO_MANY_ROWS
}

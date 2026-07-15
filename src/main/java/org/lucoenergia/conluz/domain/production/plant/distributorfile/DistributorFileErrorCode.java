package org.lucoenergia.conluz.domain.production.plant.distributorfile;

/**
 * One value per numbered validation rule of the distributor coefficient-partition file format,
 * plus {@link #LINE_MALFORMED} for a line that isn't even shaped as {@code CUPS;value}.
 */
public enum DistributorFileErrorCode {
    /** Rule 1: filename does not match {@code {code}_{YYYY}.txt}. Params: filename. */
    FILENAME_SHAPE_INVALID,
    /** Rule 2 (null case): the plant has no regulatory_code configured. No params. */
    PLANT_REGULATORY_CODE_MISSING,
    /** Rule 2 (mismatch case): filename prefix != plant's regulatory_code. Params: expected, actual. */
    FILENAME_REGULATORY_CODE_MISMATCH,
    /** Rule 3: decimal separator is not a comma. Params: line, value. */
    VALUE_DECIMAL_SEPARATOR_INVALID,
    /** Rule 4: not exactly six decimal digits. Params: line, cups, value. */
    VALUE_SCALE_INVALID,
    /** Rule 5: CUPS is not exactly 22 characters. Params: line, cups. */
    CUPS_LENGTH_INVALID,
    /** Rule 6: the same CUPS appears on more than one line. Params: cups, lines. */
    CUPS_DUPLICATE,
    /** Rule 7: sum of all coefficients is not exactly 1.000000. Params: actualSum. */
    COEFFICIENT_SUM_INVALID,
    /** Rule 8: CUPS is not a known supply of the plant's community. Params: line, cups. */
    CUPS_UNKNOWN,
    /** Not a numbered rule: line does not split into exactly two non-empty ';'-delimited tokens. Params: line, rawLine. */
    LINE_MALFORMED
}

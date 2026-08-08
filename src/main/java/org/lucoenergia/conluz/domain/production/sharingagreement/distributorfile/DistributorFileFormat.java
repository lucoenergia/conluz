package org.lucoenergia.conluz.domain.production.sharingagreement.distributorfile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.regex.Pattern;

/**
 * The i-DE distributor coefficient-partition file format: lines {@code CUPS;coefficient}, comma
 * decimal separator, exactly six decimal digits, sum of all coefficients exactly {@code 1.000000}.
 * Filename {@code {code}_{YYYY}.txt} where {@code code} is the plant's regulatory code (CAU).
 *
 * <p>Shared by {@link DistributorFileParser} (read direction) and the on-demand file generator
 * (write direction) so the two cannot drift apart.
 */
public final class DistributorFileFormat {

    public static final String SEPARATOR = ";";
    public static final String LINE_SEPARATOR = "\n";
    public static final int CUPS_LENGTH = 22;
    public static final int REQUIRED_DECIMAL_DIGITS = 6;
    public static final Charset CHARSET = StandardCharsets.UTF_8;
    public static final Pattern FILENAME_PATTERN = Pattern.compile("^(?<code>[^_]+)_(?<year>\\d{4})\\.txt$");
    public static final BigDecimal EXPECTED_SUM = new BigDecimal("1.000000");

    private DistributorFileFormat() {
    }

    /**
     * Scales a coefficient to exactly {@link #REQUIRED_DECIMAL_DIGITS} decimal digits, matching
     * what will be written to a file line. Throws {@link ArithmeticException} if the coefficient
     * has more than {@link #REQUIRED_DECIMAL_DIGITS} decimal digits of actual precision, since
     * rounding here would silently break the round-trip guarantee.
     */
    public static BigDecimal normalizeScale(BigDecimal coefficient) {
        return coefficient.setScale(REQUIRED_DECIMAL_DIGITS, RoundingMode.UNNECESSARY);
    }

    /**
     * Formats one {@code CUPS;coefficient} line, comma decimal separator, six decimal digits.
     */
    public static String formatCoefficientLine(String cups, BigDecimal coefficient) {
        String value = normalizeScale(coefficient).toPlainString().replace('.', ',');
        return cups + SEPARATOR + value;
    }

    /**
     * Builds the distributor filename {@code {regulatoryCode}_{year}.txt}.
     */
    public static String buildFilename(String regulatoryCode, int year) {
        return regulatoryCode + "_" + year + ".txt";
    }

    /**
     * A coefficient set is valid only when it sums to exactly {@link #EXPECTED_SUM} — no
     * tolerance, no rounding to force a fit.
     */
    public static boolean isValidSum(BigDecimal sum) {
        return sum.compareTo(EXPECTED_SUM) == 0;
    }

    /**
     * Sums {@code coefficients} after normalizing each to {@link #REQUIRED_DECIMAL_DIGITS} decimal
     * digits -- the same total {@link #isValidSum} is checked against. Callers that need both the
     * boolean verdict and the actual sum (e.g. to report it in an error) compute it once here and
     * pass it to {@link #isValidSum}, instead of duplicating this pipeline inline.
     */
    public static BigDecimal normalizedSum(Collection<BigDecimal> coefficients) {
        return coefficients.stream()
                .map(DistributorFileFormat::normalizeScale)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}

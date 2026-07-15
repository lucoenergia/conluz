package org.lucoenergia.conluz.infrastructure.production.plant.distributorfile;

import org.lucoenergia.conluz.domain.production.plant.distributorfile.DistributorFileEntry;
import org.lucoenergia.conluz.domain.production.plant.distributorfile.DistributorFileError;
import org.lucoenergia.conluz.domain.production.plant.distributorfile.DistributorFileErrorCode;
import org.lucoenergia.conluz.domain.production.plant.distributorfile.DistributorFileParseResult;
import org.lucoenergia.conluz.domain.production.plant.distributorfile.DistributorFileParser;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Spanish distributor coefficient-partition file: lines {@code CUPS;coefficient}, comma decimal
 * separator, exactly six decimal digits, sum of all coefficients exactly {@code 1.000000}.
 * Filename {@code {code}_{YYYY}.txt} where {@code code} is the plant's regulatory_code (CAU).
 *
 * <p>All 8 rules (plus the non-numbered malformed-line case) are checked in a single pass; a
 * failure in one rule never prevents the others from being checked and reported.
 */
@Component
public class DistributorFileParserImpl implements DistributorFileParser {

    private static final Pattern FILENAME_PATTERN = Pattern.compile("^(?<code>[^_]+)_(?<year>\\d{4})\\.txt$");
    private static final Pattern COMMA_DECIMAL_PATTERN = Pattern.compile("^\\d+,\\d+$");
    private static final BigDecimal EXPECTED_SUM = new BigDecimal("1.000000");
    private static final int CUPS_LENGTH = 22;
    private static final int REQUIRED_DECIMAL_DIGITS = 6;

    @Override
    public DistributorFileParseResult parse(String filename, byte[] content, String plantRegulatoryCode,
                                             Set<String> knownCups) {
        List<DistributorFileError> errors = new ArrayList<>();

        checkFilename(filename, plantRegulatoryCode, errors);

        List<DistributorFileEntry> entries = new ArrayList<>();
        Map<String, List<Integer>> lineNumbersByCups = new LinkedHashMap<>();
        BigDecimal sum = BigDecimal.ZERO;

        List<String> lines = splitLines(content);
        for (int i = 0; i < lines.size(); i++) {
            int lineNumber = i + 1;
            String rawLine = lines.get(i);

            String[] tokens = rawLine.split(";", -1);
            if (tokens.length != 2 || tokens[0].isEmpty() || tokens[1].isEmpty()) {
                errors.add(new DistributorFileError(DistributorFileErrorCode.LINE_MALFORMED, lineNumber,
                        Map.of("line", String.valueOf(lineNumber), "rawLine", rawLine)));
                continue;
            }
            String rawCups = tokens[0];
            String rawValue = tokens[1];

            // Rule 5 -- independent of the value checks below.
            if (rawCups.length() != CUPS_LENGTH) {
                errors.add(new DistributorFileError(DistributorFileErrorCode.CUPS_LENGTH_INVALID, lineNumber,
                        Map.of("line", String.valueOf(lineNumber), "cups", rawCups)));
            }

            // Rules 3/4 -- rule 3 gates rule 4: digit-counting is meaningless without first
            // knowing where the fractional part starts, so a non-comma-shaped token never also
            // fires rule 4.
            BigDecimal parsedValue = checkValue(rawValue, lineNumber, rawCups, errors);
            if (parsedValue != null) {
                entries.add(new DistributorFileEntry(rawCups, parsedValue, lineNumber));
                sum = sum.add(parsedValue);
            }

            // Rules 6/8 -- independent of whether the value itself parsed successfully; a CUPS
            // can be duplicated or unknown regardless of its coefficient's validity.
            lineNumbersByCups.computeIfAbsent(rawCups, k -> new ArrayList<>()).add(lineNumber);
            if (!knownCups.contains(rawCups)) {
                errors.add(new DistributorFileError(DistributorFileErrorCode.CUPS_UNKNOWN, lineNumber,
                        Map.of("line", String.valueOf(lineNumber), "cups", rawCups)));
            }
        }

        checkDuplicates(lineNumbersByCups, errors);
        checkSum(sum, errors);

        return new DistributorFileParseResult(entries, errors);
    }

    private void checkFilename(String filename, String plantRegulatoryCode, List<DistributorFileError> errors) {
        Matcher matcher = FILENAME_PATTERN.matcher(filename);
        if (!matcher.matches()) {
            errors.add(new DistributorFileError(DistributorFileErrorCode.FILENAME_SHAPE_INVALID, null,
                    Map.of("filename", filename)));
            return;
        }
        String code = matcher.group("code");
        if (plantRegulatoryCode == null) {
            errors.add(new DistributorFileError(DistributorFileErrorCode.PLANT_REGULATORY_CODE_MISSING, null,
                    Map.of()));
        } else if (!code.equals(plantRegulatoryCode)) {
            errors.add(new DistributorFileError(DistributorFileErrorCode.FILENAME_REGULATORY_CODE_MISMATCH, null,
                    Map.of("expected", plantRegulatoryCode, "actual", code)));
        }
    }

    private BigDecimal checkValue(String rawValue, int lineNumber, String rawCups, List<DistributorFileError> errors) {
        if (!COMMA_DECIMAL_PATTERN.matcher(rawValue).matches()) {
            errors.add(new DistributorFileError(DistributorFileErrorCode.VALUE_DECIMAL_SEPARATOR_INVALID, lineNumber,
                    Map.of("line", String.valueOf(lineNumber), "value", rawValue)));
            return null;
        }
        String fractionalDigits = rawValue.substring(rawValue.indexOf(',') + 1);
        if (fractionalDigits.length() != REQUIRED_DECIMAL_DIGITS) {
            errors.add(new DistributorFileError(DistributorFileErrorCode.VALUE_SCALE_INVALID, lineNumber,
                    Map.of("line", String.valueOf(lineNumber), "cups", rawCups, "value", rawValue)));
            return null;
        }
        return new BigDecimal(rawValue.replace(',', '.'));
    }

    private void checkDuplicates(Map<String, List<Integer>> lineNumbersByCups, List<DistributorFileError> errors) {
        for (Map.Entry<String, List<Integer>> entry : lineNumbersByCups.entrySet()) {
            if (entry.getValue().size() > 1) {
                String lines = entry.getValue().stream().map(String::valueOf).collect(Collectors.joining(","));
                errors.add(new DistributorFileError(DistributorFileErrorCode.CUPS_DUPLICATE, null,
                        Map.of("cups", entry.getKey(), "lines", lines)));
            }
        }
    }

    private void checkSum(BigDecimal sum, List<DistributorFileError> errors) {
        if (sum.compareTo(EXPECTED_SUM) != 0) {
            errors.add(new DistributorFileError(DistributorFileErrorCode.COEFFICIENT_SUM_INVALID, null,
                    Map.of("actualSum", sum.toPlainString())));
        }
    }

    /**
     * Splits raw file content into lines, dropping exactly one trailing empty element produced by
     * a final newline. Any other blank line is preserved and will surface as
     * {@link DistributorFileErrorCode#LINE_MALFORMED}.
     */
    private List<String> splitLines(byte[] content) {
        String text = new String(content, StandardCharsets.UTF_8);
        String[] rawLines = text.split("\r\n|\r|\n", -1);
        int lineCount = rawLines.length;
        if (lineCount > 0 && rawLines[lineCount - 1].isEmpty()) {
            lineCount--;
        }
        List<String> lines = new ArrayList<>(lineCount);
        for (int i = 0; i < lineCount; i++) {
            lines.add(rawLines[i]);
        }
        return lines;
    }
}

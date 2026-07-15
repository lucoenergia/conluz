package org.lucoenergia.conluz.infrastructure.production.plant.distributorfile;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.production.plant.distributorfile.DistributorFileError;
import org.lucoenergia.conluz.domain.production.plant.distributorfile.DistributorFileErrorCode;
import org.lucoenergia.conluz.domain.production.plant.distributorfile.DistributorFileParseResult;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DistributorFileParserImplTest {

    private static final String PLANT_REGULATORY_CODE = "ES0031300325733001FH0FA000";
    private static final String VALID_FILENAME = "ES0031300325733001FH0FA000_2023.txt";

    private static final String CUPS1 = "ES0031300325733001FH0F";
    private static final String CUPS2 = "ES0031300325733002FH0F";
    private static final String CUPS3 = "ES0031300325733003FH0F";
    private static final String CUPS_21 = "ES0031300325733002FH0";
    private static final String CUPS_23 = "ES0031300325733001FH0FX";

    private static final Set<String> KNOWN_CUPS = Set.of(CUPS1, CUPS2, CUPS3);

    private final DistributorFileParserImpl parser = new DistributorFileParserImpl();

    @Test
    void validFileProducesNoErrorsAndAllEntries() throws IOException {
        byte[] content = loadFixture(VALID_FILENAME);

        DistributorFileParseResult result = parser.parse(VALID_FILENAME, content, PLANT_REGULATORY_CODE, KNOWN_CUPS);

        assertTrue(result.isValid());
        assertEquals(3, result.getEntries().size());
        assertEquals(CUPS1, result.getEntries().get(0).getCups());
        assertEquals(1, result.getEntries().get(0).getLineNumber());
        assertEquals(0, new BigDecimal("0.333333").compareTo(result.getEntries().get(0).getCoefficient()));
        assertEquals(CUPS3, result.getEntries().get(2).getCups());
        assertEquals(0, new BigDecimal("0.333334").compareTo(result.getEntries().get(2).getCoefficient()));
    }

    @Test
    void rule1FailureSkipsRule2ButStillRunsLineChecks() throws IOException {
        byte[] content = loadFixture("rule1_filename_shape_invalid.txt");
        String badFilename = "badfilename.txt";

        DistributorFileParseResult result = parser.parse(badFilename, content, PLANT_REGULATORY_CODE, KNOWN_CUPS);

        assertEquals(1, result.getErrors().size());
        DistributorFileError error = result.getErrors().get(0);
        assertEquals(DistributorFileErrorCode.FILENAME_SHAPE_INVALID, error.getCode());
        assertEquals(badFilename, error.getParams().get("filename"));
        assertEquals(3, result.getEntries().size());
    }

    @Test
    void rule2FilenameRegulatoryCodeMismatchReportsExpectedAndActual() throws IOException {
        byte[] content = loadFixture(VALID_FILENAME);

        DistributorFileParseResult result = parser.parse(VALID_FILENAME, content, "OTHER_CODE", KNOWN_CUPS);

        DistributorFileError error = findFirst(result, DistributorFileErrorCode.FILENAME_REGULATORY_CODE_MISMATCH);
        assertEquals("OTHER_CODE", error.getParams().get("expected"));
        assertEquals(PLANT_REGULATORY_CODE, error.getParams().get("actual"));
    }

    @Test
    void rule2NullRegulatoryCodeProducesDedicatedError() throws IOException {
        byte[] content = loadFixture(VALID_FILENAME);

        DistributorFileParseResult result = parser.parse(VALID_FILENAME, content, null, KNOWN_CUPS);

        DistributorFileError error = findFirst(result, DistributorFileErrorCode.PLANT_REGULATORY_CODE_MISSING);
        assertTrue(error.getParams().isEmpty());
    }

    @Test
    void rule3DecimalSeparatorInvalid() throws IOException {
        byte[] content = loadFixture("rule3_decimal_separator_invalid_2023.txt");

        DistributorFileParseResult result = parser.parse(VALID_FILENAME, content, PLANT_REGULATORY_CODE, KNOWN_CUPS);

        DistributorFileError error = findFirst(result, DistributorFileErrorCode.VALUE_DECIMAL_SEPARATOR_INVALID);
        assertEquals("1", error.getParams().get("line"));
        assertEquals("0.333333", error.getParams().get("value"));
        assertEquals(2, result.getEntries().size());
        DistributorFileError sumError = findFirst(result, DistributorFileErrorCode.COEFFICIENT_SUM_INVALID);
        assertEquals(0, new BigDecimal("0.666667").compareTo(new BigDecimal(sumError.getParams().get("actualSum"))));
    }

    @Test
    void rule4ScaleInvalidRejectsTooFewAndTooManyDigits() throws IOException {
        byte[] content = loadFixture("rule4_scale_invalid_2023.txt");

        DistributorFileParseResult result = parser.parse(VALID_FILENAME, content, PLANT_REGULATORY_CODE, KNOWN_CUPS);

        List<DistributorFileError> scaleErrors = findAll(result, DistributorFileErrorCode.VALUE_SCALE_INVALID);
        assertEquals(2, scaleErrors.size());
        assertTrue(scaleErrors.stream().anyMatch(e -> "0,03076".equals(e.getParams().get("value")) && CUPS1.equals(e.getParams().get("cups"))));
        assertTrue(scaleErrors.stream().anyMatch(e -> "0,0307630".equals(e.getParams().get("value")) && CUPS2.equals(e.getParams().get("cups"))));
        assertEquals(1, result.getEntries().size());
    }

    @Test
    void rule5CupsLengthInvalidRejectsTooShortAndTooLong() throws IOException {
        byte[] content = loadFixture("rule5_cups_length_invalid_2023.txt");
        Set<String> knownCups = Set.of(CUPS_21, CUPS_23);

        DistributorFileParseResult result = parser.parse(VALID_FILENAME, content, PLANT_REGULATORY_CODE, knownCups);

        List<DistributorFileError> lengthErrors = findAll(result, DistributorFileErrorCode.CUPS_LENGTH_INVALID);
        assertEquals(2, lengthErrors.size());
        assertTrue(lengthErrors.stream().anyMatch(e -> CUPS_21.equals(e.getParams().get("cups"))));
        assertTrue(lengthErrors.stream().anyMatch(e -> CUPS_23.equals(e.getParams().get("cups"))));
        assertEquals(2, result.getEntries().size());
    }

    @Test
    void rule6DuplicateCupsReportsAllCollidingLines() throws IOException {
        byte[] content = loadFixture("rule6_duplicate_cups_2023.txt");
        Set<String> knownCups = Set.of(CUPS1);

        DistributorFileParseResult result = parser.parse(VALID_FILENAME, content, PLANT_REGULATORY_CODE, knownCups);

        DistributorFileError error = findFirst(result, DistributorFileErrorCode.CUPS_DUPLICATE);
        assertEquals(CUPS1, error.getParams().get("cups"));
        assertEquals("1,2,3", error.getParams().get("lines"));
        assertEquals(3, result.getEntries().size());
        assertFalse(result.getErrors().stream().anyMatch(e -> e.getCode() == DistributorFileErrorCode.COEFFICIENT_SUM_INVALID));
    }

    @Test
    void rule7SumInvalid() throws IOException {
        byte[] content = loadFixture("rule7_sum_invalid_2023.txt");

        DistributorFileParseResult result = parser.parse(VALID_FILENAME, content, PLANT_REGULATORY_CODE, KNOWN_CUPS);

        DistributorFileError error = findFirst(result, DistributorFileErrorCode.COEFFICIENT_SUM_INVALID);
        assertEquals(0, new BigDecimal("0.999999").compareTo(new BigDecimal(error.getParams().get("actualSum"))));
        assertEquals(3, result.getEntries().size());
    }

    @Test
    void rule8UnknownCupsIsScopedToProvidedKnownSet() throws IOException {
        byte[] content = loadFixture("rule8_unknown_cups_2023.txt");
        Set<String> knownCups = Set.of(CUPS1, CUPS2);

        DistributorFileParseResult result = parser.parse(VALID_FILENAME, content, PLANT_REGULATORY_CODE, knownCups);

        DistributorFileError error = findFirst(result, DistributorFileErrorCode.CUPS_UNKNOWN);
        assertEquals("3", error.getParams().get("line"));
        assertEquals(CUPS3, error.getParams().get("cups"));
    }

    @Test
    void malformedLinesAreCollectedWithoutCrashing() throws IOException {
        byte[] content = loadFixture("malformed_line_2023.txt");

        DistributorFileParseResult result = parser.parse(VALID_FILENAME, content, PLANT_REGULATORY_CODE, KNOWN_CUPS);

        List<DistributorFileError> malformed = findAll(result, DistributorFileErrorCode.LINE_MALFORMED);
        assertEquals(3, malformed.size());
        assertTrue(malformed.stream().anyMatch(e -> "1".equals(e.getParams().get("line"))));
        assertTrue(malformed.stream().anyMatch(e -> "2".equals(e.getParams().get("line"))));
        assertTrue(malformed.stream().anyMatch(e -> "3".equals(e.getParams().get("line"))));
        assertEquals(0, result.getEntries().size());
    }

    @Test
    void multiErrorFixtureCollectsAllViolationsInOnePass() throws IOException {
        byte[] content = loadFixture("multi_error_2023.txt");
        Set<String> knownCups = Set.of(CUPS1, CUPS_21);

        DistributorFileParseResult result = parser.parse(VALID_FILENAME, content, PLANT_REGULATORY_CODE, knownCups);

        Set<DistributorFileErrorCode> codes = result.getErrors().stream()
                .map(DistributorFileError::getCode)
                .collect(java.util.stream.Collectors.toSet());
        assertTrue(codes.contains(DistributorFileErrorCode.VALUE_SCALE_INVALID));
        assertTrue(codes.contains(DistributorFileErrorCode.CUPS_LENGTH_INVALID));
        assertTrue(codes.contains(DistributorFileErrorCode.CUPS_DUPLICATE));
        assertTrue(codes.contains(DistributorFileErrorCode.COEFFICIENT_SUM_INVALID));
        assertTrue(codes.size() >= 4);
    }

    @Test
    void parsingIsLocaleIndependent() throws IOException {
        byte[] content = loadFixture(VALID_FILENAME);
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            DistributorFileParseResult commaDefaultResult =
                    parser.parse(VALID_FILENAME, content, PLANT_REGULATORY_CODE, KNOWN_CUPS);

            Locale.setDefault(Locale.US);
            DistributorFileParseResult dotDefaultResult =
                    parser.parse(VALID_FILENAME, content, PLANT_REGULATORY_CODE, KNOWN_CUPS);

            assertTrue(commaDefaultResult.isValid());
            assertTrue(dotDefaultResult.isValid());
            for (int i = 0; i < commaDefaultResult.getEntries().size(); i++) {
                assertEquals(0, commaDefaultResult.getEntries().get(i).getCoefficient()
                        .compareTo(dotDefaultResult.getEntries().get(i).getCoefficient()));
            }
        } finally {
            Locale.setDefault(original);
        }
    }

    private DistributorFileError findFirst(DistributorFileParseResult result, DistributorFileErrorCode code) {
        return result.getErrors().stream()
                .filter(e -> e.getCode() == code)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected an error with code " + code + " but none was found. Errors: " + result.getErrors()));
    }

    private List<DistributorFileError> findAll(DistributorFileParseResult result, DistributorFileErrorCode code) {
        return result.getErrors().stream()
                .filter(e -> e.getCode() == code)
                .toList();
    }

    private byte[] loadFixture(String name) throws IOException {
        ClassPathResource resource = new ClassPathResource("fixtures/distributorfile/" + name);
        return Files.readAllBytes(resource.getFile().toPath());
    }
}

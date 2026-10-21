package org.lucoenergia.conluz.infrastructure.shared.web;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reads kWh figures from a response body exactly as written, for the assertions on their precision.
 *
 * <p>Jackson's default tree reads a JSON number into a double, which would hide the very digits these
 * assertions are about; reading it into a {@link BigDecimal} keeps the text the response carried.
 */
public final class KWhJson {

    private static final ObjectMapper DECIMAL_READER = new ObjectMapper()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);

    private KWhJson() {
    }

    /**
     * The number at the JSON pointer (e.g. {@code /energy/gridImportKWh}), as the response wrote it.
     */
    public static BigDecimal kWhAt(String body, String pointer) throws Exception {
        JsonNode node = DECIMAL_READER.readTree(body).at(pointer);
        assertTrue(node.isNumber(), () -> pointer + " is not a number in " + body);
        return node.decimalValue();
    }

    /**
     * Asserts the number at the JSON pointer carries at most 3 decimals and equals the expected figure.
     * A trailing zero is not a decimal: {@code 0.0020} is the value 0.002.
     */
    public static void assertKWh(String body, String pointer, String expected) throws Exception {
        BigDecimal actual = kWhAt(body, pointer);
        assertTrue(actual.stripTrailingZeros().scale() <= 3,
                () -> pointer + " carries more than 3 decimals: " + actual);
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                () -> pointer + " was " + actual + ", expected " + expected);
    }
}

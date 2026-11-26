package org.lucoenergia.conluz.infrastructure.shared.web.apidocs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The check against a small hand-written document, so that each kind of mismatch is shown to fail it.
 */
class ResponseContractAssertionsTest {

    private static final String DOCUMENT = """
            {"components": {"schemas": {
              "Outer": {"type": "object", "required": ["id", "when", "inner", "items"], "properties": {
                "id": {"type": "string"},
                "when": {"type": ["string", "null"], "format": "date-time"},
                "inner": {"$ref": "#/components/schemas/Inner", "type": ["object", "null"]},
                "items": {"type": "array", "items": {"$ref": "#/components/schemas/Inner"}}}},
              "Inner": {"type": "object", "required": ["state", "kWh"], "properties": {
                "state": {"type": "string", "enum": ["REAL", "ESTIMATED"]},
                "kWh": {"type": "number"}}}
            }}}
            """;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void aBodyMatchingTheSchemaPasses() {
        assertDoesNotThrow(() -> check("""
                {"id": "a", "when": null, "inner": null,
                 "items": [{"state": "REAL", "kWh": 1.5}]}
                """));
        assertDoesNotThrow(() -> check("""
                {"id": "a", "when": "2026-10-25T02:00:00+01:00", "inner": {"state": "ESTIMATED", "kWh": 0},
                 "items": []}
                """));
    }

    @Test
    void aMissingOrUndeclaredPropertyFails() {
        assertFails("""
                {"id": "a", "when": null, "items": []}
                """, "$ carries");
        assertFails("""
                {"id": "a", "when": null, "inner": null, "items": [], "extra": 1}
                """, "$ carries");
    }

    @Test
    void aNullWhereTheSchemaDoesNotAdmitOneFails() {
        assertFails("""
                {"id": "a", "when": null, "inner": null, "items": [{"state": "REAL", "kWh": null}]}
                """, "$.items[0].kWh is null");
    }

    @Test
    void aValueOfTheWrongShapeFails() {
        assertFails("""
                {"id": "a", "when": "yesterday", "inner": null, "items": []}
                """, "$.when is not a date-time");
        assertFails("""
                {"id": "a", "when": null, "inner": {"state": "GUESSED", "kWh": 1}, "items": []}
                """, "$.inner.state is GUESSED");
        assertFails("""
                {"id": 1, "when": null, "inner": null, "items": []}
                """, "$.id is not a string");
    }

    private void assertFails(String body, String expectedProblem) {
        AssertionFailedError error = assertThrows(AssertionFailedError.class, () -> check(body));
        assertTrue(error.getMessage().contains(expectedProblem), error::getMessage);
    }

    private void check(String body) throws Exception {
        JsonNode document = objectMapper.readTree(DOCUMENT);
        ResponseContractAssertions.assertMatchesContract(document, "Outer", objectMapper.readTree(body));
    }
}

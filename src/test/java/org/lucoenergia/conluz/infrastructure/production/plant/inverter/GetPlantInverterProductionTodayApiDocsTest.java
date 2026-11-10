package org.lucoenergia.conluz.infrastructure.production.plant.inverter;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Today's inverter production of a plant, asserted against the OpenAPI document the application generates.
 */
class GetPlantInverterProductionTodayApiDocsTest extends BaseControllerTest {

    private static final String PATH = "/api/v1/plants/{plantId}/inverter-production/today";

    @Test
    @DisplayName("PIP-001 every field is required, only the status is never null, and the status has three values")
    void everyFieldIsRequiredAndOnlyTheStatusIsNeverNull() throws Exception {
        JsonNode schema = schema("PlantInverterProductionTodayResponse");

        assertEquals(List.of("lastUpdatedAt", "powerCurve", "stale", "status", "todayEnergyKWh"),
                sorted(textValues(schema.path("required"))));
        assertEquals(List.of("lastUpdatedAt", "powerCurve", "stale", "status", "todayEnergyKWh"),
                sorted(fieldNames(schema.path("properties"))));
        assertEquals(List.of("string"), textValues(type(schema, "status")));
        assertEquals(List.of("AVAILABLE", "NO_DATA_TODAY", "NOT_AVAILABLE"),
                textValues(schema.path("properties").path("status").path("enum")));
        assertEquals(List.of("number", "null"), textValues(type(schema, "todayEnergyKWh")));
        assertEquals(List.of("array", "null"), textValues(type(schema, "powerCurve")));
        assertEquals(List.of("string", "null"), textValues(type(schema, "lastUpdatedAt")));
        assertEquals(List.of("boolean", "null"), textValues(type(schema, "stale")));
        for (String field : List.of("todayEnergyKWh", "powerCurve", "lastUpdatedAt", "stale")) {
            assertTrue(description(schema, field).contains("Null unless the status is AVAILABLE"), field);
        }
    }

    @Test
    @DisplayName("PIP-003 a curve point has a required instant and a required power that is null for a gap")
    void aCurvePointHasARequiredInstantAndANullablePower() throws Exception {
        JsonNode schema = schema("PlantInverterPowerPointResponse");

        assertEquals(List.of("endsAt", "powerKW"), sorted(textValues(schema.path("required"))));
        assertEquals(List.of("endsAt", "powerKW"), sorted(fieldNames(schema.path("properties"))));
        assertEquals(List.of("string"), textValues(type(schema, "endsAt")));
        assertEquals("date-time", schema.path("properties").path("endsAt").path("format").asText());
        assertEquals(List.of("number", "null"), textValues(type(schema, "powerKW")));
        assertTrue(description(schema, "powerKW").contains("Null when the interval is longer than 15 minutes"));
    }

    @Test
    @DisplayName("PIP-002 PIP-003 PIP-004 the description states the inverter source, carry-over, gaps and staleness")
    void theDescriptionStatesTheRules() throws Exception {
        JsonNode operation = operation();
        String description = flat(operation.path("description").asText());

        assertEquals("getPlantInverterProductionToday", operation.path("operationId").asText());
        for (String statement : List.of(
                "This is inverter data",
                "It may differ from the distributor's figures",
                "never summed with the energy metrics",
                "is carry-over and counts as 0 kWh",
                "The run ends at the first different value",
                "An interval longer than 15 minutes is a gap: its point is null",
                "Every other field is null unless the status is AVAILABLE",
                "more than 60 minutes old",
                "rounded to 3 decimals")) {
            assertTrue(description.contains(statement), () -> "does not state \"" + statement + "\": " + description);
        }
    }

    @Test
    @DisplayName("PIP-005 describes who may read, and that no station code, provider code or credential is reported")
    void describesWhoMayRead() throws Exception {
        JsonNode operation = operation();
        String description = flat(operation.path("description").asText());

        for (String statement : List.of(
                "Required: the caller can manage the plant",
                "an active community admin of the plant's community",
                "not an admin is answered 403",
                "a platform admin who is not a member included, 404",
                "No station code, provider code or credential of the integration is reported")) {
            assertTrue(description.contains(statement), () -> "does not state \"" + statement + "\": " + description);
        }
        for (String status : List.of("200", "401", "403", "404")) {
            assertFalse(operation.path("responses").path(status).isMissingNode(), status);
        }
    }

    private static String flat(String description) {
        return description.replace("**", "").replace("`", "").replaceAll("\\s+", " ");
    }

    private static JsonNode type(JsonNode schema, String field) {
        return schema.path("properties").path(field).path("type");
    }

    private static String description(JsonNode schema, String field) {
        return schema.path("properties").path(field).path("description").asText();
    }

    private JsonNode operation() throws Exception {
        JsonNode operation = document().path("paths").path(PATH).path("get");
        assertFalse(operation.isMissingNode(), () -> "No get on " + PATH);
        return operation;
    }

    private JsonNode schema(String name) throws Exception {
        JsonNode schema = document().path("components").path("schemas").path(name);
        assertFalse(schema.isMissingNode(), () -> "No schema named " + name);
        return schema;
    }

    private JsonNode document() throws Exception {
        MvcResult result = mockMvc.perform(get("/api-docs")).andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private static List<String> textValues(JsonNode node) {
        List<String> values = new ArrayList<>();
        if (node.isTextual()) {
            values.add(node.asText());
        } else if (node.isArray()) {
            node.forEach(element -> values.add(element.asText()));
        }
        return values;
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private static List<String> sorted(List<String> values) {
        return values.stream().sorted().toList();
    }
}

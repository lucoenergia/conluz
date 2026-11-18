package org.lucoenergia.conluz.infrastructure.admin.supply.energytoday;

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
 * Today's assigned production of a supply point, asserted against the OpenAPI document the application generates.
 */
class GetSupplyEnergyTodayApiDocsTest extends BaseControllerTest {

    private static final String PATH = "/api/v1/supplies/{supplyId}/energy-today";
    private static final List<String> NULLABLE_FIELDS = List.of("assignedPowerCurve", "assignedEnergyKWh",
            "currentAssignedPowerKW", "currentPlantPowerKW", "partitionCoefficient", "lastUpdatedAt", "stale");

    @Test
    @DisplayName("PIP-006 the response carries only the production, which is required")
    void theResponseCarriesOnlyTheProduction() throws Exception {
        JsonNode schema = schema("SupplyEnergyTodayResponse");

        assertEquals(List.of("production"), textValues(schema.path("required")));
        assertEquals(List.of("production"), fieldNames(schema.path("properties")));
        assertEquals("#/components/schemas/SupplyProductionTodayResponse",
                schema.path("properties").path("production").path("$ref").asText());
    }

    @Test
    @DisplayName("PIP-006 PIP-007 every field is required, only the status is never null, and the status has four values in order")
    void everyFieldIsRequiredAndOnlyTheStatusIsNeverNull() throws Exception {
        JsonNode schema = schema("SupplyProductionTodayResponse");

        List<String> fields = List.of("assignedEnergyKWh", "assignedPowerCurve", "currentAssignedPowerKW",
                "currentPlantPowerKW", "lastUpdatedAt", "partitionCoefficient", "stale", "status");
        assertEquals(fields, sorted(textValues(schema.path("required"))));
        assertEquals(fields, sorted(fieldNames(schema.path("properties"))));
        assertEquals(List.of("string"), textValues(type(schema, "status")));
        assertEquals(List.of("NO_COEFFICIENT_TODAY", "NOT_AVAILABLE", "NO_DATA_TODAY", "AVAILABLE"),
                textValues(schema.path("properties").path("status").path("enum")));
        assertEquals(List.of("array", "null"), textValues(type(schema, "assignedPowerCurve")));
        assertEquals(List.of("string", "null"), textValues(type(schema, "lastUpdatedAt")));
        assertEquals(List.of("boolean", "null"), textValues(type(schema, "stale")));
        for (String field : List.of("assignedEnergyKWh", "currentAssignedPowerKW", "currentPlantPowerKW",
                "partitionCoefficient")) {
            assertEquals(List.of("number", "null"), textValues(type(schema, field)), field);
        }
        for (String field : NULLABLE_FIELDS) {
            assertTrue(description(schema, field).contains("Null unless the status is AVAILABLE"), field);
        }
    }

    @Test
    @DisplayName("PIP-008 the assigned energy states that intervals longer than 15 minutes are included")
    void theAssignedEnergyStatesThatGapsAreIncluded() throws Exception {
        String description = description(schema("SupplyProductionTodayResponse"), "assignedEnergyKWh");

        assertTrue(description.contains("in force at the interval's start"), description);
        assertTrue(description.contains("Intervals longer than 15 minutes, the gaps of assignedPowerCurve, are " +
                "included"), description);
    }

    @Test
    @DisplayName("PIP-010 the partition coefficient states the single-plant rule, and nothing about counting plants")
    void thePartitionCoefficientStatesOnlyItsRule() throws Exception {
        String description = description(schema("SupplyProductionTodayResponse"), "partitionCoefficient");

        assertTrue(description.contains("With a single plant, the supply's partition coefficient in force now"),
                description);
        assertTrue(description.contains("With several plants, null, because the figures are summed over them"),
                description);
        for (String counting : List.of("count", "number of plants", "how many")) {
            assertFalse(description.toLowerCase().contains(counting), description);
        }
    }

    @Test
    @DisplayName("PIP-009 a curve point has a required slot end and a required power that is null for a gap")
    void aCurvePointHasARequiredSlotEndAndANullablePower() throws Exception {
        JsonNode schema = schema("SupplyAssignedPowerPointResponse");

        assertEquals(List.of("endsAt", "powerKW"), sorted(textValues(schema.path("required"))));
        assertEquals(List.of("endsAt", "powerKW"), sorted(fieldNames(schema.path("properties"))));
        assertEquals(List.of("string"), textValues(type(schema, "endsAt")));
        assertEquals("date-time", schema.path("properties").path("endsAt").path("format").asText());
        assertEquals(List.of("number", "null"), textValues(type(schema, "powerKW")));
        assertTrue(description(schema, "powerKW").contains("longer than 15 minutes"));
    }

    @Test
    @DisplayName("PIP-004 PIP-006 PIP-008 PIP-009 PIP-010 the description states the day, the rules and the staleness")
    void theDescriptionStatesTheRules() throws Exception {
        JsonNode operation = operation();
        String description = flat(operation.path("description").asText());

        assertEquals("getSupplyEnergyToday", operation.path("operationId").asText());
        assertEquals(List.of("Supplies"), textValues(operation.path("tags")));
        for (String statement : List.of(
                "\"Today\" is the current local day in the community's time zone",
                "the first that applies",
                "Every other field of production is null unless the status is AVAILABLE",
                "one interval from local midnight to the first snapshot of today",
                "with the same carry-over rule as getPlantInverterProductionToday",
                "in force at the interval's start, 0 when none is",
                "Intervals longer than 15 minutes, the gaps of the curve, are included",
                "one point per fixed 5-minute slot from local midnight",
                "in proportion to the time overlapped",
                "With several plants, energy and power are the sums over them",
                "more than 15 minutes old; one exactly 15 minutes old is not stale",
                "rounded to 3 decimals, after computing")) {
            assertTrue(description.contains(statement), () -> "does not state \"" + statement + "\": " + description);
        }
    }

    @Test
    @DisplayName("PIP-011 describes who may read, and that everyone else is answered 404")
    void describesWhoMayRead() throws Exception {
        JsonNode operation = operation();
        String description = flat(operation.path("description").asText());

        for (String statement : List.of(
                "Required: the caller can read the supply",
                "its owner, while their membership in the supply's community is active",
                "an active community admin of that community",
                "a platform admin who is not a member included, is answered 404",
                "No plant, station code, provider code or credential is reported")) {
            assertTrue(description.contains(statement), () -> "does not state \"" + statement + "\": " + description);
        }
        for (String status : List.of("200", "401", "404")) {
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

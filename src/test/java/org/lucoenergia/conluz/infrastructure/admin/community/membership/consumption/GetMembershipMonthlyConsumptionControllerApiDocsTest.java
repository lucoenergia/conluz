package org.lucoenergia.conluz.infrastructure.admin.community.membership.consumption;

import com.fasterxml.jackson.databind.JsonNode;
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
 * AC10 of #318, asserted against the OpenAPI document the application actually generates rather
 * than against the annotations meant to produce it: under OpenAPI 3.1 springdoc silently drops
 * {@code @Schema(nullable = true)}.
 *
 * <p>Every field is required -- its key is always present -- and nullability is a separate axis,
 * expressed as a {@code null} entry next to the base type.
 */
class GetMembershipMonthlyConsumptionControllerApiDocsTest extends BaseControllerTest {

    private static final String PATH = "/api/v1/communities/{communityId}/memberships/{userId}/consumption/monthly";
    private static final String BUCKET = "MembershipMonthlyConsumptionBucketResponse";

    private static final List<String> FIELDS = List.of("date", "time", "consumptionKWh", "surplusEnergyKWh",
            "generationEnergyKWh", "selfConsumptionEnergyKWh", "savingsEur", "tariffSource", "supplyCount",
            "suppliesWithData");

    @Test
    void documentsEveryFieldAsRequired() throws Exception {
        JsonNode schema = schema(BUCKET);
        List<String> required = textValues(schema.path("required"));
        List<String> properties = fieldNames(schema.path("properties"));

        assertEquals(sorted(FIELDS), sorted(required), () -> BUCKET + ".required was " + required);
        assertEquals(sorted(FIELDS), sorted(properties), () -> BUCKET + ".properties was " + properties);
    }

    @Test
    void documentsTheSavingsAndTheirSourceAsNullableWithTheirBaseType() throws Exception {
        assertNullableWithBaseType("savingsEur", "number");
        assertNullableWithBaseType("tariffSource", "string");
    }

    @Test
    void documentsEveryOtherFieldAsNotNullable() throws Exception {
        for (String field : FIELDS) {
            if (!List.of("savingsEur", "tariffSource").contains(field)) {
                JsonNode property = schema(BUCKET).path("properties").path(field);
                assertFalse(textValues(property.path("type")).contains("null"),
                        () -> BUCKET + "." + field + " must not be nullable: " + property);
            }
        }
    }

    @Test
    void neverEmitsTheDroppedNullableKeyword() throws Exception {
        JsonNode schema = schema(BUCKET);
        assertFalse(schema.toString().contains("\"nullable\""), () -> BUCKET + " carries a nullable keyword: " + schema);
    }

    @Test
    void returnsAnArrayOfBuckets() throws Exception {
        JsonNode body = operation().path("responses").path("200").path("content").path("application/json")
                .path("schema");

        assertEquals(List.of("array"), textValues(body.path("type")), () -> "200 body: " + body);
        assertTrue(body.path("items").path("$ref").asText().endsWith("/" + BUCKET),
                () -> "200 items are not " + BUCKET + ": " + body);
    }

    @Test
    void takesBothBoundsAsRequiredQueryParameters() throws Exception {
        List<String> required = new ArrayList<>();
        for (JsonNode parameter : operation().path("parameters")) {
            if ("query".equals(parameter.path("in").asText()) && parameter.path("required").asBoolean()) {
                required.add(parameter.path("name").asText());
            }
        }
        assertEquals(List.of("endDate", "startDate"), sorted(required));
    }

    @Test
    void documentsTheOperationWithItsErrorStatuses() throws Exception {
        JsonNode responses = operation().path("responses");

        for (String status : List.of("200", "400", "401", "403", "404", "500")) {
            assertFalse(responses.path(status).isMissingNode(), () -> "no " + status + ": " + fieldNames(responses));
        }
        assertEquals("getMembershipMonthlyConsumption", operation().path("operationId").asText());
    }

    /**
     * The statements #318 requires of the operation description: every month emitted unlike the
     * per-supply series, the current supply count and its consequence, a null amount as against a
     * zero, the unit of suppliesWithData, the time zone, and who may read it.
     */
    @Test
    void describesTheMonthsTheCountersTheSavingsTheTimeZoneAndAuthorization() throws Exception {
        // Line breaks and markdown emphasis are presentation, not wording.
        String description = operation().path("description").asText()
                .replace("**", "")
                .replaceAll("\\s+", " ");

        for (String statement : List.of(
                "A bucket is returned for every month in range",
                "which omits months without a stored record",
                "the number of supplies the member currently owns in the community",
                "the months before a supply started reporting show as incomplete",
                "It is `null` when no supply stored a record that month",
                "reports `0.00`",
                "`suppliesWithData` is measured from stored monthly records",
                "local calendar months of the community's time zone",
                "it is `null` exactly when `savingsEur` is",
                "Platform admins are not granted access")) {
            assertTrue(description.contains(statement), () -> "does not state \"" + statement + "\": " + description);
        }
    }

    /**
     * The field descriptions carry the two statements a reader of the schema alone needs.
     */
    @Test
    void describesWhenTheSavingsAreNullAndTheUnitOfSuppliesWithData() throws Exception {
        JsonNode properties = schema(BUCKET).path("properties");

        assertTrue(properties.path("savingsEur").path("description").asText()
                .contains("Null when no supply stored a record that month"));
        assertTrue(properties.path("tariffSource").path("description").asText()
                .contains("Null exactly when savingsEur is null"));
        assertTrue(properties.path("suppliesWithData").path("description").asText()
                .contains("Measured from stored monthly records, month by month"));
    }

    private void assertNullableWithBaseType(String field, String baseType) throws Exception {
        JsonNode property = schema(BUCKET).path("properties").path(field);
        List<String> types = textValues(property.path("type"));

        assertTrue(types.contains("null"), () -> BUCKET + "." + field + " is not nullable: " + property);
        assertTrue(types.contains(baseType), () -> BUCKET + "." + field + " lost its base type: " + property);
    }

    private JsonNode operation() throws Exception {
        JsonNode operation = document().path("paths").path(PATH).path("get");
        assertFalse(operation.isMissingNode(), () -> "No get on " + PATH);
        return operation;
    }

    private JsonNode schema(String name) throws Exception {
        JsonNode schema = document().path("components").path("schemas").path(name);
        assertFalse(schema.isMissingNode(), () -> "No schema named " + name + " in the document");
        return schema;
    }

    private JsonNode document() throws Exception {
        MvcResult result = mockMvc.perform(get("/api-docs"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    /**
     * OpenAPI 3.1 lets `type` be either a single string or an array of them, and `required` is an
     * array. Reading both shapes the same way keeps the assertions about what the document says.
     */
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

package org.lucoenergia.conluz.infrastructure.admin.community.membership.consumption;

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
 * The contract of the membership daily series, asserted against the OpenAPI document the
 * application actually generates rather than against the annotations meant to produce it.
 *
 * <p>Every field is required -- its key is always present -- and nullability is a separate axis,
 * expressed as a {@code null} entry next to the base type.
 */
class GetMembershipDailyConsumptionControllerApiDocsTest extends BaseControllerTest {

    private static final String PATH = "/api/v1/communities/{communityId}/memberships/{userId}/consumption/daily";
    private static final String BUCKET = "MembershipDailyConsumptionBucketResponse";

    private static final List<String> FIELDS = List.of("date", "time", "gridImportKWh", "surplusKWh",
            "selfConsumptionKWh", "assignedProductionKWh", "savingsEur", "tariffSource", "supplyCount",
            "suppliesWithData");

    @Test
    @DisplayName("MCS-002 the daily bucket has the monthly bucket's fields, every one required")
    void hasTheMonthlyBucketsFieldsEveryOneRequired() throws Exception {
        JsonNode schema = schema(BUCKET);
        List<String> required = textValues(schema.path("required"));
        List<String> properties = fieldNames(schema.path("properties"));
        List<String> monthlyProperties = fieldNames(schema("MembershipMonthlyConsumptionBucketResponse")
                .path("properties"));

        assertEquals(sorted(FIELDS), sorted(required), () -> BUCKET + ".required was " + required);
        assertEquals(sorted(FIELDS), sorted(properties), () -> BUCKET + ".properties was " + properties);
        assertEquals(sorted(monthlyProperties), sorted(properties));
    }

    @Test
    @DisplayName("MCS-001 the savings and their source are nullable, with their base type, and nothing else is")
    void onlyTheSavingsAndTheirSourceAreNullable() throws Exception {
        JsonNode properties = schema(BUCKET).path("properties");
        for (String field : FIELDS) {
            List<String> types = textValues(properties.path(field).path("type"));
            if (List.of("savingsEur", "tariffSource").contains(field)) {
                assertTrue(types.contains("null"), () -> field + " is not nullable: " + types);
                assertTrue(types.size() == 2, () -> field + " lost its base type: " + types);
            } else {
                assertFalse(types.contains("null"), () -> field + " must not be nullable: " + types);
            }
        }
        assertFalse(schema(BUCKET).toString().contains("\"nullable\""));
    }

    @Test
    void returnsAnArrayOfBucketsAndTakesBothBoundsAsRequired() throws Exception {
        JsonNode body = operation().path("responses").path("200").path("content").path("application/json")
                .path("schema");
        assertEquals(List.of("array"), textValues(body.path("type")), () -> "200 body: " + body);
        assertTrue(body.path("items").path("$ref").asText().endsWith("/" + BUCKET),
                () -> "200 items are not " + BUCKET + ": " + body);

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
        assertEquals("getMembershipDailyConsumption", operation().path("operationId").asText());
    }

    @Test
    @DisplayName("MCS-001 MCS-002 the description states every day emitted, the counts, and a gap as against a measured zero")
    void describesTheDaysTheCountsAndTheGapAgainstAMeasuredZero() throws Exception {
        // Line breaks and markdown emphasis are presentation, not wording.
        String description = operation().path("description").asText()
                .replace("**", "")
                .replaceAll("\\s+", " ");

        for (String statement : List.of(
                "A bucket is returned for every local calendar day of the community's time zone",
                "cover 23 and 25 hours",
                "a period starting at 12:00 counts only that day's hours from 12:00",
                "the number of supplies the member currently owns in the community",
                "how many of them stored at least one hourly record that day",
                "It is `null` when no supply stored a record that day (`suppliesWithData` is 0), and only then",
                "reports `0.00`",
                "it is `null` exactly when `savingsEur` is",
                "Platform admins are not granted access")) {
            assertTrue(description.contains(statement), () -> "does not state \"" + statement + "\": " + description);
        }
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

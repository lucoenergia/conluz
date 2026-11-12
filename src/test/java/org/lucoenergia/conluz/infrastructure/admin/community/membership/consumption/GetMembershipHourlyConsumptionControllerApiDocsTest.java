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
 * The contract of the membership hourly series, asserted against the OpenAPI document the
 * application actually generates rather than against the annotations meant to produce it.
 *
 * <p>Every field is required -- its key is always present -- and nullability is a separate axis,
 * expressed as a {@code null} entry next to the base type.
 */
class GetMembershipHourlyConsumptionControllerApiDocsTest extends BaseControllerTest {

    private static final String PATH = "/api/v1/communities/{communityId}/memberships/{userId}/consumption/hourly";
    private static final String BUCKET = "MembershipHourlyConsumptionBucketResponse";

    private static final List<String> FIELDS = List.of("startsAt", "date", "time", "gridImportKWh", "surplusKWh",
            "selfConsumptionKWh", "assignedProductionKWh", "savingsEur", "tariffSource", "supplyCount",
            "suppliesWithData");

    @Test
    @DisplayName("MCS-003 the hourly bucket has the daily bucket's fields plus startsAt, every one required")
    void hasTheDailyBucketsFieldsPlusStartsAtEveryOneRequired() throws Exception {
        JsonNode schema = schema(BUCKET);
        List<String> required = textValues(schema.path("required"));
        List<String> properties = fieldNames(schema.path("properties"));
        List<String> dailyProperties = new ArrayList<>(fieldNames(schema("MembershipDailyConsumptionBucketResponse")
                .path("properties")));
        dailyProperties.add("startsAt");

        assertEquals(sorted(FIELDS), sorted(required), () -> BUCKET + ".required was " + required);
        assertEquals(sorted(FIELDS), sorted(properties), () -> BUCKET + ".properties was " + properties);
        assertEquals(sorted(dailyProperties), sorted(properties));
    }

    @Test
    @DisplayName("MCS-003 the savings and their source are nullable, with their base type, and nothing else is")
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
    @DisplayName("MCS-003 startsAt is a date-time")
    void startsAtIsADateTime() throws Exception {
        JsonNode startsAt = schema(BUCKET).path("properties").path("startsAt");

        assertEquals(List.of("string"), textValues(startsAt.path("type")));
        assertEquals("date-time", startsAt.path("format").asText());
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
        assertEquals("getMembershipHourlyConsumption", operation().path("operationId").asText());
    }

    @Test
    @DisplayName("EPR-005 MCS-003 the description states the cap, every hour emitted, startsAt as the key, the counts, and a gap as against a measured zero")
    void describesTheCapTheHoursTheKeyTheCountsAndTheGapAgainstAMeasuredZero() throws Exception {
        // Line breaks and markdown emphasis are presentation, not wording.
        String description = operation().path("description").asText()
                .replace("**", "")
                .replaceAll("\\s+", " ");

        for (String statement : List.of(
                "the limit of rule 5 is 31 calendar days",
                "A bucket is returned for every whole hour that starts inside the period",
                "23 on the day the clocks go forward and 25 on the day they go back",
                "It is unique within the series and is the key to identify an hour by",
                "the number of supplies the member currently owns in the community",
                "how many of them have data that hour",
                "its record of that hour carries self-consumption",
                "It is `null` when no supply has data that hour (`suppliesWithData` is 0), and only then",
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

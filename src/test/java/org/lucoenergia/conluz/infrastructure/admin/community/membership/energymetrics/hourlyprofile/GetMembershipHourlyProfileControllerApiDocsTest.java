package org.lucoenergia.conluz.infrastructure.admin.community.membership.energymetrics.hourlyprofile;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.consumption.ReferenceMonthResolver;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AC12 of #317, asserted against the OpenAPI document the application actually generates rather
 * than against the annotations meant to produce it: under OpenAPI 3.1 springdoc silently drops
 * {@code @Schema(nullable = true)}.
 *
 * <p>Every field is required -- its key is always present -- and nullability is a separate axis,
 * expressed as a {@code null} entry next to the base type.
 */
class GetMembershipHourlyProfileControllerApiDocsTest extends BaseControllerTest {

    private static final String PATH =
            "/api/v1/communities/{communityId}/memberships/{userId}/energy-metrics/hourly-profile";

    private static final String RESPONSE = "MembershipHourlyProfileResponse";
    private static final String BUCKET = "MembershipHourlyProfileBucketResponse";
    private static final String COVERAGE = "MembershipEnergyMetricsCoverageResponse";
    private static final String PERIOD = "SupplyEnergyMetricsPeriodResponse";

    @Test
    void documentsEveryFieldOfEverySchemaAsRequired() throws Exception {
        Map<String, List<String>> expected = Map.of(
                RESPONSE, List.of("period", "coverage", "buckets"),
                BUCKET, List.of("hour", "averageConsumptionKWh", "consumptionSampleCount",
                        "averageAssignedProductionKWh", "assignedProductionSampleCount"),
                COVERAGE, List.of("hoursWithData", "expectedHours", "supplyCount", "suppliesWithData"),
                PERIOD, List.of("startDate", "endDate"));

        for (Map.Entry<String, List<String>> entry : expected.entrySet()) {
            JsonNode schema = schema(entry.getKey());
            List<String> required = textValues(schema.path("required"));
            List<String> properties = fieldNames(schema.path("properties"));

            assertEquals(sorted(entry.getValue()), sorted(required),
                    () -> entry.getKey() + ".required was " + required);
            assertEquals(sorted(properties), sorted(required),
                    () -> entry.getKey() + " has a property that is not required: " + properties);
        }
    }

    @Test
    void documentsTheAveragesAndTheBoundsAsNullableWithTheirBaseType() throws Exception {
        assertNullableWithBaseType(BUCKET, "averageConsumptionKWh", "number");
        assertNullableWithBaseType(BUCKET, "averageAssignedProductionKWh", "number");
        assertNullableWithBaseType(PERIOD, "startDate", "string");
        assertNullableWithBaseType(PERIOD, "endDate", "string");
    }

    @Test
    void documentsTheHourAndTheSampleCountsAsNotNullable() throws Exception {
        for (String field : List.of("hour", "consumptionSampleCount", "assignedProductionSampleCount")) {
            assertNotNullable(BUCKET, field);
        }
        for (String field : List.of("period", "coverage", "buckets")) {
            assertNotNullable(RESPONSE, field);
        }
    }

    @Test
    void documentsTheBucketsAsAnArrayOfBuckets() throws Exception {
        JsonNode buckets = schema(RESPONSE).path("properties").path("buckets");

        assertEquals(List.of("array"), textValues(buckets.path("type")), () -> "buckets: " + buckets);
        assertTrue(buckets.path("items").path("$ref").asText().endsWith("/" + BUCKET),
                () -> "buckets items are not " + BUCKET + ": " + buckets);
    }

    @Test
    void neverEmitsTheDroppedNullableKeyword() throws Exception {
        for (String name : List.of(RESPONSE, BUCKET)) {
            JsonNode schema = schema(name);
            assertFalse(schema.toString().contains("\"nullable\""),
                    () -> name + " carries a nullable keyword: " + schema);
        }
    }

    @Test
    void takesNoQueryParameters() throws Exception {
        for (JsonNode parameter : operation().path("parameters")) {
            assertEquals("path", parameter.path("in").asText(), () -> "unexpected parameter " + parameter);
        }
    }

    @Test
    void documentsTheOperationWithItsErrorStatuses() throws Exception {
        JsonNode responses = operation().path("responses");

        for (String status : List.of("200", "400", "401", "404")) {
            assertFalse(responses.path(status).isMissingNode(), () -> "no " + status + ": " + fieldNames(responses));
        }
        assertEquals("getMembershipHourlyProfile", operation().path("operationId").asText());
    }

    /**
     * The statements #317 requires of the operation description: a period that is always the
     * latest published month and cannot be chosen, averages that divide by the records found,
     * sample counts that can differ, an hour local to the community, null as against zero, the
     * daylight saving behaviour, and who may read it.
     */
    @Test
    void describesThePeriodTheAveragingTheHourNullVersusZeroDaylightSavingAndAuthorization() throws Exception {
        // Line breaks and markdown emphasis are presentation, not wording.
        String description = operation().path("description").asText()
                .replace("**", "")
                .replaceAll("\\s+", " ");

        for (String statement : List.of(
                "Always the latest published month",
                "cannot be chosen",
                "divides by the records found",
                "the two can differ",
                "local to the community",
                "which is never the same as `0`",
                "The counts are sample counts, not day counts",
                "Platform admins are not granted access")) {
            assertTrue(description.contains(statement), () -> "does not state \"" + statement + "\": " + description);
        }
    }

    /**
     * #382: the rule by which a month counts as published, with the threshold the resolver applies,
     * and what happens when no month qualifies.
     */
    @Test
    @DisplayName("ENM-001 AC7 describes when a month counts as published and what happens when none does")
    void describesWhenAMonthCountsAsPublishedAndWhatHappensWhenNoneDoes() throws Exception {
        String description = operation().path("description").asText()
                .replace("**", "")
                .replaceAll("\\s+", " ");

        for (String statement : List.of(
                "published when it carries self-consumed energy, zero included",
                "surplus alone does not count",
                "at least " + ReferenceMonthResolver.PUBLISHED_HOURS_MIN_PERCENT + "% of the hours it could have published",
                "the supply's first published record",
                "no published month in the search window")) {
            assertTrue(description.contains(statement), () -> "does not state \"" + statement + "\": " + description);
        }
    }

    private void assertNullableWithBaseType(String schemaName, String field, String baseType) throws Exception {
        JsonNode property = schema(schemaName).path("properties").path(field);
        List<String> types = textValues(property.path("type"));

        assertTrue(types.contains("null"), () -> schemaName + "." + field + " is not nullable: " + property);
        assertTrue(types.contains(baseType), () -> schemaName + "." + field + " lost its base type: " + property);
    }

    private void assertNotNullable(String schemaName, String field) throws Exception {
        JsonNode property = schema(schemaName).path("properties").path(field);

        assertFalse(property.isMissingNode(), () -> schemaName + " has no property " + field);
        assertFalse(textValues(property.path("type")).contains("null"),
                () -> schemaName + "." + field + " must not be nullable: " + property);
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

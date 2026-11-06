package org.lucoenergia.conluz.infrastructure.admin.community.energy;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
 * The three community energy series, asserted against the OpenAPI document the application generates.
 */
class GetCommunityEnergySeriesControllersApiDocsTest extends BaseControllerTest {

    private static final String PATH = "/api/v1/communities/{communityId}/energy-series/";

    @ParameterizedTest
    @CsvSource({
            "hourly, getCommunityHourlyEnergySeries, CommunityHourlyEnergyBucketResponse",
            "daily, getCommunityDailyEnergySeries, CommunityDailyEnergyBucketResponse",
            "monthly, getCommunityMonthlyEnergySeries, CommunityMonthlyEnergyBucketResponse"})
    void returnsAnArrayOfBucketsAndTakesBothBoundsAsRequired(String series, String operationId, String bucket)
            throws Exception {
        JsonNode operation = operation(series);

        assertEquals(operationId, operation.path("operationId").asText());
        JsonNode schema = operation.path("responses").path("200").path("content").path("application/json")
                .path("schema");
        assertEquals("array", schema.path("type").asText());
        assertTrue(schema.path("items").path("$ref").asText().endsWith("/" + bucket));
        for (String date : List.of("startDate", "endDate")) {
            assertTrue(parameter(operation.path("parameters"), date).path("required").asBoolean(), date);
        }
        for (String status : List.of("200", "400", "401", "403", "404")) {
            assertFalse(operation.path("responses").path(status).isMissingNode(), status);
        }
    }

    @ParameterizedTest
    @CsvSource({
            "CommunityHourlyEnergyBucketResponse, true",
            "CommunityDailyEnergyBucketResponse, false",
            "CommunityMonthlyEnergyBucketResponse, false"})
    @DisplayName("CEM-006 every field is required, and only the energy figures are nullable, for a gap")
    void everyFieldIsRequiredAndOnlyTheEnergyFiguresAreNullable(String bucket, boolean hourly) throws Exception {
        JsonNode schema = schema(bucket);
        List<String> expected = new ArrayList<>(List.of("date", "time", "selfConsumptionKWh", "surplusKWh",
                "supplyCount", "suppliesWithData"));
        if (hourly) {
            expected.add("startsAt");
        }

        assertEquals(sorted(expected), sorted(textValues(schema.path("required"))));
        assertEquals(sorted(expected), sorted(fieldNames(schema.path("properties"))));
        for (String field : expected) {
            List<String> types = textValues(schema.path("properties").path(field).path("type"));
            boolean energy = field.endsWith("KWh");
            assertEquals(energy, types.contains("null"), () -> bucket + "." + field + " has types " + types);
            if (energy) {
                assertTrue(types.contains("number"));
                assertTrue(description(schema, field).contains("In kWh"));
                assertTrue(description(schema, field).contains("Null when"));
            }
        }
    }

    @ParameterizedTest
    @CsvSource({"hourly", "daily", "monthly"})
    @DisplayName("CEM-006 CEM-007 describes the gaps, the completeness counts and who may read")
    void describesTheGapsTheCountsAndWhoMayRead(String series) throws Exception {
        String description = operation(series).path("description").asText()
                .replace("**", "")
                .replaceAll("\\s+", " ");

        for (String statement : List.of(
                "has `null` energy figures and a `suppliesWithData` of 0, never zeros",
                "`supplyCount` is the number of enabled supplies of the community",
                "Readable by any active member of the community",
                "A platform admin who is not a member is answered 403",
                "no figure, identifier or code of a single supply or member")) {
            assertTrue(description.contains(statement), () -> "does not state \"" + statement + "\": " + description);
        }
    }

    private static String description(JsonNode schema, String field) {
        return schema.path("properties").path(field).path("description").asText();
    }

    private static JsonNode parameter(JsonNode parameters, String name) {
        for (JsonNode parameter : parameters) {
            if (name.equals(parameter.path("name").asText())) {
                return parameter;
            }
        }
        throw new AssertionError("No parameter named " + name + " in " + parameters);
    }

    private JsonNode operation(String series) throws Exception {
        JsonNode operation = document().path("paths").path(PATH + series).path("get");
        assertFalse(operation.isMissingNode(), () -> "No get on " + PATH + series);
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

package org.lucoenergia.conluz.infrastructure.admin.community.membership.energymetrics;

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
 * Asserted against the OpenAPI document the application actually generates rather than against the
 * annotations meant to produce it: under OpenAPI 3.1 springdoc silently drops
 * {@code @Schema(nullable = true)}, so a field can look nullable in the source and not be nullable
 * in the contract clients are generated from.
 *
 * <p>Every field is required -- its key is always present -- and nullability is a separate axis,
 * expressed as a {@code null} entry next to the base type.
 */
class GetMembershipEnergyMetricsControllerApiDocsTest extends BaseControllerTest {

    private static final String PATH = "/api/v1/communities/{communityId}/memberships/{userId}/energy-metrics";

    private static final String RESPONSE = "MembershipEnergyMetricsResponse";
    private static final String COVERAGE = "MembershipEnergyMetricsCoverageResponse";
    private static final String ENERGY = "MembershipEnergyMetricsEnergyResponse";
    private static final String SAVINGS = "MembershipEnergyMetricsSavingsResponse";
    private static final String PERIOD = "SupplyEnergyMetricsPeriodResponse";
    private static final String ESTIMATED_PRICE = "EstimatedPriceResponse";

    @Test
    void documentsEveryFieldOfEverySchemaAsRequired() throws Exception {
        Map<String, List<String>> expected = Map.of(
                RESPONSE, List.of("period", "coverage", "energy", "savings", "selfSufficiencyRatio",
                        "selfConsumptionRatio"),
                COVERAGE, List.of("hoursWithData", "expectedHours", "supplyCount", "suppliesWithData"),
                ENERGY, List.of("totalConsumptionKWh", "gridImportKWh", "selfConsumptionKWh", "surplusKWh",
                        "assignedProductionKWh"),
                SAVINGS, List.of("amountEur", "tariffSource", "estimatedPrice"),
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
    void documentsTheNullableFieldsWithTheirBaseTypeAndNull() throws Exception {
        assertNullableWithBaseType(RESPONSE, "selfSufficiencyRatio", "number");
        assertNullableWithBaseType(RESPONSE, "selfConsumptionRatio", "number");
        assertNullableWithBaseType(SAVINGS, "amountEur", "number");
        assertNullableWithBaseType(PERIOD, "startDate", "string");
        assertNullableWithBaseType(PERIOD, "endDate", "string");
    }

    @Test
    void documentsTheFieldsThatAreNeverNullAsNotNullable() throws Exception {
        for (String field : List.of("hoursWithData", "expectedHours", "supplyCount", "suppliesWithData")) {
            assertNotNullable(COVERAGE, field);
        }
        for (String field : List.of("totalConsumptionKWh", "gridImportKWh", "selfConsumptionKWh", "surplusKWh",
                "assignedProductionKWh")) {
            assertNotNullable(ENERGY, field);
        }
        assertNotNullable(SAVINGS, "tariffSource");
    }

    /**
     * A required, nullable reference to the shared estimated-price schema; nullability lives on the
     * reference alone.
     */
    @Test
    void documentsTheEstimatedPriceAsANullableReferenceToANonNullablePrice() throws Exception {
        JsonNode estimatedPrice = schema(SAVINGS).path("properties").path("estimatedPrice");

        assertTrue(estimatedPrice.path("$ref").asText().endsWith("/" + ESTIMATED_PRICE),
                () -> "estimatedPrice is not a reference to " + ESTIMATED_PRICE + ": " + estimatedPrice);
        assertTrue(textValues(estimatedPrice.path("type")).contains("null"),
                () -> "estimatedPrice must be nullable: " + estimatedPrice);
        assertNotNullable(ESTIMATED_PRICE, "eurPerKWh");
    }

    @Test
    void neverEmitsTheDroppedNullableKeyword() throws Exception {
        for (String name : List.of(RESPONSE, COVERAGE, ENERGY, SAVINGS, PERIOD)) {
            JsonNode schema = schema(name);
            assertFalse(schema.toString().contains("\"nullable\""),
                    () -> name + " carries a nullable keyword: " + schema);
        }
    }

    @Test
    void documentsTheReferencePeriodAsAnEnumQueryParameterBesideTheTwoDates() throws Exception {
        JsonNode parameters = operation().path("parameters");

        JsonNode period = parameter(parameters, "period");
        assertEquals("query", period.path("in").asText());
        assertFalse(period.path("required").asBoolean(), () -> "period must be optional: " + period);
        assertEquals(List.of("LATEST_PUBLISHED_MONTH"), textValues(period.path("schema").path("enum")),
                () -> "period enum was " + period);

        for (String date : List.of("startDate", "endDate")) {
            JsonNode parameter = parameter(parameters, date);
            assertEquals("query", parameter.path("in").asText());
            assertFalse(parameter.path("required").asBoolean(), () -> date + " must be optional: " + parameter);
        }
    }

    @Test
    void documentsTheOperationWithItsErrorStatuses() throws Exception {
        JsonNode responses = operation().path("responses");

        for (String status : List.of("200", "400", "401", "404")) {
            assertFalse(responses.path(status).isMissingNode(), () -> "no " + status + ": " + fieldNames(responses));
        }
        assertEquals("getMembershipEnergyMetrics", operation().path("operationId").asText());
    }

    /**
     * The member home page relies on these three statements to explain a partial figure: the
     * coverage summation across every supply, the savings rule, and who may read it.
     */
    @Test
    void describesTheCoverageSummationTheSavingsRuleAndTheAuthorizationRule() throws Exception {
        // Line breaks and markdown emphasis are presentation, not wording.
        String description = operation().path("description").asText()
                .replace("**", "")
                .replaceAll("\\s+", " ");

        assertTrue(description.contains("times the number of supplies of the membership"),
                () -> "does not state that expected hours span every supply: " + description);
        assertTrue(description.contains("lowers coverage"),
                () -> "does not state that a silent supply lowers coverage: " + description);
        assertTrue(description.contains("only when no period could be resolved"),
                () -> "does not state the savings rule: " + description);
        assertTrue(description.contains("Platform admins are not granted access"),
                () -> "does not state the authorization rule: " + description);
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
                "even if another of the member's supplies is still unpublished",
                "When no month in the search window is published, no period is resolved")) {
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

    private static JsonNode parameter(JsonNode parameters, String name) {
        for (JsonNode parameter : parameters) {
            if (name.equals(parameter.path("name").asText())) {
                return parameter;
            }
        }
        throw new AssertionError("No parameter named " + name + " in " + parameters);
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
     * OpenAPI 3.1 lets `type` be either a single string or an array of them, and `required` and
     * `enum` are arrays. Reading both shapes the same way keeps the assertions about what the
     * document says, not about how it happens to be spelled.
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

package org.lucoenergia.conluz.infrastructure.admin.community.energy;

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
 * Asserted against the OpenAPI document the application generates, as
 * {@code GetMembershipEnergyMetricsControllerApiDocsTest} does for the membership.
 */
class GetCommunityEnergyMetricsControllerApiDocsTest extends BaseControllerTest {

    private static final String PATH = "/api/v1/communities/{communityId}/energy-metrics";

    private static final String RESPONSE = "CommunityEnergyMetricsResponse";
    private static final String ENERGY = "CommunityEnergyMetricsEnergyResponse";
    private static final String COVERAGE = "CommunityEnergyMetricsCoverageResponse";
    private static final String PLANT = "CommunityPlantEnergyResponse";
    private static final String SURPLUS_INCOME = "CommunityEnergyMetricsSurplusIncomeResponse";
    private static final String MEMBERSHIP_ENERGY = "MembershipEnergyMetricsEnergyResponse";
    private static final String MEMBERSHIP_COVERAGE = "MembershipEnergyMetricsCoverageResponse";

    @Test
    void documentsEveryFieldOfEverySchemaAsRequired() throws Exception {
        Map<String, List<String>> expected = Map.of(
                RESPONSE, List.of("period", "energy", "selfConsumptionRatio", "surplusRatio", "installedPowerKWp",
                        "coverage", "plants", "surplusIncome"),
                ENERGY, List.of("productionKWh", "selfConsumptionKWh", "surplusKWh"),
                COVERAGE, List.of("supplyCount", "suppliesWithData"),
                PLANT, List.of("plant", "installedPowerKWp", "productionKWh", "productionShare"),
                SURPLUS_INCOME, List.of("amountEur", "unpricedSurplusKWh"));

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
    @DisplayName("CEM-005 CEM-006 a gap is documented as null, never as 0")
    void documentsTheNullableFieldsWithTheirBaseTypeAndNull() throws Exception {
        assertNullableWithBaseType(RESPONSE, "selfConsumptionRatio", "number");
        assertNullableWithBaseType(RESPONSE, "surplusRatio", "number");
        for (String field : List.of("productionKWh", "selfConsumptionKWh", "surplusKWh")) {
            assertNullableWithBaseType(ENERGY, field, "number");
        }
        assertNullableWithBaseType(PLANT, "productionKWh", "number");
        assertNullableWithBaseType(PLANT, "productionShare", "number");
        assertNotNullable(RESPONSE, "installedPowerKWp");
        assertNotNullable(PLANT, "installedPowerKWp");
        assertNotNullable(COVERAGE, "supplyCount");
        assertNotNullable(COVERAGE, "suppliesWithData");
    }

    @Test
    @DisplayName("CEM-008 CEM-009 CEM-010 the surplus income is always present, and its figures are numbers or null")
    void documentsTheSurplusIncomeAsAlwaysPresentWithNullableFigures() throws Exception {
        assertTrue(schema(RESPONSE).path("properties").path("surplusIncome").path("$ref").asText()
                .endsWith("/" + SURPLUS_INCOME));
        assertNullableWithBaseType(SURPLUS_INCOME, "amountEur", "number");
        assertNullableWithBaseType(SURPLUS_INCOME, "unpricedSurplusKWh", "number");
    }

    @Test
    @DisplayName("CEM-008 CEM-009 the surplus income is described as an OMIE day-ahead estimate that may be negative")
    void describesTheSurplusIncomeAsAnOmieDayAheadEstimate() throws Exception {
        String amount = description(SURPLUS_INCOME, "amountEur");
        for (String statement : List.of("Estimated income", "OMIE day-ahead", "negative", "unpricedSurplusKWh",
                "Null when energy.surplusKWh is null")) {
            assertTrue(amount.contains(statement), () -> "does not state \"" + statement + "\": " + amount);
        }
        assertTrue(description(SURPLUS_INCOME, "unpricedSurplusKWh").contains("In kWh"));
        String operation = operation().path("description").asText().replaceAll("\\s+", " ");
        assertTrue(operation.contains("OMIE day-ahead"), () -> "the operation does not mention OMIE: " + operation);
    }

    @Test
    void referencesThePlantWithTheSharedPlantReference() throws Exception {
        assertTrue(schema(PLANT).path("properties").path("plant").path("$ref").asText()
                .endsWith("/PlantReferenceResponse"));
    }

    /**
     * EVL-003: a quantity the membership metrics already expose keeps its name, and its description
     * changes only in its subject and in what makes it null.
     */
    @Test
    @DisplayName("EVL-003 self-consumption, surplus and the supply counts keep the membership names and wording")
    void selfConsumptionSurplusAndTheSupplyCountsKeepTheMembershipNamesAndWording() throws Exception {
        assertEquals(
                description(MEMBERSHIP_ENERGY, "selfConsumptionKWh").replace("of the membership", "of the community"),
                description(ENERGY, "selfConsumptionKWh").replace(" Null when no supply stored a record in the " +
                        "period, or when no period could be resolved.", ""));
        assertEquals(
                description(MEMBERSHIP_ENERGY, "surplusKWh").replace("of the membership", "of the community"),
                description(ENERGY, "surplusKWh").replace(" Null when no supply stored a record in the period, " +
                        "or when no period could be resolved.", ""));
        assertFalse(description(COVERAGE, "supplyCount").isBlank());
        assertTrue(schema(MEMBERSHIP_COVERAGE).path("properties").has("supplyCount"));
        assertTrue(schema(MEMBERSHIP_COVERAGE).path("properties").has("suppliesWithData"));
    }

    @Test
    void documentsEveryEnergyAndPowerFigureWithItsUnit() throws Exception {
        for (String field : List.of("productionKWh", "selfConsumptionKWh", "surplusKWh")) {
            assertTrue(description(ENERGY, field).contains("In kWh"), () -> ENERGY + "." + field);
        }
        assertTrue(description(PLANT, "productionKWh").contains("In kWh"));
        assertTrue(description(PLANT, "installedPowerKWp").contains("kWp"));
        assertTrue(description(RESPONSE, "installedPowerKWp").contains("kWp"));
        assertTrue(description("PlantResponse", "totalPower").contains("kWp"));
    }

    @Test
    void documentsTheReferencePeriodAsAnEnumQueryParameterBesideTheTwoDates() throws Exception {
        JsonNode parameters = operation().path("parameters");

        JsonNode period = parameter(parameters, "period");
        assertEquals("query", period.path("in").asText());
        assertFalse(period.path("required").asBoolean());
        assertEquals(List.of("LATEST_PUBLISHED_MONTH"), textValues(period.path("schema").path("enum")));
        for (String date : List.of("startDate", "endDate")) {
            JsonNode parameter = parameter(parameters, date);
            assertEquals("query", parameter.path("in").asText());
            assertFalse(parameter.path("required").asBoolean());
        }
    }

    @Test
    void documentsTheOperationWithItsErrorStatuses() throws Exception {
        JsonNode responses = operation().path("responses");

        for (String status : List.of("200", "400", "401", "403", "404")) {
            assertFalse(responses.path(status).isMissingNode(), () -> "no " + status + ": " + fieldNames(responses));
        }
        assertEquals("getCommunityEnergyMetrics", operation().path("operationId").asText());
    }

    @Test
    @DisplayName("CEM-004 CEM-007 describes the coverage threshold and who may read the metrics")
    void describesTheCoverageThresholdAndWhoMayRead() throws Exception {
        String description = operation().path("description").asText()
                .replace("**", "")
                .replaceAll("\\s+", " ");

        for (String statement : List.of(
                "at least " + ReferenceMonthResolver.PUBLISHED_HOURS_MIN_PERCENT + "% of the hours the period spans",
                "Readable by any active member of the community",
                "A platform admin who is not a member is answered 403",
                "no figure, identifier or code of a single supply or member")) {
            assertTrue(description.contains(statement), () -> "does not state \"" + statement + "\": " + description);
        }
    }

    private String description(String schemaName, String field) throws Exception {
        JsonNode property = schema(schemaName).path("properties").path(field);
        assertFalse(property.isMissingNode(), () -> schemaName + " has no property " + field);
        return property.path("description").asText();
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

package org.lucoenergia.conluz.infrastructure.production.plant.energy;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
 * The plant energy metrics and the three plant energy series, asserted against the OpenAPI document the
 * application generates.
 */
class GetPlantEnergyControllersApiDocsTest extends BaseControllerTest {

    private static final String METRICS = "/api/v1/plants/{plantId}/energy-metrics";
    private static final String SERIES = "/api/v1/plants/{plantId}/energy-series/";

    @Test
    @DisplayName("PEM-001 LATEST_PRODUCTION_DAY is the only value of the plant metrics' period parameter")
    void latestProductionDayIsTheOnlyPeriodValue() throws Exception {
        JsonNode operation = operation(METRICS);

        assertEquals("getPlantEnergyMetrics", operation.path("operationId").asText());
        JsonNode period = parameter(operation.path("parameters"), "period");
        assertFalse(period.path("required").asBoolean());
        assertEquals(List.of("LATEST_PRODUCTION_DAY"), textValues(period.path("schema").path("enum")));
        for (String date : List.of("startDate", "endDate")) {
            assertFalse(parameter(operation.path("parameters"), date).path("required").asBoolean(), date);
        }
        for (String status : List.of("200", "400", "401", "403", "404")) {
            assertFalse(operation.path("responses").path(status).isMissingNode(), status);
        }
    }

    @Test
    @DisplayName("EPR-001 LATEST_PRODUCTION_DAY is not offered by the community, supply or membership metrics")
    void latestProductionDayIsNotOfferedElsewhere() throws Exception {
        MvcResult result = mockMvc.perform(get("/api-docs")).andExpect(status().isOk()).andReturn();
        JsonNode paths = objectMapper.readTree(result.getResponse().getContentAsString()).path("paths");

        List<String> offering = new ArrayList<>();
        paths.fields().forEachRemaining(path -> {
            if (path.getValue().toString().contains("LATEST_PRODUCTION_DAY")) {
                offering.add(path.getKey());
            }
        });
        assertEquals(List.of(METRICS), offering);
    }

    @Test
    @DisplayName("EVL-003 the metrics reuse the period shape and name production and coverage as elsewhere")
    void theMetricsReuseThePeriodShapeAndTheFieldNames() throws Exception {
        JsonNode response = schema("PlantEnergyMetricsResponse");
        assertEquals(List.of("coverage", "energy", "period"), sorted(textValues(response.path("required"))));
        assertTrue(response.path("properties").path("period").path("$ref").asText()
                .endsWith("/SupplyEnergyMetricsPeriodResponse"));

        JsonNode energy = schema("PlantEnergyMetricsEnergyResponse");
        assertEquals(List.of("productionKWh"), textValues(energy.path("required")));
        assertEquals(List.of("number", "null"), textValues(energy.path("properties").path("productionKWh").path("type")));

        JsonNode coverage = schema("PlantEnergyMetricsCoverageResponse");
        assertEquals(sorted(fieldNames(schema("SupplyEnergyMetricsCoverageResponse").path("properties"))),
                sorted(fieldNames(coverage.path("properties"))));
        assertEquals(List.of("expectedHours", "hoursWithData"), sorted(textValues(coverage.path("required"))));
    }

    @Test
    @DisplayName("EVL-003 PEM-002 the plant's production reads as the community metrics' per-plant production")
    void thePlantsProductionReadsAsTheCommunityPerPlantProduction() throws Exception {
        String perPlant = description(schema("CommunityPlantEnergyResponse"), "productionKWh");

        assertEquals(perPlant, description(schema("PlantEnergyMetricsEnergyResponse"), "productionKWh"));
        Map<String, String> buckets = Map.of(
                "PlantHourlyEnergyBucketResponse", "hour",
                "PlantDailyEnergyBucketResponse", "day",
                "PlantMonthlyEnergyBucketResponse", "month");
        for (Map.Entry<String, String> bucket : buckets.entrySet()) {
            String description = description(schema(bucket.getKey()), "productionKWh");
            assertTrue(description.startsWith("Energy the plant produced over the " + bucket.getValue()
                    + ", as reported by the distributor for the plant's own supply. In kWh, rounded to 3 "
                    + "decimals. Null when the plant stored no production record"),
                    () -> bucket.getKey() + ": " + description);
        }
    }

    @ParameterizedTest
    @CsvSource({
            "hourly, getPlantHourlyEnergySeries, PlantHourlyEnergyBucketResponse",
            "daily, getPlantDailyEnergySeries, PlantDailyEnergyBucketResponse",
            "monthly, getPlantMonthlyEnergySeries, PlantMonthlyEnergyBucketResponse"})
    void eachSeriesReturnsAnArrayOfBucketsAndTakesBothBoundsAsRequired(String series, String operationId,
                                                                       String bucket) throws Exception {
        JsonNode operation = operation(SERIES + series);

        assertEquals(operationId, operation.path("operationId").asText());
        JsonNode schema = operation.path("responses").path("200").path("content").path("application/json")
                .path("schema");
        assertEquals("array", schema.path("type").asText());
        assertTrue(schema.path("items").path("$ref").asText().endsWith("/" + bucket));
        for (String date : List.of("startDate", "endDate")) {
            assertTrue(parameter(operation.path("parameters"), date).path("required").asBoolean(), date);
        }
        assertTrue(parameters(operation).stream().noneMatch("period"::equals));
        for (String status : List.of("200", "400", "401", "403", "404")) {
            assertFalse(operation.path("responses").path(status).isMissingNode(), status);
        }
    }

    @ParameterizedTest
    @CsvSource({
            "PlantHourlyEnergyBucketResponse, true",
            "PlantDailyEnergyBucketResponse, false",
            "PlantMonthlyEnergyBucketResponse, false"})
    @DisplayName("PEM-003 every bucket field is required, and only the production is nullable, for a gap")
    void everyBucketFieldIsRequiredAndOnlyTheProductionIsNullable(String bucket, boolean hourly) throws Exception {
        JsonNode schema = schema(bucket);
        List<String> expected = new ArrayList<>(List.of("date", "time", "productionKWh", "hoursWithData",
                "expectedHours"));
        if (hourly) {
            expected.add("startsAt");
        }

        assertEquals(sorted(expected), sorted(textValues(schema.path("required"))));
        assertEquals(sorted(expected), sorted(fieldNames(schema.path("properties"))));
        for (String field : expected) {
            List<String> types = textValues(schema.path("properties").path(field).path("type"));
            assertEquals("productionKWh".equals(field), types.contains("null"),
                    () -> bucket + "." + field + " has types " + types);
        }
    }

    @ParameterizedTest
    @CsvSource({
            "/api/v1/plants/{plantId}/energy-metrics",
            "/api/v1/plants/{plantId}/energy-series/hourly",
            "/api/v1/plants/{plantId}/energy-series/daily",
            "/api/v1/plants/{plantId}/energy-series/monthly"})
    @DisplayName("PEM-004 describes who may read: a community admin, 403 to a member, 404 to anyone else")
    void describesWhoMayRead(String path) throws Exception {
        String description = operation(path).path("description").asText()
                .replace("**", "")
                .replaceAll("\\s+", " ");

        for (String statement : List.of(
                "Required: the caller can manage the plant",
                "an active community admin of the plant's community",
                "not an admin is answered 403",
                "a platform admin who is not a member included, 404",
                "No code or identifier of that supply is reported")) {
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

    private static List<String> parameters(JsonNode operation) {
        List<String> names = new ArrayList<>();
        operation.path("parameters").forEach(parameter -> names.add(parameter.path("name").asText()));
        return names;
    }

    private JsonNode operation(String path) throws Exception {
        JsonNode operation = document().path("paths").path(path).path("get");
        assertFalse(operation.isMissingNode(), () -> "No get on " + path);
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

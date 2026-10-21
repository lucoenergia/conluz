package org.lucoenergia.conluz.infrastructure.shared.web.apidocs;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * What the generated contract says about the energy figures of the consumption series and the
 * energy metrics (#385): one wording for each quantity across the four schemas, the precision of
 * every kWh figure, and where the monthly series is read from and what refreshes it.
 */
class EnergyValuesApiDocsTest extends BaseControllerTest {

    private static final String SUPPLY_BUCKET = "SupplyConsumptionBucketResponse";
    private static final String MEMBERSHIP_BUCKET = "MembershipMonthlyConsumptionBucketResponse";
    private static final String SUPPLY_ENERGY = "SupplyEnergyMetricsEnergyResponse";
    private static final String MEMBERSHIP_ENERGY = "MembershipEnergyMetricsEnergyResponse";
    private static final String HOURLY_PROFILE_BUCKET = "MembershipHourlyProfileBucketResponse";

    private static final String SUPPLY_MONTHLY_PATH = "/api/v1/supplies/{supplyId}/consumption/monthly";
    private static final String MEMBERSHIP_MONTHLY_PATH =
            "/api/v1/communities/{communityId}/memberships/{userId}/consumption/monthly";

    /** Grid import, self-consumption and surplus, by schema. */
    private static final Map<String, List<String>> QUANTITIES = Map.of(
            SUPPLY_BUCKET, List.of("gridImportKWh", "selfConsumptionKWh", "surplusKWh"),
            MEMBERSHIP_BUCKET, List.of("gridImportKWh", "selfConsumptionKWh", "surplusKWh"),
            SUPPLY_ENERGY, List.of("gridImportKWh", "selfConsumptionKWh", "surplusKWh"),
            MEMBERSHIP_ENERGY, List.of("gridImportKWh", "selfConsumptionKWh", "surplusKWh"));

    @Test
    @DisplayName("AC6 the four energy schemas describe grid import, self-consumption and surplus in the same words")
    void theFourSchemasDescribeTheSameQuantitiesInTheSameWords() throws Exception {
        JsonNode schemas = document().path("components").path("schemas");

        QUANTITIES.forEach((schemaName, fields) -> {
            JsonNode properties = schemas.path(schemaName).path("properties");
            assertFalse(properties.isMissingNode(), () -> "No schema named " + schemaName);

            String gridImport = description(properties, fields.get(0));
            assertTrue(gridImport.startsWith("Energy taken from the grid by ")
                            && gridImport.contains("Does not include self-consumed energy."),
                    () -> schemaName + "." + fields.get(0) + " was: " + gridImport);

            String selfConsumption = description(properties, fields.get(1));
            assertTrue(selfConsumption.startsWith("Energy produced by the community and consumed by ")
                            && selfConsumption.contains("as reported by the distributor"),
                    () -> schemaName + "." + fields.get(1) + " was: " + selfConsumption);

            String surplus = description(properties, fields.get(2));
            assertTrue(surplus.startsWith("Energy assigned to ") && surplus.contains(" and fed back to the grid over "),
                    () -> schemaName + "." + fields.get(2) + " was: " + surplus);
        });
    }

    @Test
    @DisplayName("EVL-001 AC6 every kWh figure of the four energy schemas states it is rounded to 3 decimals")
    void everyKWhFigureStatesItsPrecision() throws Exception {
        JsonNode schemas = document().path("components").path("schemas");

        for (String schemaName : QUANTITIES.keySet()) {
            JsonNode properties = schemas.path(schemaName).path("properties");
            List<String> kWhFields = new ArrayList<>();
            properties.fieldNames().forEachRemaining(field -> {
                if (field.endsWith("KWh")) {
                    kWhFields.add(field);
                }
            });
            assertFalse(kWhFields.isEmpty(), () -> schemaName + " has no kWh field");

            for (String field : kWhFields) {
                String description = description(properties, field);
                assertTrue(description.contains("rounded to 3 decimals"),
                        () -> schemaName + "." + field + " does not state its precision: " + description);
            }
        }
    }

    @Test
    @DisplayName("AC7 both monthly series state their source, what refreshes it and that they can lag the energy metrics")
    void bothMonthlySeriesStateTheirSourceAndFreshness() throws Exception {
        for (String path : List.of(SUPPLY_MONTHLY_PATH, MEMBERSHIP_MONTHLY_PATH)) {
            String description = operationDescription(path);

            for (String statement : List.of(
                    "monthly total pre-aggregated from",
                    "rewritten once a day for every month in the Datadis sync window",
                    "`POST /api/v1/communities/{communityId}/consumption/datadis/sync/monthly`",
                    "synchronising the hourly records does not",
                    "can therefore lag the energy metrics")) {
                assertTrue(description.contains(statement),
                        () -> path + " does not state \"" + statement + "\": " + description);
            }
        }
    }

    @Test
    @DisplayName("EVL-002 the membership monthly series states it rounds the unrounded per-supply sum once")
    void theMembershipMonthlySeriesStatesItRoundsOnce() throws Exception {
        String description = operationDescription(MEMBERSHIP_MONTHLY_PATH);

        assertTrue(description.contains("sums the unrounded per-supply monthly totals and is rounded once"),
                () -> "does not state the figures are rounded once: " + description);
        assertFalse(description.contains("the sum of what the per-supply monthly series reports"),
                () -> "still says it sums the per-supply series: " + description);
    }

    /**
     * One entry per quantity of the glossary: its field name, the schemas reporting it as a total,
     * whether the hourly profile reports it as an average, and the wording every description of it
     * shares, read case-insensitively. The wording is the one the energy metrics use, adapted only
     * to subject, period and averaging, so each pattern holds in all three operations.
     */
    private record Quantity(String field, List<String> totalSchemas, boolean averagedHourly,
                            List<Pattern> wording) {
    }

    private static final List<String> SERIES_AND_METRICS =
            List.of(SUPPLY_BUCKET, MEMBERSHIP_BUCKET, SUPPLY_ENERGY, MEMBERSHIP_ENERGY);

    private static final List<Quantity> GLOSSARY = List.of(
            new Quantity("gridImportKWh", SERIES_AND_METRICS, false, List.of(
                    Pattern.compile("^energy taken from the grid by "),
                    Pattern.compile("does not include self-consumed energy\\."))),
            new Quantity("totalConsumptionKWh", List.of(SUPPLY_ENERGY, MEMBERSHIP_ENERGY), true, List.of(
                    Pattern.compile("energy consumed"),
                    Pattern.compile("whatever its origin: grid ?import(kwh)? plus self-?consum"))),
            new Quantity("selfConsumptionKWh", SERIES_AND_METRICS, false, List.of(
                    Pattern.compile("^energy produced by the community and consumed by "),
                    Pattern.compile("as reported by the distributor"))),
            new Quantity("surplusKWh", SERIES_AND_METRICS, false, List.of(
                    Pattern.compile("^energy assigned to .* and fed back to the grid over "))),
            new Quantity("assignedProductionKWh", SERIES_AND_METRICS, true, List.of(
                    Pattern.compile("energy assigned to [^:]*: self-?consum(ed|ptionkwh) plus surplus"))));

    @Test
    @DisplayName("EVL-003 AC7 every quantity has one field name and one wording across the series, the energy metrics and the hourly profile")
    void everyQuantityHasOneNameAndOneWordingAcrossOperations() throws Exception {
        JsonNode schemas = document().path("components").path("schemas");

        for (Quantity quantity : GLOSSARY) {
            List<String> descriptions = new ArrayList<>();
            for (String schemaName : quantity.totalSchemas()) {
                descriptions.add(description(schemas.path(schemaName).path("properties"), quantity.field()));
            }
            if (quantity.averagedHourly()) {
                JsonNode hourly = schemas.path(HOURLY_PROFILE_BUCKET).path("properties");
                String name = quantity.field().substring(0, 1).toUpperCase() + quantity.field().substring(1);
                String sampleCount = quantity.field().replace("KWh", "SampleCount");
                descriptions.add(description(hourly, "average" + name));
                assertFalse(hourly.path(sampleCount).isMissingNode(),
                        () -> HOURLY_PROFILE_BUCKET + " has no " + sampleCount);
            }

            for (String description : descriptions) {
                String wording = description.toLowerCase();
                for (Pattern pattern : quantity.wording()) {
                    assertTrue(pattern.matcher(wording).find(),
                            () -> quantity.field() + " does not say \"" + pattern + "\": " + description);
                }
            }
        }
    }

    private static String description(JsonNode properties, String field) {
        JsonNode property = properties.path(field);
        assertFalse(property.isMissingNode(), () -> "No property named " + field);
        return property.path("description").asText();
    }

    /** Line breaks and markdown emphasis are presentation, not wording. */
    private String operationDescription(String path) throws Exception {
        JsonNode operation = document().path("paths").path(path).path("get");
        assertFalse(operation.isMissingNode(), () -> "No get on " + path);
        return operation.path("description").asText()
                .replace("**", "")
                .replaceAll("\\s+", " ");
    }

    private JsonNode document() throws Exception {
        MvcResult result = mockMvc.perform(get("/api-docs"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}

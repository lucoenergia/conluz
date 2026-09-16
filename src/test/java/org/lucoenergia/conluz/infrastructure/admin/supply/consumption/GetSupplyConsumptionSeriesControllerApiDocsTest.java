package org.lucoenergia.conluz.infrastructure.admin.supply.consumption;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AC6 and the contract half of AC3/AC10, asserted against the OpenAPI document the application
 * actually generates rather than against the annotations meant to produce it. The two have come
 * apart before: under OpenAPI 3.1 springdoc silently drops {@code @Schema(nullable = true)}, so a
 * field can look nullable in the source and not be nullable in the contract clients are generated
 * from.
 */
class GetSupplyConsumptionSeriesControllerApiDocsTest extends BaseControllerTest {

    private static final String BUCKET_SCHEMA = "SupplyConsumptionBucketResponse";
    private static final String DATADIS_SCHEMA = "DatadisConsumption";
    private static final String SERIES_PATH = "/api/v1/supplies/{supplyId}/consumption/%s";

    @Test
    void documentsEveryPropertyOfTheBucketAsRequired() throws Exception {
        List<String> required = textValues(schema(BUCKET_SCHEMA).path("required"));

        assertTrue(required.containsAll(List.of("cups", "date", "time", "consumptionKWh",
                        "obtainMethod", "surplusEnergyKWh", "generationEnergyKWh",
                        "selfConsumptionEnergyKWh", "savingsEur", "tariffSource")),
                () -> BUCKET_SCHEMA + ".required was " + required);
    }

    /**
     * The savings are computed for every bucket, zero included, so a generated client must be able
     * to read them without a null check. Only the obtain method can genuinely be absent -- a day
     * with no stored record has nothing to report it from.
     */
    @Test
    void documentsOnlyTheObtainMethodAsNullable() throws Exception {
        JsonNode properties = schema(BUCKET_SCHEMA).path("properties");

        List<String> obtainMethodTypes = textValues(properties.path("obtainMethod").path("type"));
        assertTrue(obtainMethodTypes.contains("null"),
                () -> "obtainMethod.type was " + properties.path("obtainMethod"));
        assertTrue(obtainMethodTypes.contains("string"),
                () -> "obtainMethod.type lost its base type: " + properties.path("obtainMethod"));

        for (String neverNull : List.of("cups", "date", "time", "consumptionKWh", "surplusEnergyKWh",
                "generationEnergyKWh", "selfConsumptionEnergyKWh", "savingsEur", "tariffSource")) {
            assertTrue(!textValues(properties.path(neverNull).path("type")).contains("null"),
                    () -> neverNull + " must not be nullable: " + properties.path(neverNull));
        }
    }

    @Test
    void doesNotDocumentAnEmptyPropertyOnTheBucket() throws Exception {
        JsonNode properties = schema(BUCKET_SCHEMA).path("properties");

        assertTrue(properties.path("empty").isMissingNode(),
                () -> BUCKET_SCHEMA + " still carries an empty property: " + properties);
    }

    /**
     * springdoc drops `nullable` under OpenAPI 3.1 without warning, so its absence from the whole
     * schema is the only proof the `types` convention is the one in force.
     */
    @Test
    void neverEmitsTheDroppedNullableKeyword() throws Exception {
        JsonNode bucket = schema(BUCKET_SCHEMA);

        assertTrue(!bucket.toString().contains("\"nullable\""),
                () -> BUCKET_SCHEMA + " carries a nullable keyword: " + bucket);
    }

    /**
     * AC3 and AC10 at the contract level: only the daily and monthly series moved to the new
     * schema, and the hourly and yearly ones still answer with the Datadis-shaped object --
     * {@code empty} and all.
     */
    @Test
    void onlyTheDailyAndMonthlySeriesMovedToTheNewSchema() throws Exception {
        JsonNode document = apiDocs();

        assertEquals("#/components/schemas/" + BUCKET_SCHEMA, itemRefOf(document, "daily"));
        assertEquals("#/components/schemas/" + BUCKET_SCHEMA, itemRefOf(document, "monthly"));
        assertEquals("#/components/schemas/" + DATADIS_SCHEMA, itemRefOf(document, "hourly"));
        assertEquals("#/components/schemas/" + DATADIS_SCHEMA, itemRefOf(document, "yearly"));

        assertTrue(!schema(DATADIS_SCHEMA).path("properties").path("empty").isMissingNode(),
                () -> DATADIS_SCHEMA + " lost its empty property, which hourly and yearly still expose");
    }

    private String itemRefOf(JsonNode document, String granularity) {
        JsonNode content = document.path("paths")
                .path(String.format(SERIES_PATH, granularity))
                .path("get").path("responses").path("200").path("content");
        assertTrue(content.fieldNames().hasNext(),
                () -> "No 200 content documented for the " + granularity + " series");

        JsonNode schema = content.get(content.fieldNames().next()).path("schema");
        return schema.path("items").path("$ref").asText();
    }

    private JsonNode schema(String name) throws Exception {
        JsonNode schema = apiDocs().path("components").path("schemas").path(name);
        assertTrue(!schema.isMissingNode(), () -> "No schema named " + name + " in the document");
        return schema;
    }

    private JsonNode apiDocs() throws Exception {
        MvcResult result = mockMvc.perform(get("/api-docs"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    /**
     * OpenAPI 3.1 lets `type` be either a single string or an array of them, and a `required` list
     * is an array. Reading both shapes the same way keeps the assertions about what the document
     * says, not about how it happens to be spelled.
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
}

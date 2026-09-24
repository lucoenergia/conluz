package org.lucoenergia.conluz.infrastructure.admin.supply.consumption;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AC6, asserted against the OpenAPI document the application actually generates rather than
 * against the annotations that are meant to produce it. The two have come apart before: under
 * OpenAPI 3.1 springdoc silently drops {@code @Schema(nullable = true)}, so a field can look
 * nullable in the source and not be nullable in the contract clients are generated from.
 */
class GetSupplyEnergyMetricsControllerApiDocsTest extends BaseControllerTest {

    private static final String SAVINGS_SCHEMA = "SupplyEnergyMetricsSavingsResponse";
    private static final String METRICS_SCHEMA = "SupplyEnergyMetricsResponse";

    @Test
    void documentsSavingsAsARequiredPropertyOfTheMetricsResponse() throws Exception {
        JsonNode required = schema(METRICS_SCHEMA).path("required");

        assertTrue(textValues(required).contains("savings"),
                () -> METRICS_SCHEMA + ".required was " + required);
    }

    @Test
    void documentsBothSavingsFieldsAsRequired() throws Exception {
        List<String> required = textValues(schema(SAVINGS_SCHEMA).path("required"));

        assertTrue(required.contains("amountEur"), () -> SAVINGS_SCHEMA + ".required was " + required);
        assertTrue(required.contains("tariffSource"), () -> SAVINGS_SCHEMA + ".required was " + required);
    }

    /**
     * The key is always present; its value may be null. Both halves have to reach the document,
     * or a generated client types the amount as non-nullable and breaks on the empty case.
     */
    @Test
    void documentsTheAmountAsNullableAndTheSourceAsNotNullable() throws Exception {
        JsonNode savings = schema(SAVINGS_SCHEMA).path("properties");

        List<String> amountTypes = textValues(savings.path("amountEur").path("type"));
        assertTrue(amountTypes.contains("null"),
                () -> "amountEur.type was " + savings.path("amountEur"));
        assertTrue(amountTypes.contains("number"),
                () -> "amountEur.type lost its base type: " + savings.path("amountEur"));

        assertTrue(!textValues(savings.path("tariffSource").path("type")).contains("null"),
                () -> "tariffSource must not be nullable: " + savings.path("tariffSource"));
    }

    /**
     * springdoc drops `nullable` under OpenAPI 3.1 without warning, so its absence from the whole
     * document is the only proof the `types` convention is the one in force.
     */
    @Test
    void neverEmitsTheDroppedNullableKeyword() throws Exception {
        JsonNode savings = schema(SAVINGS_SCHEMA);

        assertTrue(!savings.toString().contains("\"nullable\""),
                () -> SAVINGS_SCHEMA + " carries a nullable keyword: " + savings);
    }

    private JsonNode schema(String name) throws Exception {
        MvcResult result = mockMvc.perform(get("/api-docs"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode schema = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("components").path("schemas").path(name);
        assertTrue(!schema.isMissingNode(), () -> "No schema named " + name + " in the document");
        return schema;
    }

    /**
     * OpenAPI 3.1 lets `type` be either a single string or an array of them, and a `required`
     * list is an array. Reading both shapes the same way keeps the assertions about what the
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
}

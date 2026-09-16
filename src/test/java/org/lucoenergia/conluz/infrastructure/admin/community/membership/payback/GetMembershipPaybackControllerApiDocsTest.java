package org.lucoenergia.conluz.infrastructure.admin.community.membership.payback;

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
 * Asserted against the OpenAPI document the application actually generates rather than against the
 * annotations meant to produce it. The two have come apart before: under OpenAPI 3.1 springdoc
 * silently drops {@code @Schema(nullable = true)}, so a field can look nullable in the source and
 * not be nullable in the contract clients are generated from.
 *
 * <p>Nullability matters more here than usual. Six of the seven fields can legitimately be null,
 * and a client that types them as non-nullable does not fail at the boundary -- it renders a
 * payback card with a recovered investment where none was ever recorded.
 */
class GetMembershipPaybackControllerApiDocsTest extends BaseControllerTest {

    private static final String SCHEMA = "MembershipPaybackResponse";
    private static final String PAYBACK_PATH =
            "/api/v1/communities/{communityId}/memberships/{userId}/payback";
    private static final String INVESTMENT_PATH =
            "/api/v1/communities/{communityId}/memberships/{userId}/investment";

    @Test
    void documentsEveryFieldAsRequired() throws Exception {
        List<String> required = textValues(schema(SCHEMA).path("required"));

        assertTrue(required.containsAll(List.of("investmentEur", "savedEur", "remainingEur",
                        "progressRatio", "startDate", "estimatedRemainingMonths", "tariffSource")),
                () -> SCHEMA + ".required was " + required);
    }

    /**
     * Each key is always present; its value may be null. Both halves have to reach the document,
     * and the base type has to survive alongside the null, or a generated client loses the type.
     */
    @Test
    void documentsTheNullableAmountsWithTheirBaseTypeAndNull() throws Exception {
        JsonNode properties = schema(SCHEMA).path("properties");

        assertNullableWithBaseType(properties, "investmentEur", "number");
        assertNullableWithBaseType(properties, "savedEur", "number");
        assertNullableWithBaseType(properties, "remainingEur", "number");
        assertNullableWithBaseType(properties, "progressRatio", "number");
        assertNullableWithBaseType(properties, "estimatedRemainingMonths", "integer");
    }

    @Test
    void documentsTheStartDateAsANullableDateString() throws Exception {
        JsonNode startDate = schema(SCHEMA).path("properties").path("startDate");

        assertNullableWithBaseType(schema(SCHEMA).path("properties"), "startDate", "string");
        assertEquals("date", startDate.path("format").asText(),
                () -> "startDate lost its date format: " + startDate);
    }

    /**
     * The one field that must never be null: a monetary figure with no stated provenance cannot be
     * told apart from one backed by a real tariff.
     */
    @Test
    void documentsTheTariffSourceAsNotNullable() throws Exception {
        JsonNode tariffSource = schema(SCHEMA).path("properties").path("tariffSource");

        assertTrue(!textValues(tariffSource.path("type")).contains("null"),
                () -> "tariffSource must not be nullable: " + tariffSource);
    }

    /**
     * springdoc drops `nullable` under OpenAPI 3.1 without warning, so its absence from the schema
     * is the only proof the `types` convention is the one in force.
     */
    @Test
    void neverEmitsTheDroppedNullableKeyword() throws Exception {
        JsonNode schema = schema(SCHEMA);

        assertTrue(!schema.toString().contains("\"nullable\""),
                () -> SCHEMA + " carries a nullable keyword: " + schema);
    }

    @Test
    void documentsTheReadOperationWithItsDenialStatuses() throws Exception {
        JsonNode responses = operation(PAYBACK_PATH, "get").path("responses");

        assertTrue(!responses.path("200").isMissingNode(), () -> "no 200: " + fieldNames(responses));
        assertTrue(!responses.path("401").isMissingNode(), () -> "no 401: " + fieldNames(responses));
        assertTrue(!responses.path("404").isMissingNode(), () -> "no 404: " + fieldNames(responses));
    }

    /**
     * Both write operations answer 204 and neither returns the membership, so neither may declare
     * a 200 with a body.
     */
    @Test
    void documentsTheWriteOperationsAsNoContent() throws Exception {
        JsonNode put = operation(INVESTMENT_PATH, "put").path("responses");
        JsonNode delete = operation(INVESTMENT_PATH, "delete").path("responses");

        assertTrue(!put.path("204").isMissingNode(), () -> "PUT declares no 204: " + fieldNames(put));
        assertTrue(put.path("200").isMissingNode(), () -> "PUT must not declare a 200: " + fieldNames(put));
        assertTrue(!delete.path("204").isMissingNode(), () -> "DELETE declares no 204: " + fieldNames(delete));
        assertTrue(delete.path("200").isMissingNode(),
                () -> "DELETE must not declare a 200: " + fieldNames(delete));
    }

    /**
     * The investment is readable through the payback schema alone. If it ever appears on another
     * response schema -- a membership, a user -- it has become readable wherever that schema is,
     * under whatever authorization those endpoints happen to have.
     *
     * <p>Request schemas are exempt: {@code SetMembershipInvestmentBody} carries the amount by
     * definition, and a body is what the client sends rather than what it is told.
     */
    @Test
    void noOtherResponseSchemaCarriesTheInvestment() throws Exception {
        JsonNode schemas = document().path("components").path("schemas");

        for (String name : fieldNames(schemas)) {
            if (SCHEMA.equals(name) || name.endsWith("Body")) {
                continue;
            }
            List<String> properties = fieldNames(schemas.path(name).path("properties"));
            assertTrue(properties.stream().noneMatch(property -> property.toLowerCase().contains("investment")),
                    () -> name + " carries an investment property: " + properties);
        }
    }

    private void assertNullableWithBaseType(JsonNode properties, String field, String baseType) {
        List<String> types = textValues(properties.path(field).path("type"));

        assertTrue(types.contains("null"),
                () -> field + ".type was " + properties.path(field));
        assertTrue(types.contains(baseType),
                () -> field + ".type lost its base type: " + properties.path(field));
    }

    private JsonNode operation(String path, String method) throws Exception {
        JsonNode operation = document().path("paths").path(path).path(method);
        assertTrue(!operation.isMissingNode(), () -> "No " + method + " on " + path);
        return operation;
    }

    private JsonNode schema(String name) throws Exception {
        JsonNode schema = document().path("components").path("schemas").path(name);
        assertTrue(!schema.isMissingNode(), () -> "No schema named " + name + " in the document");
        return schema;
    }

    private JsonNode document() throws Exception {
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

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }
}

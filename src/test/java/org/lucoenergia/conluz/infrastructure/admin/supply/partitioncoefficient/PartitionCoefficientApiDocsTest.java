package org.lucoenergia.conluz.infrastructure.admin.supply.partitioncoefficient;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Asserts the generated OpenAPI document, not the runtime responses. Two of the conventions this
 * work depends on are invisible at runtime and silently wrong in the document: a list-shaped
 * endpoint that renders as a bare object, and {@code @Schema(nullable = true)}, which OpenAPI 3.1
 * drops without any warning. Only reading the document catches either.
 */
class PartitionCoefficientApiDocsTest extends BaseControllerTest {

    private JsonNode apiDocs() throws Exception {
        MvcResult result = mockMvc.perform(get("/api-docs"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode okSchemaOf(JsonNode root, String path) {
        return root.path("paths").path(path).path("get").path("responses").path("200")
                .path("content").path("application/json").path("schema");
    }

    @Test
    void activeAndAtTimestampAreDocumentedAsArrays() throws Exception {
        JsonNode root = apiDocs();

        for (String path : List.of(
                "/api/v1/supplies/{supplyId}/partition-coefficients",
                "/api/v1/supplies/{supplyId}/partition-coefficients/active",
                "/api/v1/supplies/{supplyId}/partition-coefficients/at")) {
            JsonNode schema = okSchemaOf(root, path);
            assertEquals("array", schema.path("type").asText(null), path + " -> " + schema);
        }
    }

    @Test
    void theThreeReadEndpointsAcceptAnOptionalPlantIdQueryParameter() throws Exception {
        JsonNode root = apiDocs();

        for (String path : List.of(
                "/api/v1/supplies/{supplyId}/partition-coefficients",
                "/api/v1/supplies/{supplyId}/partition-coefficients/active",
                "/api/v1/supplies/{supplyId}/partition-coefficients/at")) {
            JsonNode parameters = root.path("paths").path(path).path("get").path("parameters");
            JsonNode plantId = null;
            for (JsonNode parameter : parameters) {
                if ("plantId".equals(parameter.path("name").asText()) && "query".equals(parameter.path("in").asText())) {
                    plantId = parameter;
                }
            }
            assertTrue(plantId != null, path + " has no plantId query parameter: " + parameters);
            assertFalse(plantId.path("required").asBoolean(false), path + " -> plantId must be optional");
        }
    }

    /**
     * The history is guarded by canReadSupply, which returns 200, throws 404, or -- for an anonymous
     * caller -- yields 401. It has no reachable 403, and a documented status a caller can never
     * receive is a false promise: a client would write dead handling for it. 404, by contrast, is
     * reachable and was previously undocumented.
     */
    @Test
    void theHistoryDocumentsNotFoundAndNoForbidden() throws Exception {
        JsonNode responses = apiDocs().path("paths")
                .path("/api/v1/supplies/{supplyId}/partition-coefficients")
                .path("get").path("responses");

        assertTrue(responses.has("404"), "404 is reachable and must be documented: " + responses);
        assertTrue(responses.has("401"), "401 is reachable and must be documented: " + responses);
        assertFalse(responses.has("403"), "403 is unreachable under canReadSupply: " + responses);
    }

    @Test
    void partitionCoefficientCarriesNestedReferencesAndNoFlatIds() throws Exception {
        JsonNode properties = apiDocs().path("components").path("schemas")
                .path("PartitionCoefficientResponse").path("properties");

        assertTrue(properties.has("supply"), properties.toString());
        assertTrue(properties.has("plant"), properties.toString());
        assertTrue(properties.has("sharingAgreement"), properties.toString());
        assertFalse(properties.has("supplyId"), "flat supplyId must be gone: " + properties);
        assertFalse(properties.has("plantId"), "flat plantId must be gone: " + properties);
        assertFalse(properties.has("sharingAgreementId"), "flat sharingAgreementId must be gone: " + properties);
    }

    @Test
    void coefficientAtTimestampCarriesNestedReferencesAndNoFlatIds() throws Exception {
        JsonNode properties = apiDocs().path("components").path("schemas")
                .path("CoefficientAtTimestampResponse").path("properties");

        assertTrue(properties.has("supply"), properties.toString());
        assertTrue(properties.has("plant"), properties.toString());
        assertFalse(properties.has("supplyId"), "flat supplyId must be gone: " + properties);
    }

    /**
     * currentCoefficient is a nullable $ref. Under OpenAPI 3.1 the nullability has to reach the
     * document as "null" inside a type array; if it were written with nullable = true it would be
     * dropped here and the generated client would type the field as always present.
     *
     * <p>It renders as {@code {"type": ["object", "null"], "$ref": ...}}. That is correct OpenAPI,
     * but Orval drops the nullability when a $ref has siblings, so conluz-web needs its
     * input.override.transformer to rewrite this into an anyOf -- see AGENTS.md.
     */
    @Test
    void currentCoefficientIsDocumentedAsNullable() throws Exception {
        JsonNode currentCoefficient = apiDocs().path("components").path("schemas")
                .path("SharingAgreementPartitionCoefficientResponse")
                .path("properties").path("currentCoefficient");

        assertFalse(currentCoefficient.isMissingNode(), "currentCoefficient is not in the document");
        boolean nullable = false;
        for (JsonNode type : currentCoefficient.path("type")) {
            if ("null".equals(type.asText())) {
                nullable = true;
            }
        }
        for (JsonNode branch : currentCoefficient.path("anyOf")) {
            if ("null".equals(branch.path("type").asText())) {
                nullable = true;
            }
        }
        assertTrue(nullable, "currentCoefficient must be documented as nullable: " + currentCoefficient);
    }

    /**
     * The supply reference moved out of the sharing-agreement package and was renamed. Its old
     * schema name must not linger in the document, or clients would keep generating both.
     */
    @Test
    void theRenamedSupplyReferenceSchemaReplacesTheOldOne() throws Exception {
        JsonNode schemas = apiDocs().path("components").path("schemas");

        assertTrue(schemas.has("SupplyReferenceResponse"), "SupplyReferenceResponse is missing");
        assertFalse(schemas.has("SharingAgreementCoefficientSupplyResponse"),
                "the pre-rename schema is still generated");
    }
}

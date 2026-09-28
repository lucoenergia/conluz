package org.lucoenergia.conluz.infrastructure.admin.supply;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
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
 * Asserts the generated OpenAPI document, not the runtime responses. Whether a field is declared
 * required, and whether its type admits null, is invisible at runtime but decides the shape of the
 * generated client -- and {@code @Schema(nullable = true)} is dropped silently under OpenAPI 3.1.
 * Only reading the document catches either.
 */
class SupplyApiDocsTest extends BaseControllerTest {

    private static final String COMMUNITY_REFERENCE = "CommunityReferenceResponse";

    private JsonNode schemas() throws Exception {
        MvcResult result = mockMvc.perform(get("/api-docs"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("components").path("schemas");
    }

    private static List<String> textValues(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(node -> values.add(node.asText()));
        return values;
    }

    @Test
    void supplyResponseExposesTheOwningCommunityAsACommunityReference() throws Exception {
        JsonNode community = schemas().path("SupplyResponse").path("properties").path("community");

        assertTrue(community.path("$ref").asText().endsWith("/" + COMMUNITY_REFERENCE),
                "SupplyResponse.community must reuse " + COMMUNITY_REFERENCE + ": " + community);
    }

    /**
     * supplies.community_id is NOT NULL with a foreign key, so the reference is never absent. A
     * nullable declaration here would make every client handle a case the database cannot produce.
     */
    @Test
    void supplyResponseDeclaresTheCommunityRequiredAndNotNullable() throws Exception {
        JsonNode supplyResponse = schemas().path("SupplyResponse");

        assertTrue(textValues(supplyResponse.path("required")).contains("community"),
                "community must be required: " + supplyResponse.path("required"));

        JsonNode community = supplyResponse.path("properties").path("community");
        assertFalse(community.has("nullable"),
                "nullable is silently dropped under OpenAPI 3.1 and must not be used: " + community);
        assertFalse(textValues(community.path("type")).contains("null"),
                "community is not nullable: " + community);
        assertFalse(community.has("anyOf"), "community is not nullable: " + community);
    }

    /**
     * The change is additive. A removed or renamed property, or one that stopped being required,
     * would break the generated client silently.
     */
    @Test
    void supplyResponseKeepsEveryPreviouslyDeclaredField() throws Exception {
        JsonNode supplyResponse = schemas().path("SupplyResponse");

        for (String field : List.of("id", "code", "user", "name", "address", "addressRef", "enabled",
                "contract", "distributor", "shelly")) {
            assertTrue(supplyResponse.path("properties").has(field),
                    field + " must still be a property of SupplyResponse");
            assertTrue(textValues(supplyResponse.path("required")).contains(field),
                    field + " must still be required");
        }
        assertEquals(11, supplyResponse.path("properties").size(),
                "SupplyResponse gains community and nothing else: " + supplyResponse.path("properties"));
    }

    /**
     * SupplyResponse now shares CommunityReferenceResponse with the partition-coefficient responses,
     * so a change made for supplies would reach them too.
     */
    @Test
    void theSharedCommunityReferenceIsUnchangedForItsExistingUsers() throws Exception {
        JsonNode schemas = schemas();

        assertEquals(List.of("id", "name"), textValues(schemas.path(COMMUNITY_REFERENCE).path("required")));
        assertEquals(2, schemas.path(COMMUNITY_REFERENCE).path("properties").size());

        for (String response : List.of("PartitionCoefficientResponse", "CoefficientAtTimestampResponse")) {
            JsonNode community = schemas.path(response).path("properties").path("community");
            assertTrue(community.path("$ref").asText().endsWith("/" + COMMUNITY_REFERENCE),
                    response + ".community must still reference " + COMMUNITY_REFERENCE + ": " + community);
            assertTrue(textValues(schemas.path(response).path("required")).contains("community"),
                    response + ".community must still be required");
        }
    }
}

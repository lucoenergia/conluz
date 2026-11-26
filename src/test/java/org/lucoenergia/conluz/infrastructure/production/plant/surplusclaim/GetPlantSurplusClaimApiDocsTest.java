package org.lucoenergia.conluz.infrastructure.production.plant.surplusclaim;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
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
 * The surplus claim preview, asserted against the OpenAPI document the application generates.
 */
class GetPlantSurplusClaimApiDocsTest extends BaseControllerTest {

    private static final String PATH = "/api/v1/plants/{plantId}/surplus-claim";

    @Test
    @DisplayName("SCL-007 the month is an optional YYYY-MM query parameter, and SCL-013 the operation answers 400, 401, 403 and 404")
    void theMonthIsAnOptionalQueryParameter() throws Exception {
        JsonNode document = document();
        JsonNode operation = document.path("paths").path(PATH).path("get");

        assertEquals("getPlantSurplusClaim", operation.path("operationId").asText());
        JsonNode month = operation.path("parameters").get(1);
        assertEquals("month", month.path("name").asText());
        assertEquals("query", month.path("in").asText());
        assertFalse(month.path("required").asBoolean());
        assertEquals("string", month.path("schema").path("type").asText());
        assertTrue("2026-09".matches(month.path("schema").path("pattern").asText()));
        assertFalse("2026-13".matches(month.path("schema").path("pattern").asText()));
        for (String status : List.of("200", "400", "401", "403", "404")) {
            assertFalse(operation.path("responses").path(status).isMissingNode(), status);
        }
    }

    @Test
    @DisplayName("SCL-007 SURPLUS_CLAIM_MONTH_NOT_CLOSED is a published error code")
    void theRefusalCodeIsPublished() throws Exception {
        String document = document().toString();

        assertTrue(document.contains("\"SURPLUS_CLAIM_MONTH_NOT_CLOSED\""));
    }

    @Test
    @DisplayName("CEM-008 the surplus income reuses the field names of the community energy metrics' surplus income")
    void theSurplusIncomeReusesTheCommunityFieldNames() throws Exception {
        JsonNode schemas = document().path("components").path("schemas");

        assertEquals(sorted(fieldNames(schemas.path("CommunityEnergyMetricsSurplusIncomeResponse").path("properties"))),
                sorted(fieldNames(schemas.path("PlantSurplusClaimSurplusIncomeResponse").path("properties"))));
    }

    @Test
    @DisplayName("SCL-004 an hour of the claim is identified by startsAt, as the hourly buckets of the energy series are")
    void anHourIsIdentifiedByStartsAt() throws Exception {
        JsonNode schemas = document().path("components").path("schemas");

        assertEquals(List.of("startsAt", "surplusKWh"),
                sorted(fieldNames(schemas.path("PlantSurplusClaimHourResponse").path("properties"))));
        assertEquals("date-time",
                schemas.path("PlantHourlyEnergyBucketResponse").path("properties").path("startsAt").path("format")
                        .asText());
        assertEquals("date-time",
                schemas.path("PlantSurplusClaimHourResponse").path("properties").path("startsAt").path("format")
                        .asText());
    }

    private JsonNode document() throws Exception {
        MvcResult result = mockMvc.perform(get("/api-docs")).andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
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

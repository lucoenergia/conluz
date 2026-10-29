package org.lucoenergia.conluz.infrastructure.admin.supply.consumption;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Every operation taking a requested period documents the bounds convention word for word, read
 * from the OpenAPI document the application generates.
 */
class EnergyPeriodApiDocsTest extends BaseControllerTest {

    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "/api/v1/supplies/{supplyId}/energy-metrics",
            "/api/v1/supplies/{supplyId}/consumption/daily",
            "/api/v1/supplies/{supplyId}/consumption/monthly",
            "/api/v1/communities/{communityId}/memberships/{userId}/energy-metrics",
            "/api/v1/communities/{communityId}/memberships/{userId}/consumption/monthly",
            "/api/v1/communities/{communityId}/memberships/{userId}/consumption/daily"
    })
    void everyOperationTakingAPeriodDocumentsTheSameBounds(String path) throws Exception {
        String description = apiDocs().path("paths").path(path).path("get").path("description").asText();

        assertTrue(description.contains(EnergyPeriodApiDocs.RANGE_BOUNDS.strip()),
                () -> path + " does not carry the shared bounds paragraph:\n" + description);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "/api/v1/supplies/{supplyId}/consumption/daily",
            "/api/v1/supplies/{supplyId}/consumption/monthly",
            "/api/v1/communities/{communityId}/memberships/{userId}/consumption/monthly",
            "/api/v1/communities/{communityId}/memberships/{userId}/consumption/daily"
    })
    @DisplayName("EPR-001 every consumption series describes startDate as inclusive and endDate as exclusive")
    void everyConsumptionSeriesDescribesItsBoundsAsHalfOpen(String path) throws Exception {
        JsonNode parameters = apiDocs().path("paths").path(path).path("get").path("parameters");

        assertEquals("Start of the period, inclusive.", parameterDescription(parameters, "startDate"),
                () -> path + " startDate");
        assertEquals("End of the period, exclusive.", parameterDescription(parameters, "endDate"),
                () -> path + " endDate");
    }

    private static String parameterDescription(JsonNode parameters, String name) {
        for (JsonNode parameter : parameters) {
            if (name.equals(parameter.path("name").asText())) {
                return parameter.path("description").asText(null);
            }
        }
        return null;
    }

    private JsonNode apiDocs() throws Exception {
        MvcResult result = mockMvc.perform(get("/api-docs"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}

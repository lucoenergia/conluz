package org.lucoenergia.conluz.infrastructure.admin.supply.consumption;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
            "/api/v1/communities/{communityId}/memberships/{userId}/consumption/daily",
            "/api/v1/supplies/{supplyId}/consumption/hourly",
            "/api/v1/communities/{communityId}/memberships/{userId}/consumption/hourly"
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
            "/api/v1/communities/{communityId}/memberships/{userId}/consumption/daily",
            "/api/v1/supplies/{supplyId}/consumption/hourly",
            "/api/v1/communities/{communityId}/memberships/{userId}/consumption/hourly"
    })
    @DisplayName("EPR-001 every consumption series describes startDate as inclusive and endDate as exclusive")
    void everyConsumptionSeriesDescribesItsBoundsAsHalfOpen(String path) throws Exception {
        JsonNode parameters = apiDocs().path("paths").path(path).path("get").path("parameters");

        assertEquals("Start of the period, inclusive.", parameterDescription(parameters, "startDate"),
                () -> path + " startDate");
        assertEquals("End of the period, exclusive.", parameterDescription(parameters, "endDate"),
                () -> path + " endDate");
    }

    /**
     * The shared paragraph states the 24 months of the other operations, so the hourly series has to
     * say that its own limit replaces them.
     */
    @Test
    @DisplayName("EPR-005 the supply hourly series documents its 31-day cap")
    void theSupplyHourlySeriesDocumentsItsOwnCap() throws Exception {
        String description = apiDocs().path("paths").path("/api/v1/supplies/{supplyId}/consumption/hourly")
                .path("get").path("description").asText();

        assertTrue(description.contains("the limit of rule 5 is **31 calendar days**"),
                () -> "the hourly series does not state its cap:\n" + description);
    }

    @Test
    @DisplayName("EPR-005 the membership hourly series documents its 31-day cap")
    void theMembershipHourlySeriesDocumentsItsOwnCap() throws Exception {
        String description = apiDocs().path("paths")
                .path("/api/v1/communities/{communityId}/memberships/{userId}/consumption/hourly")
                .path("get").path("description").asText();

        assertTrue(description.contains("the limit of rule 5 is **31 calendar days**"),
                () -> "the membership hourly series does not state its cap:\n" + description);
    }

    /**
     * The shared paragraph names the params of a too-long refusal and their unit values; the hourly
     * series, whose limit is not the one that paragraph states, give their own.
     */
    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "/api/v1/supplies/{supplyId}/energy-metrics, MONTHS",
            "/api/v1/supplies/{supplyId}/consumption/daily, MONTHS",
            "/api/v1/supplies/{supplyId}/consumption/monthly, MONTHS",
            "/api/v1/communities/{communityId}/memberships/{userId}/energy-metrics, MONTHS",
            "/api/v1/communities/{communityId}/memberships/{userId}/consumption/monthly, MONTHS",
            "/api/v1/communities/{communityId}/memberships/{userId}/consumption/daily, MONTHS",
            "/api/v1/supplies/{supplyId}/consumption/hourly, DAYS",
            "/api/v1/communities/{communityId}/memberships/{userId}/consumption/hourly, DAYS"
    })
    @DisplayName("EPR-004 EPR-005 every operation documents the params of its too-long refusal")
    void everyOperationDocumentsTheTooLongParams(String path, String unit) throws Exception {
        String description = apiDocs().path("paths").path(path).path("get").path("description").asText()
                .replaceAll("\\s+", " ");

        assertTrue(description.contains("The refusal states the limit in `params`: `amount`, a whole number "
                        + "written as a string, and `unit`, `MONTHS` or `DAYS`"),
                () -> path + " does not document the params:\n" + description);
        String expected = "MONTHS".equals(unit)
                ? "{\"amount\": \"24\", \"unit\": \"MONTHS\"}"
                : "{\"amount\": \"31\", \"unit\": \"DAYS\"}";
        assertTrue(description.contains(expected), () -> path + " does not state " + expected + ":\n" + description);
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

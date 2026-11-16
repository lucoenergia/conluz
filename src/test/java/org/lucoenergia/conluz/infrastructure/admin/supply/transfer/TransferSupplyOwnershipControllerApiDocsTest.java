package org.lucoenergia.conluz.infrastructure.admin.supply.transfer;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Asserts the generated OpenAPI document of transferSupplyOwnership: what the generated client depends on and the
 * runtime responses cannot show.
 */
class TransferSupplyOwnershipControllerApiDocsTest extends BaseControllerTest {

    private JsonNode document() throws Exception {
        String content = mockMvc.perform(get("/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(content);
    }

    private static List<String> textValues(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(node -> values.add(node.asText()));
        return values;
    }

    @Test
    void theBodyRequiresTheNewOwnerAndTheEffectiveDate() throws Exception {
        JsonNode body = document().path("components").path("schemas").path("TransferSupplyOwnershipBody");

        assertThat(textValues(body.path("required"))).containsExactlyInAnyOrder("newOwnerPersonalId",
                "effectiveDate");
        assertThat(body.path("properties").path("effectiveDate").path("format").asText()).isEqualTo("date");
        assertThat(body.path("properties").path("newOwnerPersonalId").path("type").asText()).isEqualTo("string");
    }

    @Test
    @DisplayName("SUP-008 canTransferOwnership is a required capability of a supply")
    void canTransferOwnershipIsARequiredCapability() throws Exception {
        JsonNode capabilities = document().path("components").path("schemas").path("SupplyCapabilitiesResponse");

        assertThat(textValues(capabilities.path("required"))).contains("canTransferOwnership");
        assertThat(capabilities.path("properties").path("canTransferOwnership").path("type").asText())
                .isEqualTo("boolean");
    }

    @Test
    void theOperationAnswersTheSupplyAndDeclaresItsRefusals() throws Exception {
        JsonNode operation = document().path("paths").path("/api/v1/supplies/{supplyId}/ownership/transfer")
                .path("post");
        JsonNode responses = operation.path("responses");

        assertThat(operation.path("operationId").asText()).isEqualTo("transferSupplyOwnership");
        assertThat(responses.path("200").path("content").path("*/*").path("schema").path("$ref").asText())
                .endsWith("/SupplyResponse");
        for (String status : List.of("400", "401", "403", "404")) {
            assertThat(responses.has(status)).as(status).isTrue();
        }
        JsonNode examples = responses.path("409").path("content").path("application/json").path("examples");
        assertThat(examples.path("Already owned by the user").path("value").path("errors").path(0).path("code")
                .asText()).isEqualTo("SUPPLY_ALREADY_OWNED_BY_USER");
        assertThat(examples.path("Date not after the previous transfer").path("value").path("errors").path(0)
                .path("code").asText()).isEqualTo("SUPPLY_TRANSFER_DATE_NOT_AFTER_PREVIOUS");
    }
}

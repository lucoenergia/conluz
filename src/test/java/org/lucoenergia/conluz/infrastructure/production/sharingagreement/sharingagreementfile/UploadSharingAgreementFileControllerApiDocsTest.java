package org.lucoenergia.conluz.infrastructure.production.sharingagreement.sharingagreementfile;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UploadSharingAgreementFileControllerApiDocsTest extends BaseControllerTest {

    @Test
    void documents400ResponseWithRestErrorSchema() throws Exception {
        MvcResult result = mockMvc.perform(get("/api-docs"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode response400 = root.path("paths")
                .path("/api/v1/plants/{plantId}/sharing-agreements/{sharingAgreementId}/file")
                .path("post")
                .path("responses")
                .path("400");

        String ref = response400.path("content").path("application/json").path("schema").path("$ref").asText(null);
        assertEquals("#/components/schemas/RestError", ref, response400.toString());
    }
}

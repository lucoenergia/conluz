package org.lucoenergia.conluz.infrastructure.production.plant.surplusclaim.file;

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
 * The surplus claim file, asserted against the OpenAPI document the application generates.
 */
class GetPlantSurplusClaimFileApiDocsTest extends BaseControllerTest {

    private static final String PATH = "/api/v1/plants/{plantId}/surplus-claim/file";

    @Test
    @DisplayName("SCL-015 month and format are required query parameters, format ENDESA or GENERIC")
    void monthAndFormatAreRequired() throws Exception {
        JsonNode operation = operation();

        assertEquals("getPlantSurplusClaimFile", operation.path("operationId").asText());
        assertEquals(List.of("Plants"), texts(operation.path("tags")));
        JsonNode month = parameter(operation, "month");
        assertEquals("query", month.path("in").asText());
        assertTrue(month.path("required").asBoolean());
        assertEquals("string", month.path("schema").path("type").asText());
        assertTrue("2026-09".matches(month.path("schema").path("pattern").asText()));
        assertFalse("2026-13".matches(month.path("schema").path("pattern").asText()));
        JsonNode format = parameter(operation, "format");
        assertEquals("query", format.path("in").asText());
        assertTrue(format.path("required").asBoolean());
        assertEquals(List.of("ENDESA", "GENERIC"), texts(format.path("schema").path("enum")));
    }

    @Test
    @DisplayName("SCL-018 SCL-019 the file is a binary ZIP or CSV, so a generated client reads it as a blob")
    void theFileIsBinary() throws Exception {
        JsonNode content = operation().path("responses").path("200").path("content");

        assertEquals(List.of("application/zip", "text/csv"), fieldNames(content));
        for (String mediaType : List.of("application/zip", "text/csv")) {
            assertEquals("string", content.path(mediaType).path("schema").path("type").asText(), mediaType);
            assertEquals("binary", content.path(mediaType).path("schema").path("format").asText(), mediaType);
        }
    }

    @Test
    @DisplayName("SCL-016 SURPLUS_CLAIM_CIL_REQUIRED is a published 409, and SCL-013 the operation answers 400, 401, 403 and 404")
    void theRefusalsArePublished() throws Exception {
        JsonNode document = document();
        JsonNode responses = document.path("paths").path(PATH).path("get").path("responses");

        for (String status : List.of("200", "400", "401", "403", "404", "409")) {
            assertFalse(responses.path(status).isMissingNode(), status);
        }
        assertEquals("#/components/schemas/RestError",
                responses.path("409").path("content").path("application/json").path("schema").path("$ref").asText());
        assertTrue(document.toString().contains("\"SURPLUS_CLAIM_CIL_REQUIRED\""));
    }

    private JsonNode operation() throws Exception {
        return document().path("paths").path(PATH).path("get");
    }

    private JsonNode document() throws Exception {
        MvcResult result = mockMvc.perform(get("/api-docs")).andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private static JsonNode parameter(JsonNode operation, String name) {
        for (JsonNode parameter : operation.path("parameters")) {
            if (name.equals(parameter.path("name").asText())) {
                return parameter;
            }
        }
        throw new AssertionError("No parameter " + name);
    }

    private static List<String> texts(JsonNode array) {
        List<String> texts = new ArrayList<>();
        array.forEach(element -> texts.add(element.asText()));
        return texts;
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names.stream().sorted().toList();
    }
}

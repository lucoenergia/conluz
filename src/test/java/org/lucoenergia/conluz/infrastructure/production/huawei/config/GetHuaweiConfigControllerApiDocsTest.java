package org.lucoenergia.conluz.infrastructure.production.huawei.config;

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
 * Asserted against the OpenAPI document the application actually generates rather than against the
 * annotations meant to produce it. The response class was always annotated correctly; what was
 * missing was the link from the operation to it, so the generated client had no type for the body
 * (#421).
 */
class GetHuaweiConfigControllerApiDocsTest extends BaseControllerTest {

    private static final String SCHEMA = "GetHuaweiConfigResponse";
    private static final String PATH = "/api/v1/plants/{plantId}/production/huawei/config";

    @Test
    void documentsTheOkResponseAsGetHuaweiConfigResponse() throws Exception {
        JsonNode schema = operation(PATH, "get").path("responses").path("200")
                .path("content").path("application/json").path("schema");

        assertTrue(schema.path("$ref").asText().endsWith("/" + SCHEMA),
                () -> "the 200 of getHuaweiConfig is not a reference to " + SCHEMA + ": " + schema);
    }

    @Test
    void documentsEveryFieldAsRequired() throws Exception {
        List<String> required = textValues(schema(SCHEMA).path("required"));

        assertTrue(required.containsAll(List.of("username", "passwordSet", "baseUrl", "enabled")),
                () -> SCHEMA + ".required was " + required);
    }

    /**
     * The key is always present; its value may be null. The base type has to survive alongside the
     * null, or a generated client loses the type.
     */
    @Test
    void documentsTheUsernameAsANullableString() throws Exception {
        JsonNode username = schema(SCHEMA).path("properties").path("username");
        List<String> types = textValues(username.path("type"));

        assertTrue(types.contains("null"), () -> "username must be nullable: " + username);
        assertTrue(types.contains("string"), () -> "username lost its base type: " + username);
    }

    /**
     * {@code enabled} is what tells a configured but disabled plant from an enabled one, so it must
     * reach the client as a plain boolean, never as something that may be absent or null.
     */
    @Test
    void documentsPasswordSetAndEnabledAsBooleans() throws Exception {
        JsonNode properties = schema(SCHEMA).path("properties");

        for (String field : List.of("passwordSet", "enabled")) {
            List<String> types = textValues(properties.path(field).path("type"));
            assertTrue(types.contains("boolean"), () -> field + ".type was " + properties.path(field));
            assertTrue(!types.contains("null"), () -> field + " must not be nullable: " + properties.path(field));
        }
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
}

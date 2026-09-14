package org.lucoenergia.conluz.infrastructure.shared.web.error;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RestErrorDetailTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void serializesParamsAsFlatStringMap() throws Exception {

        RestErrorDetail detail = new RestErrorDetail("The file contains an invalid line",
                RestErrorCode.USER_LAST_PLATFORM_ADMIN, Map.of("lineNumber", "5", "value", "abc"));

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(detail));

        assertEquals("The file contains an invalid line", json.get("message").asText());
        assertEquals("USER_LAST_PLATFORM_ADMIN", json.get("code").asText());

        JsonNode params = json.get("params");
        assertTrue(params.isObject());
        assertEquals("5", params.get("lineNumber").asText());
        assertEquals("abc", params.get("value").asText());
        assertTrue(params.get("lineNumber").isTextual());
    }

    @Test
    void serializesNullCodeAndParamsAsExplicitNulls() throws Exception {

        RestErrorDetail detail = new RestErrorDetail("Something went wrong", null, null);

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(detail));

        assertEquals("Something went wrong", json.get("message").asText());
        assertTrue(json.get("code").isNull());
        assertTrue(json.get("params").isNull());
    }
}

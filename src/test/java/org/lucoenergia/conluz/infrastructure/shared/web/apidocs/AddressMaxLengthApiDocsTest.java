package org.lucoenergia.conluz.infrastructure.shared.web.apidocs;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The generated contract publishes the maximum length of every address of a request body, and the code a client gets
 * when a value is longer than its maximum (#574).
 */
class AddressMaxLengthApiDocsTest extends BaseControllerTest {

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"CreateSupplyBody", "UpdateSupplyBody", "CreatePlantBody", "UpdatePlantBody",
            "CreateCommunityBody", "UpdateCommunityBody", "RegisterMemberBody", "UpdateMemberRecordBody",
            "UpdateProfileBody", "CreateDefaultAdminUserBody"})
    @DisplayName("API-004 the address of each request body is published with a maxLength of 250")
    void theAddressOfEachRequestBodyIsPublishedWithAMaxLengthOf250(String schema) throws Exception {
        JsonNode address = document().path("components").path("schemas").path(schema).path("properties")
                .path("address");

        assertThat(address.isMissingNode()).as(schema + ".address").isFalse();
        assertThat(address.path("maxLength").asInt()).as(schema + ".address.maxLength").isEqualTo(250);
    }

    @Test
    @DisplayName("API-004 no request body publishes an address without a maxLength of 250")
    void noRequestBodyPublishesAnAddressWithoutTheMaximum() throws Exception {
        List<String> unbounded = new ArrayList<>();
        document().path("components").path("schemas").fields().forEachRemaining(schema -> {
            JsonNode address = schema.getValue().path("properties").path("address");
            if (schema.getKey().endsWith("Body") && !address.isMissingNode()
                    && address.path("maxLength").asInt() != 250) {
                unbounded.add(schema.getKey());
            }
        });

        assertThat(unbounded).isEmpty();
    }

    @Test
    @DisplayName("API-003 FIELD_TOO_LONG is a published error code and a published constraint of both imports")
    void fieldTooLongIsPublished() throws Exception {
        JsonNode schemas = document().path("components").path("schemas");

        assertThat(enumOf(schemas.path("RestErrorDetail").path("properties").path("code")))
                .contains("FIELD_TOO_LONG");
        assertThat(enumOf(schemas.path("ImportSuppliesRowErrorResponse").path("properties").path("constraint")))
                .contains("FIELD_TOO_LONG");
        assertThat(enumOf(schemas.path("ImportMembersRowErrorResponse").path("properties").path("constraint")))
                .contains("FIELD_TOO_LONG");
    }

    private static List<String> enumOf(JsonNode property) {
        List<String> values = new ArrayList<>();
        property.path("enum").forEach(value -> values.add(value.asText()));
        return values;
    }

    private JsonNode document() throws Exception {
        MvcResult result = mockMvc.perform(get("/api-docs")).andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}

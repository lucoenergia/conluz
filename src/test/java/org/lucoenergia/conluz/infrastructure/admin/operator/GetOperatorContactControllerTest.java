package org.lucoenergia.conluz.infrastructure.admin.operator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.lucoenergia.conluz.infrastructure.shared.security.auth.CraftedTokens;
import org.lucoenergia.conluz.infrastructure.shared.security.auth.JwtConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

import java.util.UUID;

import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Runs with the test configuration, which sets no operator contact.
 */
class GetOperatorContactControllerTest extends BaseControllerTest {

    private static final String URL = "/api/v1/operator-contact";

    @Autowired
    private JwtConfiguration jwtConfiguration;

    @Test
    @DisplayName("CTC-004 with no operator contact configured the contact is an explicit null")
    void withNoOperatorContactConfiguredTheContactIsAnExplicitNull() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasKey("contact")))
                .andExpect(jsonPath("$.contact").value(nullValue()));
    }

    @Test
    @DisplayName("CTC-004 a request without an Authorization header is answered")
    void aRequestWithoutAnAuthorizationHeaderIsAnswered() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("CTC-004 a request with an expired token is answered")
    void aRequestWithAnExpiredTokenIsAnswered() throws Exception {
        String expired = new CraftedTokens(jwtConfiguration.getSecretKey(), clock).expired(UUID.randomUUID());

        mockMvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, "Bearer " + expired))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contact").value(nullValue()));
    }

    @Test
    @DisplayName("CTC-004 a request with a malformed token is answered")
    void aRequestWithAMalformedTokenIsAnswered() throws Exception {
        mockMvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, "Bearer not-a-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contact").value(nullValue()));
    }

    @Test
    @DisplayName("CTC-004 the build information endpoint still returns its version only")
    void theBuildInformationEndpointStillReturnsItsVersionOnly() throws Exception {
        mockMvc.perform(get("/api/v1/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.*", hasSize(1)))
                .andExpect(jsonPath("$.version").isNotEmpty());
    }
}

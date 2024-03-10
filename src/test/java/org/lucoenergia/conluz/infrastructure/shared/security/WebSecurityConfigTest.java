package org.lucoenergia.conluz.infrastructure.shared.security;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WebSecurityConfigTest extends BaseControllerTest {

    @Test
    void testSecurityFilterChainForApiDocs() throws Exception {
        mockMvc.perform(get("/api-docs")).andExpect(status().isOk());
    }

    @Test
    void testSecurityFilterChainForHealth() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void testSecurityFilterChainForInfo() throws Exception {
        mockMvc.perform(get("/actuator/info")).andExpect(status().isOk());
    }
}
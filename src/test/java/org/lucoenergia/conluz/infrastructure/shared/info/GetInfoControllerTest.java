package org.lucoenergia.conluz.infrastructure.shared.info;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class GetInfoControllerTest extends BaseControllerTest {

    @Test
    void testGetInfoReturnsOkWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/info"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").isNotEmpty());
    }

    @Test
    void testGetInfoVersionMatchesExpectedPattern() throws Exception {
        mockMvc.perform(get("/api/v1/info"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(
                        matchesPattern("^\\d+\\.\\d+\\.\\d+$")));
    }
}

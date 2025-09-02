package org.lucoenergia.conluz.infrastructure.admin.user.get;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.user.DefaultUserAdminMother;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class GetCurrentUserControllerTest extends BaseControllerTest {

    private static final String URL = "/api/v1/users/current";

    @Test
    void testGetCurrentUserSuccess() throws Exception {
        String authHeader = loginAsDefaultAdmin();

        mockMvc.perform(get(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.personalId").value(DefaultUserAdminMother.PERSONAL_ID))
                .andExpect(jsonPath("$.fullName").value(DefaultUserAdminMother.FULL_NAME))
                .andExpect(jsonPath("$.email").value(DefaultUserAdminMother.EMAIL))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void testGetCurrentUserUnauthorizedWhenMissingToken() throws Exception {
        mockMvc.perform(get(URL))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(HttpStatus.UNAUTHORIZED.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }
}

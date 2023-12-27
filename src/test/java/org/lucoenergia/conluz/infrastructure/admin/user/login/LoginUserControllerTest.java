package org.lucoenergia.conluz.infrastructure.admin.user.login;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import static org.lucoenergia.conluz.domain.admin.user.DefaultUserAdminMother.PASSWORD;
import static org.lucoenergia.conluz.domain.admin.user.DefaultUserAdminMother.PERSONAL_ID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class LoginUserControllerTest extends BaseControllerTest {

    private static final String LOGIN_PATH = "/api/v1/login";

    @Test
    void testLoginWithBadCredentials() throws Exception {

        String loginBody = "{\"username\": \"" + PERSONAL_ID + "\",\"password\": \"" + PASSWORD + "\"}";

        mockMvc.perform(post(LOGIN_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value("401"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void testLoginWithoutBody() throws Exception {

        mockMvc.perform(post(LOGIN_PATH)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value("400"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void testLoginWithMalformedBody() throws Exception {

        String malformedBody = "{username: " + PERSONAL_ID + ", password: " + PASSWORD + "}";

        mockMvc.perform(post(LOGIN_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(malformedBody))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value("400"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void testLoginWithBodyWithMissingField() throws Exception {

        String loginBody = "{\"username\": \"" + PERSONAL_ID + "\"}";

        mockMvc.perform(post(LOGIN_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value("401"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void testLoginWithBodyWithFieldNotExpected() throws Exception {

        String loginBody = String.format("""
                {
                  "username": "%s",
                  "password": "%s",
                  "unknown": "foo"
                }
                        """, PERSONAL_ID, PASSWORD);

        mockMvc.perform(post(LOGIN_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value("400"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void testLoginWithBodyWithWrongField() throws Exception {

        String loginBody = String.format("""
                {
                  "username": "%s",
                  "pasword": "%s"
                }
                        """, PERSONAL_ID, PASSWORD);

        mockMvc.perform(post(LOGIN_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value("400"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void testLoginWithSuccess() throws Exception {

        init();

        String loginBody = String.format("""
                {
                  "username": "%s",
                  "password": "%s"
                }
                        """, PERSONAL_ID, PASSWORD);

        mockMvc.perform(post(LOGIN_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }
}

package org.lucoenergia.conluz.infrastructure.admin.config;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.get.GetUserRepository;
import org.lucoenergia.conluz.domain.shared.UserPersonalId;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

import static org.lucoenergia.conluz.domain.admin.user.DefaultUserAdminMother.PERSONAL_ID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class InitControllerTest extends BaseControllerTest {

    @Autowired
    private GetUserRepository getUserRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void testInit() throws Exception {

        init();

        Assertions.assertTrue(getUserRepository.existsByPersonalId(UserPersonalId.of(PERSONAL_ID)));
    }

    @Test
    void testInitCannotBeExecutedTwice() throws Exception {

        init();

        MvcResult result = init();
        Assertions.assertEquals(HttpStatus.FORBIDDEN.value(), result.getResponse().getStatus());
    }

    @Test
    void passwordOf14CodePoints_isRefused() throws Exception {
        allowInitialisationAgain();

        postInit(objectMapper.writeValueAsString(bodyWith("33600001A", "a".repeat(14))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("USER_PASSWORD_POLICY_VIOLATION"))
                .andExpect(jsonPath("$.errors[0].params.rule").value("TOO_SHORT"));

        Assertions.assertFalse(getUserRepository.existsByPersonalId(UserPersonalId.of("33600001A")));
    }

    @Test
    void passwordOf15CodePoints_isAccepted_andTheAdminIsNotFlagged() throws Exception {
        allowInitialisationAgain();

        postInit(objectMapper.writeValueAsString(bodyWith("33600002B", "a".repeat(15))))
                .andExpect(status().isOk());

        User admin = getUserRepository.findByPersonalId(UserPersonalId.of("33600002B")).orElseThrow();
        Assertions.assertFalse(admin.mustChangePassword());
        mockMvc.perform(post("/api/v1/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("username", "33600002B", "password", "a".repeat(15)))))
                .andExpect(status().isOk());
    }

    @Test
    void passwordOverSeventyTwoBytes_isRefusedWithTheBytesRule() throws Exception {
        allowInitialisationAgain();

        postInit(objectMapper.writeValueAsString(bodyWith("33600003C", "\u00F1".repeat(37))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("USER_PASSWORD_POLICY_VIOLATION"))
                .andExpect(jsonPath("$.errors[0].params.rule").value("TOO_MANY_BYTES"));
    }

    @Test
    void missingBody_answers400() throws Exception {
        postInit("").andExpect(status().isBadRequest());
    }

    @Test
    void emptyBody_answers400() throws Exception {
        postInit("{}").andExpect(status().isBadRequest());
    }

    @Test
    void emptyDefaultAdminUser_answers400() throws Exception {
        postInit("{\"defaultAdminUser\": {}}").andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"personalId", "fullName", "email", "password"})
    void missingRequiredField_answers400(String missingField) throws Exception {
        Map<String, Object> body = bodyWith("33600004D", "a secure password!!");
        @SuppressWarnings("unchecked")
        Map<String, Object> adminUser = (Map<String, Object>) body.get("defaultAdminUser");
        adminUser.remove(missingField);

        postInit(objectMapper.writeValueAsString(body)).andExpect(status().isBadRequest());

        Assertions.assertFalse(getUserRepository.existsByPersonalId(UserPersonalId.of("33600004D")));
    }

    /**
     * Other test classes may already have initialised the application in the shared database without rolling
     * it back. Clearing the flag inside this test's transaction lets {@code /init} run again; the change is
     * rolled back with the test.
     */
    private void allowInitialisationAgain() {
        jdbcTemplate.update("UPDATE config SET default_admin_user_initialized = false");
    }

    private static Map<String, Object> bodyWith(String personalId, String password) {
        Map<String, Object> adminUser = new HashMap<>();
        adminUser.put("personalId", personalId);
        adminUser.put("fullName", "Energy Community Acme");
        adminUser.put("email", "acme@email.com");
        adminUser.put("password", password);
        Map<String, Object> body = new HashMap<>();
        body.put("defaultAdminUser", adminUser);
        return body;
    }

    private ResultActions postInit(String body) throws Exception {
        return mockMvc.perform(post("/api/v1/init")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andDo(print());
    }
}

package org.lucoenergia.conluz.infrastructure.admin.user.create;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.lucoenergia.conluz.domain.admin.user.DefaultUserAdminMother;
import org.lucoenergia.conluz.domain.admin.user.Role;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.get.GetUserRepository;
import org.lucoenergia.conluz.domain.shared.UserPersonalId;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class CreateUserControllerTest extends BaseControllerTest {

    private static final String URL = "/api/v1/users";

    @Autowired
    private GetUserRepository getUserRepository;

    @Test
    void testFullBody() throws Exception {

        String authHeader = loginAsDefaultAdmin();

        String body = """
                        {
                          "personalId": "12345678Z",
                          "fullName": "John Doe",
                          "number": 1,
                          "address": "Fake Street 123",
                          "email": "johndoe@email.com",
                          "phoneNumber": "+34666555444",
                          "password": "a secure password1!",
                          "role": "PARTNER"
                        }
                """;

        User expectedUser = new User();
        expectedUser.setPersonalId("12345678Z");
        expectedUser.setNumber(1);
        expectedUser.setFullName("John Doe");
        expectedUser.setAddress("Fake Street 123");
        expectedUser.setEmail("johndoe@email.com");
        expectedUser.setPhoneNumber("+34666555444");
        expectedUser.setEnabled(true);
        expectedUser.setRole(Role.PARTNER);

        mockMvc.perform(post(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.personalId").value(expectedUser.getPersonalId()))
                .andExpect(jsonPath("$.number").value(expectedUser.getNumber()))
                .andExpect(jsonPath("$.fullName").value(expectedUser.getFullName()))
                .andExpect(jsonPath("$.address").value(expectedUser.getAddress()))
                .andExpect(jsonPath("$.email").value(expectedUser.getEmail()))
                .andExpect(jsonPath("$.phoneNumber").value(expectedUser.getPhoneNumber()))
                .andExpect(jsonPath("$.enabled").value(expectedUser.isEnabled()))
                .andExpect(jsonPath("$.role").value(expectedUser.getRole().name()))
                .andExpect(jsonPath("$.password").doesNotExist());

        Assertions.assertTrue(getUserRepository.existsByPersonalId(UserPersonalId.of(expectedUser.getPersonalId())));
    }

    @Test
    void testMinimumBody() throws Exception {

        String authHeader = loginAsDefaultAdmin();

        String body = """
                        {
                          "personalId": "12345678Z",
                          "fullName": "John Doe",
                          "number": 1,
                          "email": "johndoe@email.com",
                          "password": "a secure password1!",
                          "role": "PARTNER"
                        }
                """;

        User expectedUser = new User();
        expectedUser.setPersonalId("12345678Z");
        expectedUser.setNumber(1);
        expectedUser.setFullName("John Doe");
        expectedUser.setEmail("johndoe@email.com");
        expectedUser.setEnabled(true);
        expectedUser.setRole(Role.PARTNER);

        mockMvc.perform(post(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.personalId").value(expectedUser.getPersonalId()))
                .andExpect(jsonPath("$.number").value(expectedUser.getNumber()))
                .andExpect(jsonPath("$.fullName").value(expectedUser.getFullName()))
                .andExpect(jsonPath("$.address").value(expectedUser.getAddress()))
                .andExpect(jsonPath("$.email").value(expectedUser.getEmail()))
                .andExpect(jsonPath("$.phoneNumber").value(expectedUser.getPhoneNumber()))
                .andExpect(jsonPath("$.enabled").value(expectedUser.isEnabled()))
                .andExpect(jsonPath("$.role").value(expectedUser.getRole().name()))
                .andExpect(jsonPath("$.password").doesNotExist());

        Assertions.assertTrue(getUserRepository.existsByPersonalId(UserPersonalId.of(expectedUser.getPersonalId())));
    }

    @Test
    void testWithDuplicatedUser() throws Exception {

        String authHeader = loginAsDefaultAdmin();

        String body = String.format("""
                        {
                          "personalId": "%s",
                          "fullName": "John Doe",
                          "number": 1,
                          "email": "johndoe@email.com",
                          "password": "a secure password1!",
                          "role": "PARTNER"
                        }
                """, DefaultUserAdminMother.PERSONAL_ID);

        mockMvc.perform(post(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @ParameterizedTest
    @MethodSource("getBodyWithMissingRequiredFields")
    void testMissingRequiredFields(String body) throws Exception {

        String authHeader = loginAsDefaultAdmin();

        mockMvc.perform(post(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    static List<String> getBodyWithMissingRequiredFields() {
        return List.of("""
                        {
                          "fullName": "John Doe",
                          "number": 1,
                          "email": "johndoe@email.com",
                          "password": "a secure password1!",
                          "role": "PARTNER"
                        }
                """,
                """
                        {
                          "personalId": "12345678Z",
                          "number": 1,
                          "email": "johndoe@email.com",
                          "password": "a secure password1!",
                          "role": "PARTNER"
                        }
                """,
                """
                        {
                          "personalId": "12345678Z",
                          "fullName": "John Doe",
                          "email": "johndoe@email.com",
                          "password": "a secure password1!",
                          "role": "PARTNER"
                        }
                """,
                """
                        {
                          "personalId": "12345678Z",
                          "fullName": "John Doe",
                          "number": 1,
                          "password": "a secure password1!",
                          "role": "PARTNER"
                        }
                """,
                """
                        {
                          "personalId": "12345678Z",
                          "fullName": "John Doe",
                          "number": 1,
                          "email": "johndoe@email.com",
                          "role": "PARTNER"
                        }
                """,
                """
                        {
                          "personalId": "12345678Z",
                          "fullName": "John Doe",
                          "number": 1,
                          "email": "johndoe@email.com",
                          "password": "a secure password1!"
                        }
                """);
    }

    @ParameterizedTest
    @MethodSource("getBodyWithInvalidFormatValues")
    void testWithInvalidFormatValues(String body) throws Exception {

        String authHeader = loginAsDefaultAdmin();

        mockMvc.perform(post(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    static List<String> getBodyWithInvalidFormatValues() {
        return List.of("""
                    {
                        "personalId": "12345678Z",
                        "fullName": "John Doe",
                        "number": "invalid value",
                        "email": "johndoe@email.com",
                        "password": "a secure password1!",
                        "role": "PARTNER"
                    }
                """,
                """
                    {
                        "personalId": "12345678Z",
                        "fullName": "John Doe",
                        "number": 1,
                        "email": "invalid value",
                        "password": "a secure password1!",
                        "role": "PARTNER"
                    }
                """,
                """
                    {
                        "personalId": "12345678Z",
                        "fullName": "John Doe",
                        "number": 1,
                        "email": "johndoe@email.com",
                        "password": "a secure password1!",
                        "role": "invalid value"
                    }
                """);
    }

    @Test
    void testWithoutBody() throws Exception {
        String authHeader = loginAsDefaultAdmin();

        mockMvc.perform(post(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void testWithoutToken() throws Exception {

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(HttpStatus.UNAUTHORIZED.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void testAuthenticatedUserWithoutAdminRoleCannotAccess() throws Exception {

        String authHeader = loginAsPartner();

        String body = String.format("""
                        {
                          "personalId": "%s",
                          "fullName": "John Doe",
                          "number": 1,
                          "email": "johndoe@email.com",
                          "password": "a secure password1!",
                          "role": "PARTNER"
                        }
                """, DefaultUserAdminMother.PERSONAL_ID);

        mockMvc.perform(post(URL)
                        .content(body)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(HttpStatus.FORBIDDEN.value()));
    }
}

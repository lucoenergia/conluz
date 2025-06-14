package org.lucoenergia.conluz.infrastructure.admin.user.udpate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.lucoenergia.conluz.domain.admin.user.DefaultUserAdminMother;
import org.lucoenergia.conluz.domain.admin.user.Role;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.infrastructure.admin.user.update.UpdateUserBody;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class UpdateUserControllerTest extends BaseControllerTest {

    private static final String URL = "/api/v1/users";

    @Autowired
    private CreateUserRepository createUserRepository;

    @Test
    void testUpdateUser() throws Exception {

        String authHeader = loginAsDefaultAdmin();

        // Creates a user
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setPersonalId("12345678Z");
        user.setNumber(1);
        user.setFullName("John Doe");
        user.setAddress("Fake Street 123");
        user.setEmail("johndoe@email.com");
        user.setPhoneNumber("+34666555444");
        user.setPassword(UserMother.randomPassword());
        user.setEnabled(true);
        user.setRole(Role.ADMIN);
        createUserRepository.create(user);

        // Modify data of the user
        UpdateUserBody userModified = new UpdateUserBody();
        userModified.setNumber(2);
        userModified.setPersonalId("12345666A");
        userModified.setFullName("Alice Smith");
        userModified.setAddress("Fake Street 666");
        userModified.setEmail("alicesmith@email.com");
        userModified.setPhoneNumber("+34666555111");
        userModified.setRole(Role.PARTNER);

        String bodyAsString = objectMapper.writeValueAsString(userModified);

        mockMvc.perform(put(String.format(URL + "/%s", user.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyAsString))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId().toString()))
                .andExpect(jsonPath("$.personalId").value(userModified.getPersonalId()))
                .andExpect(jsonPath("$.number").value(userModified.getNumber()))
                .andExpect(jsonPath("$.fullName").value(userModified.getFullName()))
                .andExpect(jsonPath("$.address").value(userModified.getAddress()))
                .andExpect(jsonPath("$.email").value(userModified.getEmail()))
                .andExpect(jsonPath("$.phoneNumber").value(userModified.getPhoneNumber()))
                .andExpect(jsonPath("$.role").value(userModified.getRole().name()))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void testWithMissingNotRequiredFields() throws Exception {

        String authHeader = loginAsDefaultAdmin();

        // Creates a user
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setPersonalId("12345678Z");
        user.setNumber(1);
        user.setFullName("John Doe");
        user.setAddress("Fake Street 123");
        user.setEmail("johndoe@email.com");
        user.setPhoneNumber("+34666555444");
        user.setPassword(UserMother.randomPassword());
        user.setEnabled(true);
        user.setRole(Role.ADMIN);
        createUserRepository.create(user);

        // Modify data of the user
        UpdateUserBody userModified = new UpdateUserBody();
        userModified.setNumber(2);
        userModified.setPersonalId("12345666A");
        userModified.setFullName("Alice Smith");
        userModified.setRole(Role.PARTNER);

        String bodyAsString = objectMapper.writeValueAsString(userModified);

        mockMvc.perform(put(String.format(URL + "/%s", user.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyAsString))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId().toString()))
                .andExpect(jsonPath("$.personalId").value(userModified.getPersonalId()))
                .andExpect(jsonPath("$.number").value(userModified.getNumber()))
                .andExpect(jsonPath("$.fullName").value(userModified.getFullName()))
                .andExpect(jsonPath("$.address").isEmpty())
                .andExpect(jsonPath("$.email").value(userModified.getEmail()))
                .andExpect(jsonPath("$.phoneNumber").isEmpty())
                .andExpect(jsonPath("$.role").value(userModified.getRole().name()))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void testWithUnknownUser() throws Exception {

        String authHeader = loginAsDefaultAdmin();

        final String userId = UUID.randomUUID().toString();

        String body = """
                        {
                          "fullName": "John Doe",
                          "number": 1,
                          "email": "johndoe@email.com",
                          "role": "PARTNER"
                        }
                """;

        mockMvc.perform(put(URL + "/" + userId)
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

    @Test
    void testWithUnknownFields() throws Exception {

        final String body = """
                        {
                          "unknown": 1,
                          "email": "johndoe@email.com",
                          "role": "PARTNER"
                        }
                """;

        final String authHeader = loginAsDefaultAdmin();

        final String userId = UUID.randomUUID().toString();

        mockMvc.perform(put(URL + "/" + userId)
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

        final String authHeader = loginAsDefaultAdmin();

        final String userId = UUID.randomUUID().toString();

        mockMvc.perform(put(URL + "/" + userId)
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
        return List.of(
                """
                                {
                                  "number": 1,
                                  "email": "johndoe@email.com",
                                  "role": "PARTNER"
                                }
                        """,
                """
                                {
                                  "fullName": "John Doe",
                                  "email": "johndoe@email.com",
                                  "role": "PARTNER"
                                }
                        """,
                """
                                {
                                  "fullName": "John Doe",
                                  "number": 1,
                                  "role": "PARTNER"
                                }
                        """,
                """
                                {
                                  "fullName": "John Doe",
                                  "number": 1,
                                  "email": "johndoe@email.com"
                                }
                        """);
    }

    @ParameterizedTest
    @MethodSource("getBodyWithInvalidFormatValues")
    void testWithInvalidFormatValues(String body) throws Exception {

        final String authHeader = loginAsDefaultAdmin();

        final String userId = UUID.randomUUID().toString();

        mockMvc.perform(put(URL + "/" + userId)
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
                                "number": "invalid value",
                                "fullName": "John Doe",
                                "email": "johndoe@email.com",
                                "role": "PARTNER"
                            }
                        """,
                """
                            {
                                "number": 1,
                                "fullName": "John Doe",
                                "email": "invalid value",
                                "role": "PARTNER"
                            }
                        """,
                """
                            {
                                "number": 1,
                                "fullName": "John Doe",
                                "email": "johndoe@email.com",
                                "role": "invalid value"
                            }
                        """);
    }

    @Test
    void
    testWithoutBody() throws Exception {
        final String authHeader = loginAsDefaultAdmin();

        final String userId = UUID.randomUUID().toString();

        mockMvc.perform(put(URL + "/" + userId)
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
    void
    testWithoutIdInPath() throws Exception {
        final String authHeader = loginAsDefaultAdmin();

        mockMvc.perform(put(URL)
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

        mockMvc.perform(put(URL)
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

        // Test users endpoint
        mockMvc.perform(get(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(HttpStatus.FORBIDDEN.value()));
    }
}

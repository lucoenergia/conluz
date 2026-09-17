package org.lucoenergia.conluz.infrastructure.admin.user.update;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
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

        String authHeader = loginAsDefaultPlatformAdmin();

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
        createUserRepository.create(user);

        // Modify data of the user
        UpdateUserBody userModified = new UpdateUserBody();
        userModified.setNumber(2);
        userModified.setPersonalId("12345666A");
        userModified.setFullName("Alice Smith");
        userModified.setAddress("Fake Street 666");
        userModified.setEmail("alicesmith@email.com");
        userModified.setPhoneNumber("+34666555111");

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
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void testWithMissingNotRequiredFields() throws Exception {

        String authHeader = loginAsDefaultPlatformAdmin();

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
        createUserRepository.create(user);

        // Modify data of the user
        UpdateUserBody userModified = new UpdateUserBody();
        userModified.setNumber(2);
        userModified.setPersonalId("12345666A");
        userModified.setFullName("Alice Smith");

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
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void testWithUnknownUser() throws Exception {

        String authHeader = loginAsDefaultPlatformAdmin();

        final String userId = UUID.randomUUID().toString();

        String body = """
                        {
                          "fullName": "John Doe",
                          "number": 1,
                          "email": "johndoe@email.com"
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
                          "email": "johndoe@email.com"
                        }
                """;

        final String authHeader = loginAsDefaultPlatformAdmin();

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

        final String authHeader = loginAsDefaultPlatformAdmin();

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
                                  "email": "johndoe@email.com"
                                }
                        """,
                """
                                {
                                  "fullName": "John Doe",
                                  "email": "johndoe@email.com"
                                }
                        """,
                """
                                {
                                  "fullName": "John Doe",
                                  "number": 1
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

        final String authHeader = loginAsDefaultPlatformAdmin();

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
                                "email": "johndoe@email.com"
                            }
                        """,
                """
                            {
                                "number": 1,
                                "fullName": "John Doe",
                                "email": "invalid value"
                            }
                        """);
    }

    @Test
    void
    testWithoutBody() throws Exception {
        final String authHeader = loginAsDefaultPlatformAdmin();

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
        final String authHeader = loginAsDefaultPlatformAdmin();

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

        // Against the real path: PUT /api/v1/users has no mapping, so this used to pass only
        // because 401 precedes routing.
        mockMvc.perform(put(URL + "/" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(HttpStatus.UNAUTHORIZED.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void testPartnerEditingAnotherUserIsToldTheUserIsMissing() throws Exception {
        // This test used to GET the list endpoint, so the denial it claimed to prove for
        // PUT /users/{userId} was asserted nowhere. A partner shares no community with the target,
        // so they cannot see them at all -> 404, not 403.
        User target = UserMother.randomUser();
        createUserRepository.create(target);

        String authHeader = loginAsPartner();

        mockMvc.perform(put(URL + "/" + target.getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(HttpStatus.NOT_FOUND.value()));
    }

    @Test
    void testPartnerEditingThemselvesIsForbidden() throws Exception {
        // The administrative endpoint replaces personalId, fullName and number, so it stays closed
        // even on one's own record. Contact details are self-service through PUT /users/profile.
        User self = UserMother.randomUser();
        self.enable();
        createUserRepository.create(self);
        String authHeader = loginUser(self);

        mockMvc.perform(put(URL + "/" + self.getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andDo(print())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(HttpStatus.FORBIDDEN.value()));
    }

    private static String validBody() {
        return """
                {
                  "number": 7,
                  "personalId": "12345678Z",
                  "fullName": "John Doe",
                  "email": "johndoe@email.com"
                }
                """;
    }
}

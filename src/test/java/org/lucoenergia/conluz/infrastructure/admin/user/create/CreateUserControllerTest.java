package org.lucoenergia.conluz.infrastructure.admin.user.create;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.create.CreateCommunityRepository;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.admin.user.DefaultUserAdminMother;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.get.GetUserRepository;
import org.lucoenergia.conluz.domain.shared.UserPersonalId;
import org.lucoenergia.conluz.infrastructure.admin.user.UserEntity;
import org.lucoenergia.conluz.infrastructure.admin.user.UserRepository;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class CreateUserControllerTest extends BaseControllerTest {

    private static final String URL = "/api/v1/users";

    @Autowired
    private GetUserRepository getUserRepository;
    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CreateCommunityRepository createCommunityRepository;

    @Test
    void testFullBody() throws Exception {

        String authHeader = loginAsDefaultPlatformAdmin();

        String body = """
                        {
                          "personalId": "12345678Z",
                          "fullName": "John Doe",
                          "number": 1,
                          "address": "Fake Street 123",
                          "email": "johndoe@email.com",
                          "phoneNumber": "+34666555444",
                          "password": "a secure password1!"
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
                .andExpect(jsonPath("$.password").doesNotExist());

        Assertions.assertTrue(getUserRepository.existsByPersonalId(UserPersonalId.of(expectedUser.getPersonalId())));
    }

    @Test
    void testMinimumBody() throws Exception {

        String authHeader = loginAsDefaultPlatformAdmin();

        String body = """
                        {
                          "personalId": "12345678Z",
                          "fullName": "John Doe",
                          "number": 1,
                          "email": "johndoe@email.com",
                          "password": "a secure password1!"
                        }
                """;

        User expectedUser = new User();
        expectedUser.setPersonalId("12345678Z");
        expectedUser.setNumber(1);
        expectedUser.setFullName("John Doe");
        expectedUser.setEmail("johndoe@email.com");
        expectedUser.setEnabled(true);

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
                .andExpect(jsonPath("$.password").doesNotExist());

        Assertions.assertTrue(getUserRepository.existsByPersonalId(UserPersonalId.of(expectedUser.getPersonalId())));
    }

    @Test
    void testWithDuplicatedUser() throws Exception {

        String authHeader = loginAsDefaultPlatformAdmin();

        String body = String.format("""
                        {
                          "personalId": "%s",
                          "fullName": "John Doe",
                          "number": 1,
                          "email": "johndoe@email.com",
                          "password": "a secure password1!"
                        }
                """, DefaultUserAdminMother.PERSONAL_ID);

        mockMvc.perform(post(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andDo(print())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(HttpStatus.CONFLICT.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.errors[0].code").value("USER_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.errors[0].params").doesNotExist())
                .andExpect(content().string(not(containsString(DefaultUserAdminMother.PERSONAL_ID))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"12345678a", " 12345678 A ", "12345678-A", "12.345.678-A"})
    void testWithTypingVariantOfAnExistingPersonalIdIsAConflict(String variant) throws Exception {

        createUserRepository.create(UserMother.randomUserWithPersonalId("12345678A"));
        String authHeader = loginAsDefaultPlatformAdmin();

        mockMvc.perform(post(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWithPersonalId(variant)))
                .andDo(print())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("USER_ALREADY_EXISTS"))
                .andExpect(content().string(not(containsString("12345678"))));

        Assertions.assertEquals(1, userRepository.findAll().stream()
                .filter(user -> user.getPersonalId().contains("12345678"))
                .count());
    }

    @Test
    void testPersonalIdIsStoredNormalised() throws Exception {

        String authHeader = loginAsDefaultPlatformAdmin();

        mockMvc.perform(post(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWithPersonalId("x1234567-l")))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.personalId").value("X1234567L"));

        UserEntity stored = userRepository.findByPersonalId("X1234567L").orElseThrow();
        Assertions.assertEquals("X1234567L", stored.getPersonalId());
        Assertions.assertTrue(userRepository.findByPersonalId("x1234567-l").isEmpty());
    }

    @Test
    void testCommunityAdminCannotCreateUserInAnotherCommunity() throws Exception {

        Community communityA = createCommunityRepository.create(CommunityMother.random().build());
        Community communityB = createCommunityRepository.create(CommunityMother.random().build());
        String authHeader = loginAsCommunityAdmin(communityA.getId());

        String body = objectMapper.writeValueAsString(Map.of(
                "personalId", "33100009Z",
                "fullName", "John Doe",
                "number", 1,
                "email", "johndoe@email.com",
                "password", "a secure password1!",
                "communityId", communityB.getId().toString(),
                "communityRole", "COMMUNITY_MEMBER"));

        mockMvc.perform(post(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(HttpStatus.NOT_FOUND.value()));

        Assertions.assertTrue(userRepository.findByPersonalId("33100009Z").isEmpty());
    }

    @Test
    void passwordOf14CodePoints_isRefused() throws Exception {
        String authHeader = loginAsDefaultPlatformAdmin();

        createUser(authHeader, bodyWithPassword("33300001A", "a".repeat(14)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("USER_PASSWORD_POLICY_VIOLATION"))
                .andExpect(jsonPath("$.errors[0].params.rule").value("TOO_SHORT"));

        Assertions.assertTrue(userRepository.findByPersonalId("33300001A").isEmpty());
    }

    @Test
    void passwordOf15CodePoints_isAccepted() throws Exception {
        String authHeader = loginAsDefaultPlatformAdmin();

        createUser(authHeader, bodyWithPassword("33300002B", "a".repeat(15))).andExpect(status().isOk());

        loginWith("33300002B", "a".repeat(15)).andExpect(status().isOk());
    }

    @Test
    void passwordOf64CodePointsWithSpacesAndAccents_isAccepted_andOnlyTheExactValueLogsIn() throws Exception {
        String password = " contrase\u00F1a: el \u00F1and\u00FA corre por la pampa, sin prisa y sin pausa ";
        Assertions.assertEquals(64, password.codePointCount(0, password.length()));
        Assertions.assertTrue(password.getBytes(StandardCharsets.UTF_8).length <= 72);
        String authHeader = loginAsDefaultPlatformAdmin();

        createUser(authHeader, bodyWithPassword("33300003C", password)).andExpect(status().isOk());

        loginWith("33300003C", password).andExpect(status().isOk());
        loginWith("33300003C", password.trim()).andExpect(status().isUnauthorized());
    }

    @Test
    void passwordOverSeventyTwoBytes_isRefusedWithTheBytesRule_neverTruncatedNorA500() throws Exception {
        // 37 code points, 74 bytes: within the length rule, over BCrypt's input limit
        String password = "\u00F1".repeat(37);
        String authHeader = loginAsDefaultPlatformAdmin();

        createUser(authHeader, bodyWithPassword("33300004D", password))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("USER_PASSWORD_POLICY_VIOLATION"))
                .andExpect(jsonPath("$.errors[0].params.rule").value("TOO_MANY_BYTES"));

        Assertions.assertTrue(userRepository.findByPersonalId("33300004D").isEmpty());
    }

    @Test
    void longLowercaseOnlyPassword_isAccepted() throws Exception {
        String authHeader = loginAsDefaultPlatformAdmin();

        createUser(authHeader, bodyWithPassword("33300005E", "correcthorsebatterystaple"))
                .andExpect(status().isOk());

        loginWith("33300005E", "correcthorsebatterystaple").andExpect(status().isOk());
    }

    @Test
    void createdUser_isFlaggedAsHavingToChangeThePassword_andIsRefusedUntilTheyChangeIt() throws Exception {
        Community community = createCommunityRepository.create(CommunityMother.random().build());
        String authHeader = loginAsDefaultPlatformAdmin();
        String body = objectMapper.writeValueAsString(Map.of(
                "personalId", "33300006F",
                "fullName", "John Doe",
                "number", 1,
                "email", "johndoe@email.com",
                "password", "a secure password1!",
                "communityId", community.getId().toString(),
                "communityRole", "COMMUNITY_MEMBER"));

        createUser(authHeader, body).andExpect(status().isOk());
        Assertions.assertTrue(userRepository.findByPersonalId("33300006F").orElseThrow().mustChangePassword());

        String userToken = "Bearer " + objectMapper.readTree(loginWith("33300006F", "a secure password1!")
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("token").asText();

        // Until the password is changed, only the current user can be read (#342)
        mockMvc.perform(get(URL + "/current").header(HttpHeaders.AUTHORIZATION, userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(true));
        mockMvc.perform(put(URL + "/profile")
                        .header(HttpHeaders.AUTHORIZATION, userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "john.doe@email.com"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errors[0].code").value("USER_PASSWORD_CHANGE_REQUIRED"));
        mockMvc.perform(get("/api/v1/communities/" + community.getId())
                        .header(HttpHeaders.AUTHORIZATION, userToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errors[0].code").value("USER_PASSWORD_CHANGE_REQUIRED"));
    }

    private ResultActions createUser(String authHeader, String body)
            throws Exception {
        return mockMvc.perform(post(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andDo(print());
    }

    private ResultActions loginWith(String personalId, String password)
            throws Exception {
        return mockMvc.perform(post("/api/v1/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("username", personalId, "password", password))));
    }

    private String bodyWithPassword(String personalId, String password) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "personalId", personalId,
                "fullName", "John Doe",
                "number", 1,
                "email", "johndoe@email.com",
                "password", password));
    }

    private String bodyWithPersonalId(String personalId) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "personalId", personalId,
                "fullName", "John Doe",
                "number", 1,
                "email", "johndoe@email.com",
                "password", "a secure password1!"));
    }

    @ParameterizedTest
    @MethodSource("getBodyWithMissingRequiredFields")
    void testMissingRequiredFields(String body) throws Exception {

        String authHeader = loginAsDefaultPlatformAdmin();

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
                          "password": "a secure password1!"
                        }
                """,
                """
                        {
                          "personalId": "12345678Z",
                          "number": 1,
                          "email": "johndoe@email.com",
                          "password": "a secure password1!"
                        }
                """,
                """
                        {
                          "personalId": "12345678Z",
                          "fullName": "John Doe",
                          "email": "johndoe@email.com",
                          "password": "a secure password1!"
                        }
                """,
                """
                        {
                          "personalId": "12345678Z",
                          "fullName": "John Doe",
                          "number": 1,
                          "password": "a secure password1!"
                        }
                """,
                """
                        {
                          "personalId": "12345678Z",
                          "fullName": "John Doe",
                          "number": 1,
                          "email": "johndoe@email.com"
                        }
                """);
    }

    @ParameterizedTest
    @MethodSource("getBodyWithInvalidFormatValues")
    void testWithInvalidFormatValues(String body) throws Exception {

        String authHeader = loginAsDefaultPlatformAdmin();

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
                        "password": "a secure password1!"
                    }
                """,
                """
                    {
                        "personalId": "12345678Z",
                        "fullName": "John Doe",
                        "number": 1,
                        "email": "invalid value",
                        "password": "a secure password1!"
                    }
                """);
    }

    @Test
    void testWithoutBody() throws Exception {
        String authHeader = loginAsDefaultPlatformAdmin();

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
                          "password": "a secure password1!"
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

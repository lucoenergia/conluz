package org.lucoenergia.conluz.infrastructure.admin.user.update;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.create.CreateCommunityRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.CreateMembershipService;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.admin.user.get.GetUserRepository;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityMembershipJpaRepository;
import org.lucoenergia.conluz.infrastructure.admin.community.MembershipStateTestSupport;
import org.lucoenergia.conluz.infrastructure.admin.user.update.UpdateUserBody;
import org.lucoenergia.conluz.infrastructure.admin.user.UserRepository;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class UpdateUserControllerTest extends BaseControllerTest {

    private static final String URL = "/api/v1/users";

    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private GetUserRepository getUserRepository;
    @Autowired
    private CreateCommunityRepository createCommunityRepository;
    @Autowired
    private CreateMembershipService createMembershipService;
    @Autowired
    private CommunityMembershipJpaRepository membershipJpaRepository;

    @Test
    @DisplayName("ACC-008 a community admin editing an active member of their community is refused, and the account is unchanged")
    void aCommunityAdminEditingAMemberIsForbidden() throws Exception {
        Community community = newCommunity();
        User member = persistMemberOf(community);
        List<Object> before = accountOf(member);

        mockMvc.perform(put(URL + "/{userId}", member.getId())
                        .header(HttpHeaders.AUTHORIZATION, loginAsCommunityAdmin(community.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(takeoverBody())))
                .andExpect(status().isForbidden());

        assertEquals(before, accountOf(member));
    }

    @Test
    @DisplayName("ACC-008 a community admin editing a member who also belongs to another community is refused, and the account is unchanged")
    void aCommunityAdminEditingAMemberOfTwoCommunitiesIsForbidden() throws Exception {
        Community administered = newCommunity();
        Community other = newCommunity();
        User member = persistMemberOf(administered);
        createMembershipService.create(other.getId(), member.getId(), CommunityRole.COMMUNITY_MEMBER);
        List<Object> before = accountOf(member);

        mockMvc.perform(put(URL + "/{userId}", member.getId())
                        .header(HttpHeaders.AUTHORIZATION, loginAsCommunityAdmin(administered.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(takeoverBody())))
                .andExpect(status().isForbidden());

        assertEquals(before, accountOf(member));
    }

    /**
     * A pending member is not visible to a community admin (MEM-004), so the refusal is the 404 every
     * other read of them gets, not a 403.
     */
    @Test
    @DisplayName("ACC-008 a community admin editing a pending member of their community is refused, and the account is unchanged")
    void aCommunityAdminEditingAPendingMemberIsToldTheUserIsMissing() throws Exception {
        Community community = newCommunity();
        User member = persistMemberOf(community);
        MembershipStateTestSupport.markPending(membershipJpaRepository, member.getId(), community.getId());
        List<Object> before = accountOf(member);

        mockMvc.perform(put(URL + "/{userId}", member.getId())
                        .header(HttpHeaders.AUTHORIZATION, loginAsCommunityAdmin(community.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(takeoverBody())))
                .andExpect(status().isNotFound());

        assertEquals(before, accountOf(member));
    }

    @Test
    @DisplayName("ACC-008 a community admin still edits their own account")
    void aCommunityAdminEditingThemselvesIsAllowed() throws Exception {
        Community community = newCommunity();
        User admin = UserMother.randomUser();
        admin.enable();
        createUserRepository.create(admin);
        createMembershipService.create(community.getId(), admin.getId(), CommunityRole.COMMUNITY_ADMIN);
        UpdateUserBody body = bodyWithPersonalId(admin.getPersonalId());

        mockMvc.perform(put(URL + "/{userId}", admin.getId())
                        .header(HttpHeaders.AUTHORIZATION, loginUser(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());

        User stored = getUserRepository.findById(UserId.of(admin.getId())).orElseThrow();
        assertEquals(body.getFullName(), stored.getFullName());
        assertEquals(body.getEmail(), stored.getEmail());
    }

    @Test
    @DisplayName("ACC-008 the platform admin still edits the account of a community member")
    void thePlatformAdminEditingACommunityMemberIsAllowed() throws Exception {
        Community community = newCommunity();
        User member = persistMemberOf(community);
        UpdateUserBody body = takeoverBody();

        mockMvc.perform(put(URL + "/{userId}", member.getId())
                        .header(HttpHeaders.AUTHORIZATION, loginAsDefaultPlatformAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());

        User stored = getUserRepository.findById(UserId.of(member.getId())).orElseThrow();
        assertEquals(List.of(body.getNumber(), body.getPersonalId(), body.getFullName(), body.getAddress(),
                        body.getEmail(), body.getPhoneNumber()),
                Arrays.asList(stored.getNumber(), stored.getPersonalId(), stored.getFullName(), stored.getAddress(),
                        stored.getEmail(), stored.getPhoneNumber()));
    }

    private Community newCommunity() {
        return createCommunityRepository.create(CommunityMother.random().build());
    }

    private User persistMemberOf(Community community) {
        User member = UserMother.randomUser();
        member.enable();
        createUserRepository.create(member);
        createMembershipService.create(community.getId(), member.getId(), CommunityRole.COMMUNITY_MEMBER);
        return member;
    }

    /**
     * Every field of the stored account, read back through the domain, so that a refused request can be
     * shown to have written nothing.
     */
    private List<Object> accountOf(User user) {
        User stored = getUserRepository.findById(UserId.of(user.getId())).orElseThrow();
        return Arrays.asList(stored.getPersonalId(), stored.getNumber(), stored.getFullName(), stored.getAddress(),
                stored.getEmail(), stored.getPhoneNumber(), stored.getPassword(), stored.isEnabled(),
                stored.isPlatformAdmin(), stored.getPasswordChangedAt(), stored.getDisabledAt());
    }

    /**
     * A body changing every field, with an email the caller would control.
     */
    private static UpdateUserBody takeoverBody() {
        UpdateUserBody body = new UpdateUserBody();
        body.setNumber(999);
        body.setPersonalId("99999999R");
        body.setFullName("Taken Over");
        body.setAddress("Attacker Street 1");
        body.setEmail("attacker@example.com");
        body.setPhoneNumber("+34600000000");
        return body;
    }

    @ParameterizedTest
    @ValueSource(strings = {"12345678a", " 12345678 A ", "12345678-A", "12.345.678-A"})
    void testChangingThePersonalIdToAVariantOfAnotherUsersIsAConflict(String variant) throws Exception {

        String authHeader = loginAsDefaultPlatformAdmin();
        User other = persistUser("12345678A");
        User user = persistUser("87654321B");

        mockMvc.perform(put(String.format(URL + "/%s", user.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bodyWithPersonalId(variant))))
                .andDo(print())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("USER_ALREADY_EXISTS"))
                .andExpect(content().string(not(containsString("12345678"))));

        assertEquals("87654321B", userRepository.findById(user.getId()).orElseThrow().getPersonalId());
        assertEquals("12345678A", userRepository.findById(other.getId()).orElseThrow().getPersonalId());
    }

    @Test
    void testUpdatingWithAVariantOfTheUsersOwnPersonalIdStoresItNormalised() throws Exception {

        String authHeader = loginAsDefaultPlatformAdmin();
        User user = persistUser("12345678A");

        mockMvc.perform(put(String.format(URL + "/%s", user.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bodyWithPersonalId("12.345.678-a"))))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.personalId").value("12345678A"));

        assertEquals("12345678A", userRepository.findById(user.getId()).orElseThrow().getPersonalId());
    }

    private static UpdateUserBody bodyWithPersonalId(String personalId) {
        UpdateUserBody body = new UpdateUserBody();
        body.setNumber(2);
        body.setPersonalId(personalId);
        body.setFullName("Alice Smith");
        body.setEmail("alice.smith@email.com");
        return body;
    }

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

    /**
     * Address and phone number are genuinely optional: clearing them is a legitimate edit, and the
     * columns allow it. The email is not, so it is supplied here -- the case that omits it is
     * {@link #testWithoutEmail()}.
     */
    @Test
    void testWithMissingNotRequiredFields() throws Exception {

        String authHeader = loginAsDefaultPlatformAdmin();

        User user = persistUser("12345678Z");

        // Modify data of the user
        UpdateUserBody userModified = new UpdateUserBody();
        userModified.setNumber(2);
        userModified.setPersonalId("12345666A");
        userModified.setFullName("Alice Smith");
        userModified.setEmail("alice.smith@email.com");

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

    /**
     * Omitting the email used to answer 200 with {@code "email": null} while describing an update
     * the database could never accept: this endpoint overwrites the stored value with whatever the
     * body carries, and {@code users.email} is NOT NULL. The violation surfaced only once the
     * transaction flushed, after the response had been rendered, so the caller was told an edit had
     * succeeded that had in fact been rolled back.
     *
     * <p>A user must have an email, as creation and the self-service profile edit already required.
     * So the request is rejected up front instead.</p>
     */
    @Test
    void testWithoutEmail() throws Exception {

        String authHeader = loginAsDefaultPlatformAdmin();

        User user = persistUser("12345679R");

        UpdateUserBody userModified = new UpdateUserBody();
        userModified.setNumber(2);
        userModified.setPersonalId("12345667B");
        userModified.setFullName("Alice Smith");

        mockMvc.perform(put(String.format(URL + "/%s", user.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(userModified)))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    /**
     * The stored email must be untouched by the rejected request -- the point of rejecting it is
     * that the previous behaviour left the caller unable to tell.
     */
    @Test
    void testWithoutEmailLeavesTheStoredEmailIntact() throws Exception {

        String authHeader = loginAsDefaultPlatformAdmin();

        User user = persistUser("12345680T");

        UpdateUserBody userModified = new UpdateUserBody();
        userModified.setNumber(2);
        userModified.setPersonalId("12345668C");
        userModified.setFullName("Alice Smith");

        mockMvc.perform(put(String.format(URL + "/%s", user.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(userModified)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get(String.format(URL + "/%s", user.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("johndoe@email.com"))
                .andExpect(jsonPath("$.fullName").value("John Doe"));
    }

    @Test
    void testWithBlankEmail() throws Exception {

        String authHeader = loginAsDefaultPlatformAdmin();

        User user = persistUser("12345681W");

        String body = """
                        {
                          "number": 2,
                          "personalId": "12345669D",
                          "fullName": "Alice Smith",
                          "email": "  "
                        }
                """;

        mockMvc.perform(put(String.format(URL + "/%s", user.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    private User persistUser(String personalId) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setPersonalId(personalId);
        user.setNumber(1);
        user.setFullName("John Doe");
        user.setAddress("Fake Street 123");
        user.setEmail("johndoe@email.com");
        user.setPhoneNumber("+34666555444");
        user.setPassword(UserMother.randomPassword());
        user.setEnabled(true);
        createUserRepository.create(user);
        return user;
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

package org.lucoenergia.conluz.infrastructure.admin.user;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.create.CreateCommunityRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.CreateMembershipRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The legacy user and membership creation endpoints, and granting the platform-admin role by id, are gone (#447):
 * accounts are created by {@code /init}, member registration, import and invitations, a person edits their own
 * contact details through {@code PUT /api/v1/users/profile}, and the platform-admin role is granted only by accepting
 * an invitation. Each request is sent with a valid body, by a caller who could have used the endpoint, so the answer
 * can only come from the route no longer existing.
 */
@Transactional
class RetiredAccountCreationEndpointsControllerTest extends BaseControllerTest {

    @Autowired
    private CreateCommunityRepository createCommunityRepository;
    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateMembershipRepository createMembershipRepository;
    @Autowired
    private MessageSource messageSource;

    private Community community;
    private User member;

    @BeforeEach
    void createACommunityWithAMember() {
        community = createCommunityRepository.create(CommunityMother.random().build());
        member = UserMother.randomUser();
        member.enable();
        createUserRepository.create(member);
        createMembershipRepository.create(community.getId(), member.getId(), CommunityRole.COMMUNITY_MEMBER);
    }

    @Test
    @DisplayName("ACC-004 creating, importing and updating users by id no longer exist, whoever asks")
    void theUserCreationImportAndUpdateAreGone() throws Exception {
        for (String bearer : List.of(loginAsDefaultPlatformAdmin(), loginAsCommunityAdmin(community.getId()))) {
            assertGone(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content(json(Map.of(
                    "personalId", UserMother.randomPersonalId(),
                    "fullName", "Created By Someone Else",
                    "number", 7,
                    "email", "created@example.org",
                    "password", "a secure password1!",
                    "communityId", community.getId().toString(),
                    "communityRole", "COMMUNITY_ADMIN"))), bearer);
            assertGone(multipart("/api/v1/users/import")
                    .file(new MockMultipartFile("file", "users.csv", "text/csv", (
                            "number,fullName,personalId,address,email,phoneNumber,role,password,communityId,"
                                    + "communityRole\n1,Imported By Someone Else," + UserMother.randomPersonalId()
                                    + ",1 Test St,imported@example.org,600000001,partner,a secure password1!,,"
                                    + "COMMUNITY_ADMIN\n").getBytes(StandardCharsets.UTF_8)))
                    .param("communityId", community.getId().toString()), bearer);
            assertGone(put("/api/v1/users/{userId}", member.getId()).contentType(MediaType.APPLICATION_JSON)
                    .content(json(Map.of(
                            "personalId", UserMother.randomPersonalId(),
                            "fullName", "Renamed By Someone Else",
                            "number", 1,
                            "email", "renamed@example.org"))), bearer);
        }
    }

    @Test
    @DisplayName("MEM-031 adding an existing user to a community by id no longer exists")
    void theMembershipCreationIsGone() throws Exception {
        User user = UserMother.randomUser();
        user.enable();
        createUserRepository.create(user);

        assertGone(post("/api/v1/communities/{communityId}/memberships", community.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("userId", user.getId(), "role", "COMMUNITY_ADMIN"))),
                loginAsCommunityAdmin(community.getId()));
    }

    @Test
    @DisplayName("ACC-014 granting the platform-admin role by id no longer exists")
    void grantingThePlatformAdminRoleByIdIsGone() throws Exception {
        assertGone(post("/api/v1/users/{userId}/grant-platform-admin", member.getId()),
                loginAsDefaultPlatformAdmin());
    }

    @Test
    @DisplayName("ACC-004 ACC-014 MEM-031 the API document describes none of the retired operations")
    void theApiDocumentDescribesNoneOfThem() throws Exception {
        JsonNode paths = objectMapper.readTree(mockMvc.perform(get("/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("paths");

        assertThat(paths.path("/api/v1/users").has("post")).isFalse();
        assertThat(paths.has("/api/v1/users/import")).isFalse();
        assertThat(paths.path("/api/v1/users/{userId}").has("put")).isFalse();
        assertThat(paths.path("/api/v1/communities/{communityId}/memberships").has("post")).isFalse();
        assertThat(paths.has("/api/v1/users/{userId}/grant-platform-admin")).isFalse();
        // The siblings that stay are still described, so the assertions above look in the right place
        assertThat(paths.path("/api/v1/users").has("get")).isTrue();
        assertThat(paths.path("/api/v1/users/{userId}").has("get")).isTrue();
        assertThat(paths.path("/api/v1/communities/{communityId}/memberships").has("get")).isTrue();
        assertThat(paths.has("/api/v1/users/{userId}/revoke-platform-admin")).isTrue();
    }

    /**
     * The route no longer exists. When a sibling route with another method remains on the same path (a GET, say), the
     * application answers an unsupported method with 400 and its own message rather than 405, as it does for every
     * endpoint; that message is what tells the two 400s apart.
     */
    private void assertGone(MockHttpServletRequestBuilder request, String bearer) throws Exception {
        MockHttpServletResponse response = mockMvc.perform(request
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en"))
                .andReturn().getResponse();
        String body = response.getContentAsString();
        assertThat(response.getStatus()).as("the route must not exist: %s", body).isIn(400, 404, 405);
        if (response.getStatus() == 400) {
            String methodNotSupported = messageSource.getMessage("error.http.request.method.not.supported",
                    new Object[]{request.buildRequest(new MockServletContext()).getMethod()}, Locale.ENGLISH);
            assertThat(objectMapper.readTree(body).path("message").asText())
                    .as("a 400 must be the unsupported method, not a refused body: %s", body)
                    .isEqualTo(methodNotSupported);
        }
    }

    private String json(Map<String, Object> body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }
}

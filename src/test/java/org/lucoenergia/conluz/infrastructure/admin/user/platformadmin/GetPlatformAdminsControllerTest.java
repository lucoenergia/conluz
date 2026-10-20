package org.lucoenergia.conluz.infrastructure.admin.user.platformadmin;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.membership.CreateMembershipService;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.admin.user.get.GetUserRepository;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.ManagePlatformAdminAccessService;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.domain.shared.UserPersonalId;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lucoenergia.conluz.domain.admin.user.DefaultUserAdminMother.EMAIL;
import static org.lucoenergia.conluz.domain.admin.user.DefaultUserAdminMother.FULL_NAME;
import static org.lucoenergia.conluz.domain.admin.user.DefaultUserAdminMother.PERSONAL_ID;
import static org.lucoenergia.conluz.infrastructure.admin.supply.create.CreateSupplyRepositoryDatabase.DEFAULT_COMMUNITY_ID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class GetPlatformAdminsControllerTest extends BaseControllerTest {

    private static final String URL = "/api/v1/platform-admins";

    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private GetUserRepository getUserRepository;
    @Autowired
    private CreateMembershipService createMembershipService;
    @Autowired
    private ManagePlatformAdminAccessService managePlatformAdminAccessService;

    @Test
    @DisplayName("ACC-011 lists exactly the platform admins, enabled or not, by full name")
    void listsExactlyThePlatformAdmins() throws Exception {
        String authHeader = loginAsDefaultPlatformAdmin();
        User defaultAdmin = defaultAdmin();
        // Sorts before the default admin although it is created after it
        User disabledAdmin = platformAdmin("Aaron Disabled Admin", false, null);
        // Users who are not platform admins, of every kind
        persistUser("Abel Plain User", true);
        createMembershipService.create(DEFAULT_COMMUNITY_ID, persistUser("Abigail Member", true).getId(),
                CommunityRole.COMMUNITY_MEMBER);
        createMembershipService.create(DEFAULT_COMMUNITY_ID, persistUser("Adam Community Admin", true).getId(),
                CommunityRole.COMMUNITY_ADMIN);

        mockMvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].user.id").value(disabledAdmin.getId().toString()))
                .andExpect(jsonPath("$[0].fullName").value("Aaron Disabled Admin"))
                .andExpect(jsonPath("$[0].email").value(nullValue()))
                .andExpect(jsonPath("$[0].enabled").value(false))
                .andExpect(jsonPath("$[1].user.id").value(defaultAdmin.getId().toString()))
                .andExpect(jsonPath("$[1].fullName").value(FULL_NAME))
                .andExpect(jsonPath("$[1].email").value(EMAIL))
                .andExpect(jsonPath("$[1].enabled").value(true));
    }

    @Test
    @DisplayName("ACC-011 platform admins with the same full name are listed by id")
    void platformAdminsWithTheSameNameAreListedById() throws Exception {
        String authHeader = loginAsDefaultPlatformAdmin();
        User first = platformAdmin("Aaron Namesake", true, null);
        User second = platformAdmin("Aaron Namesake", true, null);
        // PostgreSQL orders uuids byte by byte, which is the order of their lowercase hex strings
        List<String> expected = new ArrayList<>(List.of(first.getId().toString(), second.getId().toString()));
        expected.sort(String::compareTo);

        JsonNode rows = list(authHeader);

        assertEquals(expected, List.of(rows.get(0).get("user").get("id").asText(),
                rows.get(1).get("user").get("id").asText()));
    }

    @Test
    @DisplayName("ACC-011 each row carries only the declared account fields: no personal id, no memberships")
    void eachRowCarriesOnlyTheDeclaredFields() throws Exception {
        String authHeader = loginAsDefaultPlatformAdmin();
        User admin = platformAdmin("Aaron Member Admin", true, "aaron@example.com");
        // Member data and a membership the response must not leak
        createMembershipService.create(DEFAULT_COMMUNITY_ID, admin.getId(), CommunityRole.COMMUNITY_ADMIN);

        JsonNode rows = list(authHeader);

        assertEquals(2, rows.size());
        for (JsonNode row : rows) {
            assertEquals(Set.of("user", "fullName", "email", "enabled", "revocable", "invitation", "capabilities"),
                    keys(row));
            assertEquals(Set.of("id"), keys(row.get("user")));
            // A user holding the role has no pending invitation (ADR-0004 §7)
            assertTrue(row.get("invitation").isNull());
            assertEquals(Set.of("canRevoke", "canResendInvitation", "canRevokeInvitation"),
                    keys(row.get("capabilities")));
        }
    }

    @Test
    @DisplayName("ACC-011 a community admin may not list the platform admins")
    void aCommunityAdminIsRefused() throws Exception {
        mockMvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ACC-011 a community member may not list the platform admins")
    void aCommunityMemberIsRefused() throws Exception {
        mockMvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, loginAsCommunityMember(DEFAULT_COMMUNITY_ID)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ACC-011 a user of no community may not list the platform admins")
    void aUserOfNoCommunityIsRefused() throws Exception {
        mockMvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, loginAsPartner()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ACC-011 an anonymous caller may not list the platform admins")
    void anAnonymousCallerIsRefused() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("ACC-011 granting the role adds the user to the list, and revoking it removes them")
    void theListFollowsGrantAndRevoke() throws Exception {
        String authHeader = loginAsDefaultPlatformAdmin();
        User user = persistUser("Aaron Future Admin", true);
        assertFalse(listedIds(authHeader).contains(user.getId().toString()));

        mockMvc.perform(post("/api/v1/users/" + user.getId() + "/grant-platform-admin")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk());
        assertTrue(listedIds(authHeader).contains(user.getId().toString()));

        mockMvc.perform(post("/api/v1/users/" + user.getId() + "/revoke-platform-admin")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk());
        assertFalse(listedIds(authHeader).contains(user.getId().toString()));
    }

    @Test
    @DisplayName("ACC-011 with one enabled platform admin a disabled one is revocable, and revoking it really succeeds")
    void withOneEnabledPlatformAdminADisabledOneIsRevocable() throws Exception {
        String authHeader = loginAsDefaultPlatformAdmin();
        User self = defaultAdmin();
        // A disabled platform admin does not count towards the last-platform-admin rule
        User disabledAdmin = platformAdmin("Aaron Disabled Admin", false, null);

        JsonNode rows = list(authHeader);
        JsonNode disabledRow = rowOf(rows, disabledAdmin.getId());
        JsonNode selfRow = rowOf(rows, self.getId());

        assertTrue(disabledRow.get("revocable").asBoolean());
        assertTrue(disabledRow.get("capabilities").get("canRevoke").asBoolean());
        assertFalse(selfRow.get("revocable").asBoolean());

        mockMvc.perform(post("/api/v1/users/" + disabledAdmin.getId() + "/revoke-platform-admin")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk());
        assertFalse(listedIds(authHeader).contains(disabledAdmin.getId().toString()));
    }

    @Test
    @DisplayName("ACC-011 the last enabled platform admin is not revocable, and revoking it is refused")
    void theLastEnabledPlatformAdminIsNotRevocable() throws Exception {
        String authHeader = loginAsDefaultPlatformAdmin();
        // The last enabled platform admin is necessarily the caller: a disabled account cannot authenticate
        User self = defaultAdmin();
        platformAdmin("Aaron Disabled Admin", false, null);

        JsonNode selfRow = rowOf(list(authHeader), self.getId());

        assertFalse(selfRow.get("revocable").asBoolean());
        assertFalse(selfRow.get("capabilities").get("canRevoke").asBoolean());

        // The guard answers first; LastPlatformAdminException is covered by ManagePlatformAdminAccessServiceTest
        mockMvc.perform(post("/api/v1/users/" + self.getId() + "/revoke-platform-admin")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ACC-011 with two enabled platform admins the other one is revocable, and revoking really succeeds")
    void withTwoEnabledPlatformAdminsTheOtherOneIsRevocable() throws Exception {
        String authHeader = loginAsDefaultPlatformAdmin();
        User self = defaultAdmin();
        User otherAdmin = platformAdmin("Aaron Other Admin", true, null);

        JsonNode rows = list(authHeader);
        JsonNode otherRow = rowOf(rows, otherAdmin.getId());
        JsonNode selfRow = rowOf(rows, self.getId());

        assertTrue(otherRow.get("revocable").asBoolean());
        assertTrue(otherRow.get("capabilities").get("canRevoke").asBoolean());
        assertTrue(selfRow.get("revocable").asBoolean());
        assertFalse(selfRow.get("capabilities").get("canRevoke").asBoolean());

        mockMvc.perform(post("/api/v1/users/" + self.getId() + "/revoke-platform-admin")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/users/" + otherAdmin.getId() + "/revoke-platform-admin")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk());
    }

    private User defaultAdmin() {
        return getUserRepository.findByPersonalId(UserPersonalId.of(PERSONAL_ID)).orElseThrow();
    }

    private User persistUser(String fullName, boolean enabled) {
        User user = UserMother.randomUser();
        user.setFullName(fullName);
        user.setEnabled(enabled);
        createUserRepository.create(user);
        return user;
    }

    private User platformAdmin(String fullName, boolean enabled, String email) {
        User user = UserMother.randomUser();
        user.setFullName(fullName);
        user.setEnabled(enabled);
        user.setEmail(email);
        createUserRepository.create(user);
        managePlatformAdminAccessService.grant(UserId.of(user.getId()));
        return user;
    }

    private JsonNode list(String authHeader) throws Exception {
        String body = mockMvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private List<String> listedIds(String authHeader) throws Exception {
        List<String> ids = new ArrayList<>();
        list(authHeader).forEach(row -> ids.add(row.get("user").get("id").asText()));
        return ids;
    }

    private JsonNode rowOf(JsonNode rows, UUID userId) {
        for (JsonNode row : rows) {
            if (row.get("user").get("id").asText().equals(userId.toString())) {
                return row;
            }
        }
        throw new AssertionError("no row for user " + userId + " in " + rows);
    }

    private Set<String> keys(JsonNode node) {
        Set<String> keys = new TreeSet<>();
        node.fieldNames().forEachRemaining(keys::add);
        return keys;
    }
}

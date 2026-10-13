package org.lucoenergia.conluz.infrastructure.admin.community.membership;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.create.CreateCommunityRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityMembershipJpaRepository;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Every existing path that creates a membership fills its member record from the user and accepts
 * it on creation (#388). The rows are read back with plain SQL, so what is checked is what is
 * stored, not what the entity mapping returns.
 */
@Transactional
class MembershipMemberRecordOnCreationTest extends BaseControllerTest {

    @Autowired
    private CreateCommunityRepository createCommunityRepository;
    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CommunityMembershipJpaRepository membershipJpaRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("MEM-001 MEM-002 a membership added to a community holds the user's record and is accepted on creation")
    void addingAMemberToACommunity() throws Exception {
        Community community = createCommunityRepository.create(CommunityMother.random().build());
        User user = UserMother.randomUser();
        user.enable();
        createUserRepository.create(user);
        String adminToken = loginAsCommunityAdmin(community.getId());
        Instant now = clock.instant();

        mockMvc.perform(post("/api/v1/communities/{communityId}/memberships", community.getId())
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\": \"" + user.getId() + "\", \"role\": \"COMMUNITY_MEMBER\"}"))
                .andExpect(status().isOk());

        assertMemberRecordCopiedAndAccepted(user.getId(), community.getId(), now);
    }

    @Test
    @DisplayName("MEM-001 MEM-002 a user created into a community gets a membership holding their record, accepted on creation")
    void creatingAUserInACommunity() throws Exception {
        Community community = createCommunityRepository.create(CommunityMother.random().build());
        String authHeader = loginAsDefaultPlatformAdmin();
        Instant now = clock.instant();
        String body = objectMapper.writeValueAsString(Map.of(
                "personalId", "38800001A",
                "fullName", "Jane Member",
                "number", 17,
                "address", "Calle Mayor 1",
                "email", "jane.member@example.org",
                "phoneNumber", "600000001",
                "password", "a secure password1!",
                "communityId", community.getId().toString(),
                "communityRole", "COMMUNITY_MEMBER"));

        mockMvc.perform(post("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        UUID userId = userIdOf("38800001A");
        Map<String, Object> membership = assertMemberRecordCopiedAndAccepted(userId, community.getId(), now);
        assertEquals("Jane Member", membership.get("full_name"));
        assertEquals(17, membership.get("member_number"));
        assertEquals("jane.member@example.org", membership.get("contact_email"));
        assertEquals("Calle Mayor 1", membership.get("address"));
        assertEquals("600000001", membership.get("phone_number"));
    }

    @Test
    @DisplayName("MEM-001 MEM-002 imported users get memberships holding their record, with no contact email when the email is empty or blank")
    void importingUsersFromACsvFile() throws Exception {
        Community community = createCommunityRepository.create(CommunityMother.random().build());
        String authHeader = loginAsDefaultPlatformAdmin();
        Instant now = clock.instant();
        String csv = "number,fullName,personalId,address,email,phoneNumber,role,password,communityId,communityRole\n"
                + "21,With Email,38800021A,Calle Mayor 21,with.email@example.org,600000021,partner,a secure password1!,"
                + community.getId() + ",COMMUNITY_MEMBER\n"
                + "22,Empty Email,38800022B,Calle Mayor 22,,600000022,partner,a secure password1!,"
                + community.getId() + ",COMMUNITY_MEMBER\n"
                + "23,Blank Email,38800023C,,\"   \",,partner,a secure password1!,"
                + community.getId() + ",COMMUNITY_ADMIN\n";

        mockMvc.perform(multipart("/api/v1/users/import")
                        .file(new MockMultipartFile("file", "users.csv", "text/csv", csv.getBytes()))
                        .param("communityId", community.getId().toString())
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created.length()").value(3))
                .andExpect(jsonPath("$.errors").isEmpty());

        Map<String, Object> withEmail = assertMemberRecordCopiedAndAccepted(userIdOf("38800021A"), community.getId(), now);
        assertEquals("with.email@example.org", withEmail.get("contact_email"));
        Map<String, Object> emptyEmail = assertMemberRecordCopiedAndAccepted(userIdOf("38800022B"), community.getId(), now);
        assertNull(emptyEmail.get("contact_email"));
        Map<String, Object> blankEmail = assertMemberRecordCopiedAndAccepted(userIdOf("38800023C"), community.getId(), now);
        assertNull(blankEmail.get("contact_email"));
    }

    /**
     * Asserts that the membership's record equals its user's data (no email, or an empty or blank one, recorded
     * as no contact email), that it was accepted at {@code acceptedAt}, and that no invitation is recorded.
     */
    private Map<String, Object> assertMemberRecordCopiedAndAccepted(UUID userId, UUID communityId, Instant acceptedAt) {
        membershipJpaRepository.flush();
        Map<String, Object> user = jdbcTemplate.queryForMap("SELECT * FROM users WHERE id = ?", userId);
        Map<String, Object> membership = jdbcTemplate.queryForMap(
                "SELECT * FROM community_memberships WHERE user_id = ? AND community_id = ?", userId, communityId);

        assertEquals(user.get("full_name"), membership.get("full_name"));
        assertEquals(user.get("number"), membership.get("member_number"));
        String email = (String) user.get("email");
        assertEquals(email == null || email.isBlank() ? null : email, membership.get("contact_email"));
        assertEquals(user.get("address"), membership.get("address"));
        assertEquals(user.get("phone_number"), membership.get("phone_number"));
        assertEquals(acceptedAt, ((Timestamp) membership.get("accepted_at")).toInstant());
        assertNull(membership.get("invited_by"));
        assertNull(membership.get("invited_at"));
        return membership;
    }

    private UUID userIdOf(String personalId) {
        return jdbcTemplate.queryForObject("SELECT id FROM users WHERE personal_id = ?", UUID.class, personalId);
    }
}

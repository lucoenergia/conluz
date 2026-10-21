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
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
}

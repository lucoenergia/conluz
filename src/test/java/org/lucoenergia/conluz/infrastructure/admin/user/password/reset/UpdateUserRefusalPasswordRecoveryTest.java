package org.lucoenergia.conluz.infrastructure.admin.user.password.reset;

import jakarta.mail.Message;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.create.CreateCommunityRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.CreateMembershipService;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.infrastructure.admin.user.update.UpdateUserBody;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * What the refusal of {@code PUT /api/v1/users/{userId}} to a community admin protects: the account email that
 * password recovery sends its link to (#415).
 */
class UpdateUserRefusalPasswordRecoveryTest extends BasePasswordResetTest {

    private static final String ATTACKER_EMAIL = "attacker@example.com";

    @Autowired
    private CreateCommunityRepository createCommunityRepository;
    @Autowired
    private CreateMembershipService createMembershipService;

    private final List<UUID> communities = new CopyOnWriteArrayList<>();

    // Runs before the base class deletes the users, which their memberships would otherwise keep
    @AfterEach
    void deleteTheCommunities() {
        for (UUID community : communities) {
            jdbcTemplate.update("DELETE FROM community_memberships WHERE community_id = ?", community);
            jdbcTemplate.update("DELETE FROM communities WHERE id = ?", community);
        }
    }

    @Test
    @DisplayName("ACC-008 after a community admin is refused changing a member's email, recovery still reaches the member")
    void aRefusedEmailChange_leavesRecoveryWithTheMember() throws Exception {
        Community community = createCommunityRepository.create(CommunityMother.random().build());
        communities.add(community.getId());
        User member = newUser();
        createMembershipService.create(community.getId(), member.getId(), CommunityRole.COMMUNITY_MEMBER);
        User admin = newUser();
        createMembershipService.create(community.getId(), admin.getId(), CommunityRole.COMMUNITY_ADMIN);

        UpdateUserBody takeover = new UpdateUserBody();
        takeover.setNumber(1);
        takeover.setPersonalId(member.getPersonalId());
        takeover.setFullName(member.getFullName());
        takeover.setEmail(ATTACKER_EMAIL);
        mockMvc.perform(put("/api/v1/users/{userId}", member.getId())
                        .header(HttpHeaders.AUTHORIZATION, loginUser(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(takeover)))
                .andExpect(status().isForbidden());

        requestRecovery(member.getPersonalId()).andExpect(status().isAccepted());

        MimeMessage email = awaitEmails(1)[0];
        assertThat(email.getRecipients(Message.RecipientType.TO))
                .extracting(address -> ((InternetAddress) address).getAddress())
                .containsExactly(member.getEmail());
        assertThat(GREEN_MAIL.getReceivedMessagesForDomain(ATTACKER_EMAIL)).isEmpty();
    }
}

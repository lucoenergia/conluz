package org.lucoenergia.conluz.infrastructure.admin.community.membership.invitation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Each membership a bulk send selects is invited in a transaction of its own, opened by the step itself.
 */
class FirstInvitationStepIntegrationTest extends BaseInvitationTest {

    // No interface: the step is an infrastructure component of the bulk send
    @Autowired
    private FirstInvitationStep step;

    @Test
    @DisplayName("MEM-012 called outside any transaction, the step invites an eligible membership and commits it")
    void anEligibleMembership_isInvited_inATransactionOfItsOwn() throws Exception {
        Community community = newCommunity();
        Map<String, Object> body = newRecord();
        String personalId = (String) body.get("personalId");
        UUID userId = registered(loginAsAdminOf(community), community.getId(), body);

        assertTrue(step.inviteIfEligible(community.getId(), UserId.of(userId)));

        assertThat(invitedAt(personalId, community.getId())).isEqualTo(clock.instant());
        assertThat(invitationTokenCount(personalId, community.getId())).isEqualTo(1);
        // The email is handed over once the step's transaction commits
        assertThat(linkWorks(tokenFrom(awaitNextEmails(1)[0]))).isTrue();
    }

    @Test
    @DisplayName("MEM-012 a membership the step has already invited is skipped the second time")
    void anInvitedMembership_isSkipped() throws Exception {
        Community community = newCommunity();
        Map<String, Object> body = newRecord();
        String personalId = (String) body.get("personalId");
        UUID userId = registered(loginAsAdminOf(community), community.getId(), body);
        assertTrue(step.inviteIfEligible(community.getId(), UserId.of(userId)));
        tokenFrom(awaitNextEmails(1)[0]);

        assertFalse(step.inviteIfEligible(community.getId(), UserId.of(userId)));

        assertThat(invitationTokenCount(personalId, community.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("MEM-012 a membership that does not exist is skipped without an error")
    void aMissingMembership_isSkipped() {
        assertFalse(step.inviteIfEligible(newCommunity().getId(), UserId.of(UUID.randomUUID())));
    }
}

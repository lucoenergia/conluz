package org.lucoenergia.conluz.infrastructure.admin.community.membership.invitation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.CommunityMembershipMother;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FirstInvitationStepTest {

    private static final UserId USER_ID = UserId.of(UUID.randomUUID());
    private static final UUID COMMUNITY_ID = UUID.randomUUID();
    private static final String CONTACT_EMAIL = "member@example.org";

    @Mock
    private InvitationSending sending;

    private FirstInvitationStep step() {
        return new FirstInvitationStep(sending);
    }

    @Test
    @DisplayName("MEM-012 a membership still eligible once locked is invited")
    void anEligibleMembership_isInvited_afterItIsLocked() {
        CommunityMembership eligible = new CommunityMembership.Builder().withContactEmail(CONTACT_EMAIL).build();
        when(sending.lockAndFind(COMMUNITY_ID, USER_ID)).thenReturn(Optional.of(eligible));

        assertTrue(step().inviteIfEligible(COMMUNITY_ID, USER_ID));

        InOrder order = inOrder(sending);
        order.verify(sending).lockAndFind(COMMUNITY_ID, USER_ID);
        order.verify(sending).issueAndSend(eligible);
    }

    @Test
    @DisplayName("MEM-012 a membership that no longer exists once locked is skipped, without an error")
    void aMembershipGoneOnceLocked_isSkipped() {
        when(sending.lockAndFind(COMMUNITY_ID, USER_ID)).thenReturn(Optional.empty());

        assertFalse(step().inviteIfEligible(COMMUNITY_ID, USER_ID));

        verify(sending, never()).issueAndSend(any());
    }

    @Test
    @DisplayName("MEM-012 a membership invited by another request before the lock was taken is skipped")
    void aMembershipAlreadyInvited_isSkipped() {
        assertSkipped(new CommunityMembership.Builder().withContactEmail(CONTACT_EMAIL)
                .withInvitedAt(Instant.parse("2026-01-01T00:00:00Z")).build());
    }

    @Test
    @DisplayName("MEM-012 a membership accepted before the lock was taken is skipped")
    void anAcceptedMembership_isSkipped() {
        assertSkipped(CommunityMembershipMother.active().withContactEmail(CONTACT_EMAIL).build());
    }

    @Test
    @DisplayName("MEM-012 a membership disabled before the lock was taken is skipped")
    void aDisabledMembership_isSkipped() {
        assertSkipped(new CommunityMembership.Builder().withContactEmail(CONTACT_EMAIL).withEnabled(false).build());
    }

    @Test
    @DisplayName("MEM-012 a member record whose contact email was removed before the lock was taken is skipped")
    void aRecordWithoutAContactEmail_isSkipped() {
        assertSkipped(new CommunityMembership.Builder().build());
    }

    private void assertSkipped(CommunityMembership underTheLock) {
        when(sending.lockAndFind(COMMUNITY_ID, USER_ID)).thenReturn(Optional.of(underTheLock));

        assertFalse(step().inviteIfEligible(COMMUNITY_ID, USER_ID));

        verify(sending, never()).issueAndSend(any());
    }
}

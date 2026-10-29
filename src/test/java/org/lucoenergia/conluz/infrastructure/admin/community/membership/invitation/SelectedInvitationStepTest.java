package org.lucoenergia.conluz.infrastructure.admin.community.membership.invitation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.MembershipDisabledException;
import org.lucoenergia.conluz.domain.admin.community.MembershipNotFoundException;
import org.lucoenergia.conluz.domain.admin.community.membership.invitation.InvitationOutcome;
import org.lucoenergia.conluz.domain.admin.community.membership.invitation.ResendInvitationService;
import org.lucoenergia.conluz.domain.admin.community.membership.invitation.SendInvitationService;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SelectedInvitationStepTest {

    private static final UserId USER_ID = UserId.of(UUID.randomUUID());
    private static final UUID COMMUNITY_ID = UUID.randomUUID();
    private static final UserId ACTOR = UserId.of(UUID.randomUUID());

    @Mock
    private InvitationSending sending;
    @Mock
    private SendInvitationService sendInvitationService;
    @Mock
    private ResendInvitationService resendInvitationService;

    private SelectedInvitationStep step() {
        return new SelectedInvitationStep(sending, sendInvitationService, resendInvitationService);
    }

    @Test
    @DisplayName("MEM-037 a membership never invited, once locked, is sent its first invitation by the single send")
    void aMembershipNeverInvited_isSent() {
        when(sending.lockAndRead(COMMUNITY_ID, USER_ID)).thenReturn(new CommunityMembership.Builder().build());

        assertEquals(InvitationOutcome.SENT, step().sendOrResend(COMMUNITY_ID, USER_ID, ACTOR));

        InOrder order = inOrder(sending, sendInvitationService);
        order.verify(sending).lockAndRead(COMMUNITY_ID, USER_ID);
        order.verify(sendInvitationService).send(COMMUNITY_ID, USER_ID, ACTOR);
        verifyNoInteractions(resendInvitationService);
    }

    @Test
    @DisplayName("MEM-037 a membership already invited, once locked, is resent its invitation by the single resend")
    void aMembershipInvited_isResent() {
        when(sending.lockAndRead(COMMUNITY_ID, USER_ID)).thenReturn(new CommunityMembership.Builder()
                .withInvitedAt(Instant.parse("2026-01-01T00:00:00Z")).build());

        assertEquals(InvitationOutcome.RESENT, step().sendOrResend(COMMUNITY_ID, USER_ID, ACTOR));

        InOrder order = inOrder(sending, resendInvitationService);
        order.verify(sending).lockAndRead(COMMUNITY_ID, USER_ID);
        order.verify(resendInvitationService).resend(COMMUNITY_ID, USER_ID, ACTOR);
        verifyNoInteractions(sendInvitationService);
    }

    @Test
    @DisplayName("MEM-037 a user with no membership in the community is refused before anything is sent")
    void aUserWithoutMembership_isRefused() {
        when(sending.lockAndRead(COMMUNITY_ID, USER_ID))
                .thenThrow(new MembershipNotFoundException(COMMUNITY_ID, USER_ID.getId()));

        assertThrows(MembershipNotFoundException.class, () -> step().sendOrResend(COMMUNITY_ID, USER_ID, ACTOR));

        verifyNoInteractions(sendInvitationService, resendInvitationService);
    }

    @Test
    @DisplayName("MEM-037 a refusal of the single send is thrown out of the step")
    void aRefusalOfTheSend_isThrown() {
        when(sending.lockAndRead(COMMUNITY_ID, USER_ID)).thenReturn(new CommunityMembership.Builder().build());
        when(sendInvitationService.send(COMMUNITY_ID, USER_ID, ACTOR))
                .thenThrow(new MembershipDisabledException(COMMUNITY_ID, USER_ID.getId()));

        assertThrows(MembershipDisabledException.class, () -> step().sendOrResend(COMMUNITY_ID, USER_ID, ACTOR));
    }
}

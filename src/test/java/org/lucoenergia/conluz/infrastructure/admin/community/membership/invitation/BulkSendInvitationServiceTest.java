package org.lucoenergia.conluz.infrastructure.admin.community.membership.invitation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lucoenergia.conluz.domain.admin.community.MembershipInvitationUnavailableException;
import org.lucoenergia.conluz.domain.admin.community.membership.GetMembershipsRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.invitation.BulkSendInvitationService;
import org.lucoenergia.conluz.domain.admin.community.membership.invitation.SentInvitations;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BulkSendInvitationServiceTest {

    private static final UUID COMMUNITY_ID = UUID.randomUUID();

    @Mock
    private InvitationSending sending;
    @Mock
    private GetMembershipsRepository getMembershipsRepository;
    @Mock
    private FirstInvitationStep step;

    private BulkSendInvitationService service() {
        return new BulkSendInvitationServiceImpl(sending, getMembershipsRepository, step);
    }

    @Test
    @DisplayName("MEM-012 when the server cannot send, nothing is selected or invited")
    void whenTheServerCannotSend_nothingIsSelected() {
        doThrow(new MembershipInvitationUnavailableException()).when(sending).requireAvailable();

        assertThrows(MembershipInvitationUnavailableException.class, () -> service().send(COMMUNITY_ID));

        verifyNoInteractions(getMembershipsRepository, step);
    }

    @Test
    @DisplayName("MEM-012 at most 50 candidates are invited, in the order selected, each through its own step, and "
            + "those no longer eligible are skipped")
    void theCandidatesAreInvitedInOrder_andTheIneligibleSkipped() {
        UserId first = UserId.of(UUID.randomUUID());
        UserId second = UserId.of(UUID.randomUUID());
        UserId third = UserId.of(UUID.randomUUID());
        when(getMembershipsRepository.findFirstInvitationCandidates(COMMUNITY_ID, 50))
                .thenReturn(List.of(first, second, third));
        when(step.inviteIfEligible(COMMUNITY_ID, first)).thenReturn(true);
        when(step.inviteIfEligible(COMMUNITY_ID, second)).thenReturn(false);
        when(step.inviteIfEligible(COMMUNITY_ID, third)).thenReturn(true);
        when(getMembershipsRepository.countFirstInvitationCandidates(COMMUNITY_ID)).thenReturn(7L);

        assertEquals(new SentInvitations(2, 7), service().send(COMMUNITY_ID));

        InOrder order = inOrder(sending, getMembershipsRepository, step);
        order.verify(sending).requireAvailable();
        order.verify(getMembershipsRepository).findFirstInvitationCandidates(COMMUNITY_ID, 50);
        order.verify(step).inviteIfEligible(COMMUNITY_ID, first);
        order.verify(step).inviteIfEligible(COMMUNITY_ID, second);
        order.verify(step).inviteIfEligible(COMMUNITY_ID, third);
        order.verify(getMembershipsRepository).countFirstInvitationCandidates(COMMUNITY_ID);
    }
}

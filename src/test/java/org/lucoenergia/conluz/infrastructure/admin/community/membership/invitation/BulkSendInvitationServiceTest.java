package org.lucoenergia.conluz.infrastructure.admin.community.membership.invitation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lucoenergia.conluz.domain.admin.community.MembershipAlreadyExistsException;
import org.lucoenergia.conluz.domain.admin.community.MembershipAlreadyInvitedException;
import org.lucoenergia.conluz.domain.admin.community.MembershipDisabledException;
import org.lucoenergia.conluz.domain.admin.community.MembershipInvitationResendLimitException;
import org.lucoenergia.conluz.domain.admin.community.MembershipInvitationUnavailableException;
import org.lucoenergia.conluz.domain.admin.community.MembershipNotFoundException;
import org.lucoenergia.conluz.domain.admin.community.MembershipWithoutContactEmailException;
import org.lucoenergia.conluz.domain.admin.community.membership.GetMembershipsRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.invitation.BulkSendInvitationService;
import org.lucoenergia.conluz.domain.admin.community.membership.invitation.InvitationOutcome;
import org.lucoenergia.conluz.domain.admin.community.membership.invitation.InvitationRefusal;
import org.lucoenergia.conluz.domain.admin.community.membership.invitation.InvitationResult;
import org.lucoenergia.conluz.domain.admin.community.membership.invitation.InvitationSelectionProblem;
import org.lucoenergia.conluz.domain.admin.community.membership.invitation.InvitationSelectionRefusedException;
import org.lucoenergia.conluz.domain.admin.community.membership.invitation.SentInvitations;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BulkSendInvitationServiceTest {

    private static final UUID COMMUNITY_ID = UUID.randomUUID();
    private static final UserId ACTOR = UserId.of(UUID.randomUUID());

    @Mock
    private InvitationSending sending;
    @Mock
    private GetMembershipsRepository getMembershipsRepository;
    @Mock
    private FirstInvitationStep step;
    @Mock
    private SelectedInvitationStep selectedStep;

    private BulkSendInvitationService service() {
        return new BulkSendInvitationServiceImpl(sending, getMembershipsRepository, step, selectedStep);
    }

    @Test
    @DisplayName("MEM-012 when the server cannot send, nothing is selected or invited")
    void whenTheServerCannotSend_nothingIsSelected() {
        doThrow(new MembershipInvitationUnavailableException()).when(sending).requireAvailable();

        assertThrows(MembershipInvitationUnavailableException.class, () -> service().send(COMMUNITY_ID, ACTOR));

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
        when(step.inviteIfEligible(COMMUNITY_ID, first, ACTOR)).thenReturn(true);
        when(step.inviteIfEligible(COMMUNITY_ID, second, ACTOR)).thenReturn(false);
        when(step.inviteIfEligible(COMMUNITY_ID, third, ACTOR)).thenReturn(true);
        when(getMembershipsRepository.countFirstInvitationCandidates(COMMUNITY_ID)).thenReturn(7L);

        assertEquals(new SentInvitations(2, 7, List.of()), service().send(COMMUNITY_ID, ACTOR));

        InOrder order = inOrder(sending, getMembershipsRepository, step);
        order.verify(sending).requireAvailable();
        order.verify(getMembershipsRepository).findFirstInvitationCandidates(COMMUNITY_ID, 50);
        order.verify(step).inviteIfEligible(COMMUNITY_ID, first, ACTOR);
        order.verify(step).inviteIfEligible(COMMUNITY_ID, second, ACTOR);
        order.verify(step).inviteIfEligible(COMMUNITY_ID, third, ACTOR);
        order.verify(getMembershipsRepository).countFirstInvitationCandidates(COMMUNITY_ID);
    }

    @Test
    @DisplayName("MEM-037 an empty selection is refused before anything else")
    void anEmptySelection_isRefused() {
        assertSelectionRefused(List.of(), InvitationSelectionProblem.EMPTY);
        assertSelectionRefused(null, InvitationSelectionProblem.EMPTY);
    }

    @Test
    @DisplayName("MEM-037 a selection of more than 50 user ids is refused before anything else; 50 are accepted")
    void aSelectionOfMoreThan50_isRefused() {
        assertSelectionRefused(randomIds(51), InvitationSelectionProblem.TOO_LARGE);

        List<UUID> fifty = randomIds(50);
        when(selectedStep.sendOrResend(eq(COMMUNITY_ID), any(), eq(ACTOR)))
                .thenReturn(InvitationOutcome.SENT);
        assertEquals(50, service().sendTo(COMMUNITY_ID, fifty, ACTOR).results().size());
    }

    @Test
    @DisplayName("MEM-037 a selection naming a user id twice is refused before anything else")
    void aDuplicatedSelection_isRefused() {
        UUID id = UUID.randomUUID();
        assertSelectionRefused(List.of(id, UUID.randomUUID(), id), InvitationSelectionProblem.DUPLICATED);
    }

    @Test
    @DisplayName("MEM-037 when the server cannot send, it is checked once and nothing is read or invited")
    void whenTheServerCannotSend_noSelectedMembershipIsRead() {
        doThrow(new MembershipInvitationUnavailableException()).when(sending).requireAvailable();

        assertThrows(MembershipInvitationUnavailableException.class,
                () -> service().sendTo(COMMUNITY_ID, randomIds(3), ACTOR));

        verify(sending, times(1)).requireAvailable();
        verifyNoInteractions(getMembershipsRepository, step, selectedStep);
    }

    @Test
    @DisplayName("MEM-037 the selection is processed in ascending user id, as PostgreSQL orders a uuid, and answered "
            + "in the order requested")
    void theSelection_isProcessedInUserIdOrder_andAnsweredInRequestOrder() {
        // The signed comparison of UUID.compareTo puts 8000… before 7fff…; PostgreSQL compares bytes
        UserId low = UserId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        UserId middle = UserId.of(UUID.fromString("7fffffff-0000-0000-0000-000000000000"));
        UserId high = UserId.of(UUID.fromString("80000000-0000-0000-0000-000000000000"));
        when(selectedStep.sendOrResend(COMMUNITY_ID, low, ACTOR)).thenReturn(InvitationOutcome.SENT);
        when(selectedStep.sendOrResend(COMMUNITY_ID, middle, ACTOR)).thenReturn(InvitationOutcome.RESENT);
        when(selectedStep.sendOrResend(COMMUNITY_ID, high, ACTOR)).thenReturn(InvitationOutcome.SENT);
        when(getMembershipsRepository.countFirstInvitationCandidates(COMMUNITY_ID)).thenReturn(4L);

        SentInvitations sent = service().sendTo(COMMUNITY_ID,
                List.of(high.getId(), low.getId(), middle.getId()), ACTOR);

        assertEquals(new SentInvitations(2, 4, List.of(InvitationResult.sent(high), InvitationResult.sent(low),
                InvitationResult.resent(middle))), sent);
        InOrder order = inOrder(sending, selectedStep, getMembershipsRepository);
        order.verify(sending).requireAvailable();
        order.verify(selectedStep).sendOrResend(COMMUNITY_ID, low, ACTOR);
        order.verify(selectedStep).sendOrResend(COMMUNITY_ID, middle, ACTOR);
        order.verify(selectedStep).sendOrResend(COMMUNITY_ID, high, ACTOR);
        order.verify(getMembershipsRepository).countFirstInvitationCandidates(COMMUNITY_ID);
        verifyNoInteractions(step);
    }

    @Test
    @DisplayName("MEM-037 each refusal of the send or the resend answers its result, and the others are processed")
    void eachRefusal_answersItsResult_andTheOthersAreProcessed() {
        List<UserId> ids = IntStream.range(0, 6).mapToObj(i -> UserId.of(UUID.randomUUID())).toList();
        when(selectedStep.sendOrResend(COMMUNITY_ID, ids.get(0), ACTOR))
                .thenThrow(new MembershipNotFoundException(COMMUNITY_ID, ids.get(0).getId()));
        when(selectedStep.sendOrResend(COMMUNITY_ID, ids.get(1), ACTOR))
                .thenThrow(new MembershipAlreadyExistsException(COMMUNITY_ID, ids.get(1).getId()));
        when(selectedStep.sendOrResend(COMMUNITY_ID, ids.get(2), ACTOR))
                .thenThrow(new MembershipDisabledException(COMMUNITY_ID, ids.get(2).getId()));
        when(selectedStep.sendOrResend(COMMUNITY_ID, ids.get(3), ACTOR))
                .thenThrow(new MembershipWithoutContactEmailException(COMMUNITY_ID, ids.get(3).getId()));
        when(selectedStep.sendOrResend(COMMUNITY_ID, ids.get(4), ACTOR))
                .thenThrow(new MembershipInvitationResendLimitException(COMMUNITY_ID, ids.get(4).getId(), 3600));
        when(selectedStep.sendOrResend(COMMUNITY_ID, ids.get(5), ACTOR)).thenReturn(InvitationOutcome.RESENT);

        SentInvitations sent = service().sendTo(COMMUNITY_ID, ids.stream().map(UserId::getId).toList(), ACTOR);

        assertThat(sent.results()).containsExactly(
                InvitationResult.refused(ids.get(0), InvitationRefusal.NOT_A_MEMBER),
                InvitationResult.refused(ids.get(1), InvitationRefusal.ALREADY_ACCEPTED),
                InvitationResult.refused(ids.get(2), InvitationRefusal.DISABLED),
                InvitationResult.refused(ids.get(3), InvitationRefusal.WITHOUT_CONTACT_EMAIL),
                InvitationResult.resendLimitReached(ids.get(4), 3600),
                InvitationResult.resent(ids.get(5)));
        assertEquals(0, sent.sent());
    }

    @Test
    @DisplayName("MEM-037 any other failure stops the request, keeping what was already done")
    void anyOtherFailure_propagates() {
        UUID id = UUID.randomUUID();
        when(selectedStep.sendOrResend(COMMUNITY_ID, UserId.of(id), ACTOR))
                .thenThrow(new MembershipAlreadyInvitedException(COMMUNITY_ID, id));

        assertThrows(MembershipAlreadyInvitedException.class,
                () -> service().sendTo(COMMUNITY_ID, List.of(id), ACTOR));
        verifyNoInteractions(getMembershipsRepository);
    }

    private void assertSelectionRefused(List<UUID> userIds, InvitationSelectionProblem problem) {
        InvitationSelectionRefusedException e = assertThrows(InvitationSelectionRefusedException.class,
                () -> service().sendTo(COMMUNITY_ID, userIds, ACTOR));
        assertEquals(problem, e.getProblem());
        verifyNoInteractions(sending, getMembershipsRepository, step, selectedStep);
    }

    private static List<UUID> randomIds(int count) {
        return IntStream.range(0, count).mapToObj(i -> UUID.randomUUID()).toList();
    }
}

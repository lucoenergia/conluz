package org.lucoenergia.conluz.infrastructure.admin.community.membership.invitation;

import org.lucoenergia.conluz.domain.admin.community.MembershipAlreadyExistsException;
import org.lucoenergia.conluz.domain.admin.community.MembershipDisabledException;
import org.lucoenergia.conluz.domain.admin.community.MembershipInvitationResendLimitException;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Not transactional itself: each membership is invited in a transaction of its own by {@link FirstInvitationStep}, or
 * by {@link SelectedInvitationStep} for a selection, so the locks are held for one membership at a time, each email is
 * handed over as soon as its invitation commits, and a failure keeps the invitations already made.
 */
@Service
public class BulkSendInvitationServiceImpl implements BulkSendInvitationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(BulkSendInvitationServiceImpl.class);

    /**
     * Ascending user id as PostgreSQL orders a uuid, byte by byte, which is how {@link #send} selects its candidates.
     * {@link UUID#compareTo} compares signed numbers, which is not the same order.
     */
    private static final Comparator<UUID> USER_ID_ORDER = Comparator.comparing(UUID::toString);

    private final InvitationSending sending;
    private final GetMembershipsRepository getMembershipsRepository;
    private final FirstInvitationStep step;
    private final SelectedInvitationStep selectedStep;

    public BulkSendInvitationServiceImpl(InvitationSending sending, GetMembershipsRepository getMembershipsRepository,
                                         FirstInvitationStep step, SelectedInvitationStep selectedStep) {
        this.sending = sending;
        this.getMembershipsRepository = getMembershipsRepository;
        this.step = step;
        this.selectedStep = selectedStep;
    }

    @Override
    public SentInvitations send(UUID communityId, UserId sentBy) {
        sending.requireAvailable();

        // In ascending user id, and invited in that order: two concurrent calls lock in the same order
        List<UserId> candidates = getMembershipsRepository.findFirstInvitationCandidates(communityId, BATCH_SIZE);
        int sent = 0;
        for (UserId candidate : candidates) {
            if (step.inviteIfEligible(communityId, candidate, sentBy)) {
                sent++;
            }
        }
        long remaining = getMembershipsRepository.countFirstInvitationCandidates(communityId);

        LOGGER.info("Member invitations sent: communityId={}, sent={}, skipped={}, remaining={}", communityId, sent,
                candidates.size() - sent, remaining);
        return new SentInvitations(sent, remaining, List.of());
    }

    @Override
    public SentInvitations sendTo(UUID communityId, List<UUID> userIds, UserId sentBy) {
        requireValid(userIds);
        sending.requireAvailable();

        // In ascending user id, as send does, and answered in the order requested
        Map<UUID, InvitationResult> results = new HashMap<>();
        userIds.stream().sorted(USER_ID_ORDER)
                .forEach(userId -> results.put(userId, sendOrResend(communityId, UserId.of(userId), sentBy)));
        List<InvitationResult> inRequestOrder = userIds.stream().map(results::get).toList();
        long remaining = getMembershipsRepository.countFirstInvitationCandidates(communityId);

        int sent = count(inRequestOrder, InvitationOutcome.SENT);
        int resent = count(inRequestOrder, InvitationOutcome.RESENT);
        LOGGER.info("Selected member invitations sent: communityId={}, sent={}, resent={}, refused={}, remaining={}",
                communityId, sent, resent, inRequestOrder.size() - sent - resent, remaining);
        return new SentInvitations(sent, remaining, inRequestOrder);
    }

    private static void requireValid(List<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            throw new InvitationSelectionRefusedException(InvitationSelectionProblem.EMPTY);
        }
        if (userIds.size() > BATCH_SIZE) {
            throw new InvitationSelectionRefusedException(InvitationSelectionProblem.TOO_LARGE);
        }
        if (new HashSet<>(userIds).size() < userIds.size()) {
            throw new InvitationSelectionRefusedException(InvitationSelectionProblem.DUPLICATED);
        }
    }

    /**
     * The refusals a membership chosen between the send and the resend can meet. A membership already invited, or
     * not invited, cannot be refused as such: the step chooses under the lock the send or the resend runs under.
     */
    private InvitationResult sendOrResend(UUID communityId, UserId userId, UserId sentBy) {
        try {
            return selectedStep.sendOrResend(communityId, userId, sentBy) == InvitationOutcome.SENT
                    ? InvitationResult.sent(userId)
                    : InvitationResult.resent(userId);
        } catch (MembershipNotFoundException e) {
            return InvitationResult.refused(userId, InvitationRefusal.NOT_A_MEMBER);
        } catch (MembershipAlreadyExistsException e) {
            return InvitationResult.refused(userId, InvitationRefusal.ALREADY_ACCEPTED);
        } catch (MembershipDisabledException e) {
            return InvitationResult.refused(userId, InvitationRefusal.DISABLED);
        } catch (MembershipWithoutContactEmailException e) {
            return InvitationResult.refused(userId, InvitationRefusal.WITHOUT_CONTACT_EMAIL);
        } catch (MembershipInvitationResendLimitException e) {
            return InvitationResult.resendLimitReached(userId, e.getRetryAfterSeconds());
        }
    }

    private static int count(List<InvitationResult> results, InvitationOutcome outcome) {
        return (int) results.stream().filter(result -> result.outcome() == outcome).count();
    }
}

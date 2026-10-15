package org.lucoenergia.conluz.infrastructure.admin.community.membership.invitation;

import org.lucoenergia.conluz.domain.admin.community.membership.GetMembershipsRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.invitation.BulkSendInvitationService;
import org.lucoenergia.conluz.domain.admin.community.membership.invitation.SentInvitations;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Not transactional itself: each membership is invited in a transaction of its own by {@link FirstInvitationStep}, so
 * the locks are held for one membership at a time, each email is handed over as soon as its invitation commits, and a
 * failure keeps the invitations already made.
 */
@Service
public class BulkSendInvitationServiceImpl implements BulkSendInvitationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(BulkSendInvitationServiceImpl.class);

    private final InvitationSending sending;
    private final GetMembershipsRepository getMembershipsRepository;
    private final FirstInvitationStep step;

    public BulkSendInvitationServiceImpl(InvitationSending sending, GetMembershipsRepository getMembershipsRepository,
                                         FirstInvitationStep step) {
        this.sending = sending;
        this.getMembershipsRepository = getMembershipsRepository;
        this.step = step;
    }

    @Override
    public SentInvitations send(UUID communityId) {
        sending.requireAvailable();

        // In ascending user id, and invited in that order: two concurrent calls lock in the same order
        List<UserId> candidates = getMembershipsRepository.findFirstInvitationCandidates(communityId, BATCH_SIZE);
        int sent = 0;
        for (UserId candidate : candidates) {
            if (step.inviteIfEligible(communityId, candidate)) {
                sent++;
            }
        }
        long remaining = getMembershipsRepository.countFirstInvitationCandidates(communityId);

        LOGGER.info("Member invitations sent: communityId={}, sent={}, skipped={}, remaining={}", communityId, sent,
                candidates.size() - sent, remaining);
        return new SentInvitations(sent, remaining);
    }
}

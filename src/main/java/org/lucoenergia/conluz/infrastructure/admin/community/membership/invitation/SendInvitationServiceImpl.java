package org.lucoenergia.conluz.infrastructure.admin.community.membership.invitation;

import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.MembershipAlreadyExistsException;
import org.lucoenergia.conluz.domain.admin.community.MembershipAlreadyInvitedException;
import org.lucoenergia.conluz.domain.admin.community.MembershipWithoutContactEmailException;
import org.lucoenergia.conluz.domain.admin.community.membership.MembershipWithInvitation;
import org.lucoenergia.conluz.domain.admin.community.membership.invitation.SendInvitationService;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
public class SendInvitationServiceImpl implements SendInvitationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SendInvitationServiceImpl.class);

    private final InvitationSending sending;

    public SendInvitationServiceImpl(InvitationSending sending) {
        this.sending = sending;
    }

    @Override
    public MembershipWithInvitation send(UUID communityId, UserId userId) {
        sending.requireAvailable();

        // Read under the lock, so two invitations of the same membership run one after the other
        CommunityMembership membership = sending.lockAndRead(communityId, userId);
        if (!membership.isPending()) {
            throw new MembershipAlreadyExistsException(communityId, userId.getId());
        }
        if (membership.getInvitedAt() != null) {
            throw new MembershipAlreadyInvitedException(communityId, userId.getId());
        }
        if (membership.getContactEmail() == null) {
            throw new MembershipWithoutContactEmailException(communityId, userId.getId());
        }
        CommunityMembership invited = sending.issueAndSend(membership);

        LOGGER.info("Member invited: userId={}, communityId={}", userId.getId(), communityId);
        return sending.withInvitation(invited);
    }
}

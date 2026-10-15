package org.lucoenergia.conluz.infrastructure.admin.community.membership.invitation;

import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.membership.MembershipWithInvitation;
import org.lucoenergia.conluz.domain.admin.community.membership.invitation.ResendInvitationService;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
public class ResendInvitationServiceImpl implements ResendInvitationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ResendInvitationServiceImpl.class);

    private final InvitationResending resending;
    private final InvitationSending sending;

    public ResendInvitationServiceImpl(InvitationResending resending, InvitationSending sending) {
        this.resending = resending;
        this.sending = sending;
    }

    @Override
    public MembershipWithInvitation resend(UUID communityId, UserId userId) {
        // Every membership of the community, whatever its role
        CommunityMembership invited = resending.resend(communityId, userId, membership -> true);

        LOGGER.info("Member invitation resent: userId={}, communityId={}", userId.getId(), communityId);
        return sending.withInvitation(invited);
    }
}

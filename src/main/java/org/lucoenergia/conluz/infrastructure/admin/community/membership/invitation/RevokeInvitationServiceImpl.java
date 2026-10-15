package org.lucoenergia.conluz.infrastructure.admin.community.membership.invitation;

import org.lucoenergia.conluz.domain.admin.community.membership.MembershipWithInvitation;
import org.lucoenergia.conluz.domain.admin.community.membership.invitation.RevokeInvitationService;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
public class RevokeInvitationServiceImpl implements RevokeInvitationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RevokeInvitationServiceImpl.class);

    private final InvitationRevoking revoking;
    private final InvitationSending sending;

    public RevokeInvitationServiceImpl(InvitationRevoking revoking, InvitationSending sending) {
        this.revoking = revoking;
        this.sending = sending;
    }

    @Override
    public MembershipWithInvitation revoke(UUID communityId, UserId userId) {
        // Every membership of the community, whatever its role
        InvitationRevoking.RevokedInvitation revoked = revoking.revoke(communityId, userId, membership -> true);

        LOGGER.info("Member invitation revoked: userId={}, communityId={}, revoked={}", userId.getId(), communityId,
                revoked.revoked());
        return sending.withInvitation(revoked.membership());
    }
}

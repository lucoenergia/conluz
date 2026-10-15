package org.lucoenergia.conluz.infrastructure.admin.community.membership.invitation;

import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.MembershipAlreadyExistsException;
import org.lucoenergia.conluz.domain.admin.community.membership.MembershipWithInvitation;
import org.lucoenergia.conluz.domain.admin.community.membership.invitation.RevokeInvitationService;
import org.lucoenergia.conluz.domain.admin.user.token.OneTimeTokenPurpose;
import org.lucoenergia.conluz.domain.admin.user.token.RevokeOneTimeTokenService;
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

    private final InvitationSending sending;
    private final RevokeOneTimeTokenService revokeOneTimeTokenService;

    public RevokeInvitationServiceImpl(InvitationSending sending,
                                       RevokeOneTimeTokenService revokeOneTimeTokenService) {
        this.sending = sending;
        this.revokeOneTimeTokenService = revokeOneTimeTokenService;
    }

    @Override
    public MembershipWithInvitation revoke(UUID communityId, UserId userId) {
        CommunityMembership membership = sending.lockAndRead(communityId, userId);
        if (!membership.isPending()) {
            throw new MembershipAlreadyExistsException(communityId, userId.getId());
        }
        // The invitation instant is kept: it records the last send, which counts toward the resend limit
        boolean revoked = revokeOneTimeTokenService.revokeForMembership(userId, communityId,
                OneTimeTokenPurpose.INVITATION);

        LOGGER.info("Member invitation revoked: userId={}, communityId={}, revoked={}", userId.getId(), communityId,
                revoked);
        return sending.withInvitation(membership);
    }
}

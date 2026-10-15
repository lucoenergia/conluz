package org.lucoenergia.conluz.infrastructure.admin.community.membership.invitation;

import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.MembershipAlreadyExistsException;
import org.lucoenergia.conluz.domain.admin.community.MembershipNotFoundException;
import org.lucoenergia.conluz.domain.admin.user.token.OneTimeTokenPurpose;
import org.lucoenergia.conluz.domain.admin.user.token.RevokeOneTimeTokenService;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.function.Predicate;

/**
 * The rules of revoking an invitation, shared by the community admin revoking a member's invitation and the platform
 * admin revoking a community admin's: the link stops working, and the membership stays pending with its invitation
 * instant. It works whatever the membership's enabled state, and whether or not invitations can be sent.
 */
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class InvitationRevoking {

    /**
     * @param membership the membership as read under the lock
     * @param revoked    whether a link that could still be used was revoked
     */
    public record RevokedInvitation(CommunityMembership membership, boolean revoked) {
    }

    private final InvitationSending sending;
    private final RevokeOneTimeTokenService revokeOneTimeTokenService;

    public InvitationRevoking(InvitationSending sending, RevokeOneTimeTokenService revokeOneTimeTokenService) {
        this.sending = sending;
        this.revokeOneTimeTokenService = revokeOneTimeTokenService;
    }

    /**
     * @param inScope whether the membership read under the lock is one the caller's endpoint acts on; one that is not
     *                is answered as a missing one
     */
    public RevokedInvitation revoke(UUID communityId, UserId userId, Predicate<CommunityMembership> inScope) {
        CommunityMembership membership = sending.lockAndRead(communityId, userId);
        if (!inScope.test(membership)) {
            throw new MembershipNotFoundException(communityId, userId.getId());
        }
        if (!membership.isPending()) {
            throw new MembershipAlreadyExistsException(communityId, userId.getId());
        }
        // The invitation instant is kept: it records the last send, which counts toward the resend limit
        boolean revoked = revokeOneTimeTokenService.revokeForMembership(userId, communityId,
                OneTimeTokenPurpose.INVITATION);
        return new RevokedInvitation(membership, revoked);
    }
}

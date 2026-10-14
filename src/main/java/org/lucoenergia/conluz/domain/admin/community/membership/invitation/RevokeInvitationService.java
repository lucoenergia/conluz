package org.lucoenergia.conluz.domain.admin.community.membership.invitation;

import org.lucoenergia.conluz.domain.admin.community.membership.MembershipWithInvitation;
import org.lucoenergia.conluz.domain.shared.UserId;

import java.util.UUID;

/**
 * Withdraws the invitation link of a pending membership (ADR-0003 §5): revokes its active {@code INVITATION} token.
 * The membership stays pending, keeps its invitation instant, and can be resent. Sends nothing, so it does not depend
 * on whether the server can send.
 */
public interface RevokeInvitationService {

    /**
     * Changes nothing when the membership has no active invitation token.
     *
     * @return the membership, which then has no invitation link
     * @throws org.lucoenergia.conluz.domain.admin.community.MembershipNotFoundException if the user has no
     *         membership in the community
     * @throws org.lucoenergia.conluz.domain.admin.community.MembershipAlreadyExistsException if the membership has
     *         been accepted
     */
    MembershipWithInvitation revoke(UUID communityId, UserId userId);
}

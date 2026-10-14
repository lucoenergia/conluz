package org.lucoenergia.conluz.domain.admin.community.membership.invitation;

import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.shared.UserId;

import java.util.UUID;

/**
 * Invites the holder of a pending membership to accept it (ADR-0003 §5 and §6): issues an {@code INVITATION} token
 * for the membership, records the invitation instant and emails the link to the member record's contact email once
 * the transaction commits. This is the initial send only; resending is #393.
 */
public interface SendInvitationService {

    /**
     * @return the membership, with its invitation instant if the invitation was sent. If the public web URL is not
     *         configured, nothing is issued or recorded and the membership is returned unchanged
     * @throws org.lucoenergia.conluz.domain.admin.community.MembershipNotFoundException if the user has no
     *         membership in the community
     * @throws org.lucoenergia.conluz.domain.admin.community.MembershipAlreadyExistsException if the membership has
     *         been accepted: the person is already a member, so there is nothing to invite them to
     * @throws org.lucoenergia.conluz.domain.admin.community.MembershipAlreadyInvitedException if the membership has
     *         already been sent its invitation
     * @throws org.lucoenergia.conluz.domain.admin.community.MembershipWithoutContactEmailException if the member
     *         record has no contact email
     */
    CommunityMembership send(UUID communityId, UserId userId);
}

package org.lucoenergia.conluz.domain.admin.community.membership.invitation;

import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;

import java.util.Optional;

/**
 * Invites the holder of a pending membership to accept it (ADR-0003 §6): issues an {@code INVITATION} token for the
 * membership, records the invitation instant and emails the link to the member record's contact email once the
 * transaction commits.
 */
public interface SendInvitationService {

    /**
     * Must run inside the transaction that holds the lock on the membership's user row, so the lock order is user,
     * then membership, then tokens.
     *
     * @param membership a membership whose member record has a contact email
     * @return the membership with its invitation instant, or empty if the public web URL is not configured, in
     *         which case nothing is issued or recorded
     * @throws org.lucoenergia.conluz.domain.admin.community.MembershipAlreadyExistsException if the membership has
     *         been accepted: the person is already a member, so there is nothing to invite them to
     */
    Optional<CommunityMembership> send(CommunityMembership membership);
}

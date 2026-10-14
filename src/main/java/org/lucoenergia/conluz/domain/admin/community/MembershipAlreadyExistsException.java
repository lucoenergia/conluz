package org.lucoenergia.conluz.domain.admin.community;

import java.util.UUID;

/**
 * The user is already a member of this community: the membership is there when one is being created, or it has
 * been accepted when an invitation to it is being sent.
 *
 * <p>A state conflict rather than a malformed request: the body is well-formed and the caller is
 * authorized, but {@code (user_id, community_id)} is unique and the row is already there, or the
 * membership is no longer pending. Mapped to
 * {@code 409}, which is what
 * {@code docs/security/authorization-policy.md} prescribes for exactly this shape of failure.
 */
public class MembershipAlreadyExistsException extends RuntimeException {

    private final UUID communityId;
    private final UUID userId;

    public MembershipAlreadyExistsException(UUID communityId, UUID userId) {
        this.communityId = communityId;
        this.userId = userId;
    }

    public UUID getCommunityId() {
        return communityId;
    }

    public UUID getUserId() {
        return userId;
    }
}

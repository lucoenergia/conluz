package org.lucoenergia.conluz.domain.admin.community;

import java.util.UUID;

/**
 * No membership exists for this user in this community.
 *
 * <p>Distinct from {@link CommunityNotFoundException} and
 * {@link org.lucoenergia.conluz.domain.admin.user.UserNotFoundException}: both the community and
 * the user can exist perfectly well while the row joining them does not. Before this existed, that
 * case was the one gap in the membership endpoints' error mapping, surfacing as a 500.
 */
public class MembershipNotFoundException extends RuntimeException {

    private final UUID communityId;
    private final UUID userId;

    public MembershipNotFoundException(UUID communityId, UUID userId) {
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

package org.lucoenergia.conluz.domain.admin.community.access;

import java.util.Set;
import java.util.UUID;

public interface CommunityAccessGuard extends
        SupplyAccessGuard,
        MembershipAccessGuard,
        UserAccessGuard,
        PlantAccessGuard,
        SharingAgreementAccessGuard {

    boolean canReadCommunity(UUID communityId);

    boolean canManageCommunity(UUID communityId);

    Set<UUID> visibleCommunityIds();

    /**
     * Community ids the current user administers. Returns the set of community ids where the user holds an enabled
     * {@code COMMUNITY_ADMIN} membership (possibly empty).
     */
    Set<UUID> adminCommunityIds();

    /**
     * @return true when the authenticated user's id equals {@code userId}.
     */
    boolean isCurrentUser(UUID userId);
}

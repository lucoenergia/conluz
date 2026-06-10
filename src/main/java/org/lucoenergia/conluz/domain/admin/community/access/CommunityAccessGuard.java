package org.lucoenergia.conluz.domain.admin.community.access;

import java.util.Set;
import java.util.UUID;

public interface CommunityAccessGuard {

    boolean canReadSupply(UUID supplyId);

    boolean canEditSupply(UUID supply);

    boolean canReadCommunity(UUID communityId);

    boolean canManageCommunity(UUID communityId);

    boolean canManageMemberships(UUID communityId);

    boolean canReadUser(UUID userId);

    boolean canEditUser(UUID userId);

    Set<UUID> visibleCommunityIds();

    /**
     * Community ids the current user administers. Returns {@code null} for platform
     * admins (meaning "all communities"), otherwise the set of community ids where the
     * user holds an enabled {@code COMMUNITY_ADMIN} membership (possibly empty).
     */
    Set<UUID> adminCommunityIds();

    /**
     * @return true when the authenticated user's id equals {@code userId}.
     */
    boolean isCurrentUser(UUID userId);

    boolean canCreateUserIn(UUID communityId);

    boolean canListUsers();

    boolean canManagePlant(UUID plantId);

    boolean canCreatePlant(String supplyCode);

    boolean canManageSharingAgreement(UUID agreementId);
}

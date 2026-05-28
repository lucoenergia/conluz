package org.lucoenergia.conluz.domain.admin.community.access;

import org.lucoenergia.conluz.domain.admin.supply.Supply;

import java.util.Set;
import java.util.UUID;

public interface CommunityAccessGuard {

    boolean canReadSupply(Supply supply);

    boolean canReadSupply(UUID supplyId);

    boolean canEditSupply(Supply supply);

    boolean canEditSupply(UUID supplyId);

    boolean canReadCommunity(UUID communityId);

    boolean canManageCommunity(UUID communityId);

    boolean canManagePlatform();

    boolean canManageMemberships(UUID communityId);

    boolean canReadUser(UUID userId);

    boolean canEditUser(UUID userId);

    Set<UUID> visibleCommunityIds();

    boolean canCreateUserIn(UUID communityId);
}

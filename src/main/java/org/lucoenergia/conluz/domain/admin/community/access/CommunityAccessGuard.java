package org.lucoenergia.conluz.domain.admin.community.access;

import org.lucoenergia.conluz.domain.admin.supply.Supply;

import java.util.UUID;

public interface CommunityAccessGuard {

    boolean canReadSupply(Supply supply);

    boolean canEditSupply(Supply supply);

    boolean canReadCommunity(UUID communityId);

    boolean canManageCommunity(UUID communityId);

    boolean canManagePlatform();
}

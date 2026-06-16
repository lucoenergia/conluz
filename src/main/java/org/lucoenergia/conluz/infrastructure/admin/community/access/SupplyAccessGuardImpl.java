package org.lucoenergia.conluz.infrastructure.admin.community.access;

import org.lucoenergia.conluz.domain.admin.community.access.SupplyAccessGuard;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.shared.SupplyId;

import java.util.UUID;

class SupplyAccessGuardImpl implements SupplyAccessGuard {

    private final CommunityAccessGuardHelper helper;
    private final GetSupplyRepository getSupplyRepository;

    public SupplyAccessGuardImpl(CommunityAccessGuardHelper helper, GetSupplyRepository getSupplyRepository) {
        this.helper = helper;
        this.getSupplyRepository = getSupplyRepository;
    }

    @Override
    public boolean canReadSupply(UUID supplyId) {
        User user = helper.getCurrentUser().orElse(null);
        if (user == null || supplyId == null) {
            return false;
        }
        // A caller who cannot see the supply (it does not exist, or they neither administer its
        // community nor own it) gets a 404, never a 403, to avoid leaking the supply's existence.
        if (!canSeeSupply(user, supplyId)) {
            throw new SupplyNotFoundException(SupplyId.of(supplyId));
        }
        return true;
    }

    @Override
    public boolean canEditSupply(UUID supplyId) {
        // Reading and editing a supply require the same access (community admin or owner), so a
        // caller who cannot edit it also cannot see it: deny with a 404 to avoid leaking existence.
        return canReadSupply(supplyId);
    }

    private boolean canSeeSupply(User user, UUID supplyId) {
        Supply supply = getSupplyRepository.findById(SupplyId.of(supplyId)).orElse(null);
        if (supply == null) {
            return false;
        }
        UUID communityId = supply.getCommunity() != null ? supply.getCommunity().getId() : null;
        return helper.hasCommunityAdminRoleIn(user, communityId) || isOwner(supply, user);
    }

    private boolean isOwner(Supply supply, User user) {
        return supply.getUser() != null && supply.getUser().getId() != null
                && supply.getUser().getId().equals(user.getId());
    }
}

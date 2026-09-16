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
        requireVisible(findSupply(supplyId), user, supplyId);
        return true;
    }

    @Override
    public boolean canEditSupply(UUID supplyId) {
        User user = helper.getCurrentUser().orElse(null);
        if (user == null || supplyId == null) {
            return false;
        }
        Supply supply = requireVisible(findSupply(supplyId), user, supplyId);
        return isCommunityAdminOf(supply, user);
    }

    /**
     * Resolves the supply once and returns it, or throws: a caller who cannot see the supply (it does
     * not exist, or they neither administer its community nor own it) gets a 404, never a 403, to
     * avoid leaking the supply's existence.
     */
    private Supply requireVisible(Supply supply, User user, UUID supplyId) {
        if (supply == null || !(isCommunityAdminOf(supply, user) || isOwner(supply, user))) {
            throw new SupplyNotFoundException(SupplyId.of(supplyId));
        }
        return supply;
    }

    /**
     * The single spelling of "Community Admin of this supply's community". Every caller passes an
     * already-resolved supply so the rule -- and the lookup behind it -- exists exactly once.
     */
    private boolean isCommunityAdminOf(Supply supply, User user) {
        UUID communityId = supply.getCommunity() != null ? supply.getCommunity().getId() : null;
        return helper.hasCommunityAdminRoleIn(user, communityId);
    }

    private boolean isOwner(Supply supply, User user) {
        return supply.getUser() != null && supply.getUser().getId() != null
                && supply.getUser().getId().equals(user.getId());
    }

    private Supply findSupply(UUID supplyId) {
        return getSupplyRepository.findById(SupplyId.of(supplyId)).orElse(null);
    }
}

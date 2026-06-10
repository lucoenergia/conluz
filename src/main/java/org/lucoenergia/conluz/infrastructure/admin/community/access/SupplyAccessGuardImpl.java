package org.lucoenergia.conluz.infrastructure.admin.community.access;

import org.lucoenergia.conluz.domain.admin.community.access.SupplyAccessGuard;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.shared.SupplyId;

import java.util.Optional;
import java.util.UUID;

public class SupplyAccessGuardImpl implements SupplyAccessGuard {

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
        Supply supply = getSupplyRepository.findById(SupplyId.of(supplyId)).orElse(null);
        if (supply == null) {
            return throwNotFoundIfAuthorized(supplyId);
        }
        UUID communityId = supply.getCommunity() != null ? supply.getCommunity().getId() : null;
        if (helper.hasCommunityAdminRoleIn(user, communityId)) {
            return true;
        }
        return isOwner(supply, user);
    }

    @Override
    public boolean canEditSupply(UUID supplyId) {
        User user = helper.getCurrentUser().orElse(null);
        Supply supply = getSupplyRepository.findById(SupplyId.of(supplyId)).orElse(null);
        if (user == null) {
            return false;
        }
        if (supply == null) {
            return throwNotFoundIfAuthorized(supplyId);
        }
        UUID communityId = supply.getCommunity() != null ? supply.getCommunity().getId() : null;
        if (helper.hasCommunityAdminRoleIn(user, communityId)) {
            return true;
        }
        return isOwner(supply, user);
    }

    private boolean isOwner(Supply supply, User user) {
        return supply.getUser() != null && supply.getUser().getId() != null
                && supply.getUser().getId().equals(user.getId());
    }

    private boolean throwNotFoundIfAuthorized(UUID supplyId) {
        Optional<User> userOpt = helper.getCurrentUser();
        if (userOpt.isPresent() && Boolean.TRUE.equals(userOpt.get().isPlatformAdmin())) {
            throw new SupplyNotFoundException(SupplyId.of(supplyId));
        }
        return false;
    }
}

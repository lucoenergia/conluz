package org.lucoenergia.conluz.infrastructure.admin.community.access;

import org.lucoenergia.conluz.domain.admin.community.CommunityNotFoundException;
import org.lucoenergia.conluz.domain.admin.community.access.PlantAccessGuard;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.plant.PlantNotFoundException;
import org.lucoenergia.conluz.domain.production.plant.get.GetPlantRepository;
import org.lucoenergia.conluz.domain.shared.PlantId;
import org.lucoenergia.conluz.domain.shared.SupplyCode;

import java.util.UUID;

class PlantAccessGuardImpl implements PlantAccessGuard {

    private final CommunityAccessGuardHelper helper;
    private final GetPlantRepository getPlantRepository;
    private final GetSupplyRepository getSupplyRepository;

    public PlantAccessGuardImpl(CommunityAccessGuardHelper helper, GetPlantRepository getPlantRepository,
                                GetSupplyRepository getSupplyRepository) {
        this.helper = helper;
        this.getPlantRepository = getPlantRepository;
        this.getSupplyRepository = getSupplyRepository;
    }

    @Override
    public boolean canManagePlant(UUID plantId) {
        User user = helper.getCurrentUser().orElse(null);
        if (user == null) {
            return false;
        }
        UUID communityId = communityIdOfVisiblePlant(user, plantId);
        // A member of the plant's community can see it but only community admins may manage it
        // (member-non-admin -> 403). Non-members never get here (they received a 404 above).
        return helper.hasCommunityAdminRoleIn(user, communityId);
    }

    @Override
    public boolean canReadPlant(UUID plantId) {
        User user = helper.getCurrentUser().orElse(null);
        if (user == null) {
            return false;
        }
        communityIdOfVisiblePlant(user, plantId);
        return true;
    }

    @Override
    public boolean canCreatePlant(String supplyCode) {
        User user = helper.getCurrentUser().orElse(null);
        if (user == null) {
            return false;
        }
        if (supplyCode == null) {
            return false;
        }
        Supply supply = getSupplyRepository.findByCode(SupplyCode.of(supplyCode)).orElse(null);
        UUID communityId = supply != null && supply.getCommunity() != null
                ? supply.getCommunity().getId() : null;
        // The supply is the resource whose existence must not leak: a caller who cannot see it
        // (it does not exist, or they neither administer its community nor own it) gets a 404.
        if (supply == null || !(helper.hasCommunityAdminRoleIn(user, communityId) || isOwner(supply, user))) {
            throw new SupplyNotFoundException(SupplyCode.of(supplyCode));
        }
        // Owners who are not community admins can see the supply but may not create plants (-> 403).
        return helper.hasCommunityAdminRoleIn(user, communityId);
    }

    @Override
    public boolean canListPlants(UUID communityId) {
        User user = helper.getCurrentUser().orElse(null);
        if (user == null) {
            return false;
        }
        if (!helper.canSeeCommunity(user, communityId)) {
            throw new CommunityNotFoundException(communityId);
        }
        // Any enabled member (regardless of role) of the community can list its plants.
        return helper.hasMembershipInCommunity(user, communityId);
    }

    /**
     * Resolves the community of the plant the user is allowed to see, throwing
     * {@link PlantNotFoundException} (404) when the plant does not exist or the user is not a
     * member of its community — so the plant's existence is never leaked to non-members.
     */
    private UUID communityIdOfVisiblePlant(User user, UUID plantId) {
        Plant plant = getPlantRepository.findById(PlantId.of(plantId)).orElse(null);
        if (plant == null) {
            throw new PlantNotFoundException(PlantId.of(plantId));
        }
        UUID communityId = plant.getSupply() != null && plant.getSupply().getCommunity() != null
                ? plant.getSupply().getCommunity().getId() : null;
        if (!helper.hasMembershipInCommunity(user, communityId)) {
            throw new PlantNotFoundException(PlantId.of(plantId));
        }
        return communityId;
    }

    private boolean isOwner(Supply supply, User user) {
        return supply.getUser() != null && supply.getUser().getId() != null
                && supply.getUser().getId().equals(user.getId());
    }
}

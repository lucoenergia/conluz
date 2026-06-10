package org.lucoenergia.conluz.infrastructure.admin.community.access;

import org.lucoenergia.conluz.domain.admin.community.access.PlantAccessGuard;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.plant.get.GetPlantRepository;
import org.lucoenergia.conluz.domain.shared.PlantId;
import org.lucoenergia.conluz.domain.shared.SupplyCode;

import java.util.Optional;
import java.util.UUID;

public class PlantAccessGuardImpl implements PlantAccessGuard {

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
        Optional<Plant> plant = getPlantRepository.findById(PlantId.of(plantId));
        if (plant.isEmpty()) {
            return false;
        }
        UUID communityId = plant.get().getSupply() != null && plant.get().getSupply().getCommunity() != null
                ? plant.get().getSupply().getCommunity().getId() : null;
        return helper.hasCommunityAdminRoleIn(user, communityId);
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
        Optional<Supply> supply = getSupplyRepository.findByCode(SupplyCode.of(supplyCode));
        if (supply.isEmpty()) {
            return false;
        }
        UUID communityId = supply.get().getCommunity() != null ? supply.get().getCommunity().getId() : null;
        return helper.hasCommunityAdminRoleIn(user, communityId);
    }
}

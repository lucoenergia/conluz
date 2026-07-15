package org.lucoenergia.conluz.infrastructure.admin.community.access;

import org.lucoenergia.conluz.domain.admin.community.CommunityNotFoundException;
import org.lucoenergia.conluz.domain.admin.community.access.*;
import org.lucoenergia.conluz.domain.admin.community.get.GetCommunityRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.GetMembershipsRepository;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.auth.AuthService;
import org.lucoenergia.conluz.domain.production.plant.get.GetPlantRepository;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;

@Service("communityAccessGuard")
public class CommunityAccessGuardImpl implements CommunityAccessGuard {

    private final CommunityAccessGuardHelper helper;
    private final SupplyAccessGuard supplyAccessGuard;
    private final MembershipAccessGuard membershipAccessGuard;
    private final UserAccessGuard userAccessGuard;
    private final PlantAccessGuard plantAccessGuard;

    public CommunityAccessGuardImpl(AuthService authService,
                                    GetCommunityRepository getCommunityRepository,
                                    GetMembershipsRepository getMembershipsRepository,
                                    GetSupplyRepository getSupplyRepository,
                                    GetPlantRepository getPlantRepository) {
        this.helper = new CommunityAccessGuardHelper(authService, getCommunityRepository);
        this.supplyAccessGuard = new SupplyAccessGuardImpl(helper, getSupplyRepository);
        this.membershipAccessGuard = new MembershipAccessGuardImpl(helper);
        this.userAccessGuard = new UserAccessGuardImpl(helper, getMembershipsRepository);
        this.plantAccessGuard = new PlantAccessGuardImpl(helper, getPlantRepository, getSupplyRepository);
    }

    @Override
    public boolean canReadSupply(UUID supplyId) {
        return supplyAccessGuard.canReadSupply(supplyId);
    }

    @Override
    public boolean canEditSupply(UUID supplyId) {
        return supplyAccessGuard.canEditSupply(supplyId);
    }

    @Override
    public boolean canReadCommunity(UUID communityId) {
        User user = helper.getCurrentUser().orElse(null);
        if (user == null) {
            return false;
        }
        if (!helper.canSeeCommunity(user, communityId)) {
            throw new CommunityNotFoundException(communityId);
        }
        return true;
    }

    @Override
    public boolean isMemberOfCommunity(UUID communityId) {
        User user = helper.getCurrentUser().orElse(null);
        if (user == null) {
            return false;
        }
        if (!helper.canSeeCommunity(user, communityId)) {
            throw new CommunityNotFoundException(communityId);
        }
        return helper.hasMembershipInCommunity(user, communityId);
    }

    @Override
    public boolean canManageCommunity(UUID communityId) {
        User user = helper.getCurrentUser().orElse(null);
        if (user == null) {
            return false;
        }
        if (!helper.canSeeCommunity(user, communityId)) {
            throw new CommunityNotFoundException(communityId);
        }
        return helper.hasCommunityAdminRoleIn(user, communityId);
    }

    @Override
    public boolean canManageMemberships(UUID communityId) {
        return membershipAccessGuard.canManageMemberships(communityId);
    }

    @Override
    public boolean canReadUser(UUID userId) {
        return userAccessGuard.canReadUser(userId);
    }

    @Override
    public boolean canEditUser(UUID userId) {
        return userAccessGuard.canEditUser(userId);
    }

    @Override
    public boolean canCreateUserIn(UUID communityId) {
        return userAccessGuard.canCreateUserIn(communityId);
    }

    @Override
    public boolean canListUsers() {
        return userAccessGuard.canListUsers();
    }

    @Override
    public boolean canManagePlant(UUID plantId) {
        return plantAccessGuard.canManagePlant(plantId);
    }

    @Override
    public boolean canReadPlant(UUID plantId) {
        return plantAccessGuard.canReadPlant(plantId);
    }

    @Override
    public boolean canCreatePlant(String supplyCode) {
        return plantAccessGuard.canCreatePlant(supplyCode);
    }

    @Override
    public boolean canListPlants(UUID communityId) {
        return plantAccessGuard.canListPlants(communityId);
    }

    @Override
    public Set<UUID> visibleCommunityIds() {
        User user = helper.getCurrentUser().orElse(null);
        return helper.visibleCommunityIds(user);
    }

    @Override
    public Set<UUID> adminCommunityIds() {
        User user = helper.getCurrentUser().orElse(null);
        return helper.adminCommunityIds(user);
    }

    @Override
    public boolean isCurrentUser(UUID userId) {
        User user = helper.getCurrentUser().orElse(null);
        return helper.isCurrentUser(user, userId);
    }
}

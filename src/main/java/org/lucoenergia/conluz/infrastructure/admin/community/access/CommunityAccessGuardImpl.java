package org.lucoenergia.conluz.infrastructure.admin.community.access;

import org.lucoenergia.conluz.domain.admin.community.CommunityNotFoundException;
import org.lucoenergia.conluz.domain.admin.community.access.*;
import org.lucoenergia.conluz.domain.admin.community.access.policy.AccessDecision;
import org.lucoenergia.conluz.domain.admin.community.access.policy.CallerMemberships;
import org.lucoenergia.conluz.domain.admin.community.access.policy.CommunityAccessPolicy;
import org.lucoenergia.conluz.domain.admin.community.get.GetCommunityRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.GetMembershipsRepository;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.auth.AuthService;
import org.lucoenergia.conluz.domain.production.plant.get.GetPlantRepository;
import org.lucoenergia.conluz.domain.production.sharingagreement.get.GetSharingAgreementRepository;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;

@Service("communityAccessGuard")
public class CommunityAccessGuardImpl implements CommunityAccessGuard {

    private final CommunityAccessGuardHelper helper;
    private final CommunityAccessPolicy communityAccessPolicy;
    private final SupplyAccessGuard supplyAccessGuard;
    private final MembershipAccessGuard membershipAccessGuard;
    private final UserAccessGuard userAccessGuard;
    private final PlantAccessGuard plantAccessGuard;
    private final PlatformAccessGuard platformAccessGuard;

    public CommunityAccessGuardImpl(AuthService authService,
                                    GetCommunityRepository getCommunityRepository,
                                    GetMembershipsRepository getMembershipsRepository,
                                    GetSupplyRepository getSupplyRepository,
                                    GetPlantRepository getPlantRepository,
                                    GetSharingAgreementRepository getSharingAgreementRepository,
                                    AccessPolicies policies) {
        this.helper = new CommunityAccessGuardHelper(authService, getCommunityRepository);
        this.communityAccessPolicy = policies.community();
        this.supplyAccessGuard = new SupplyAccessGuardImpl(helper, getSupplyRepository, policies.supply());
        this.membershipAccessGuard = new MembershipAccessGuardImpl(helper, policies.membership());
        this.userAccessGuard = new UserAccessGuardImpl(helper, getMembershipsRepository, policies.user());
        this.plantAccessGuard = new PlantAccessGuardImpl(helper, getPlantRepository, getSupplyRepository,
                getSharingAgreementRepository, policies.plant(), policies.sharingAgreement());
        this.platformAccessGuard = new PlatformAccessGuardImpl(helper, policies.platform());
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
    public boolean canReadSupplyPartitionCoefficients(UUID supplyId) {
        return supplyAccessGuard.canReadSupplyPartitionCoefficients(supplyId);
    }

    @Override
    public boolean isCommunityAdminOfSupply(UUID supplyId) {
        return supplyAccessGuard.isCommunityAdminOfSupply(supplyId);
    }

    @Override
    public boolean canReadCommunity(UUID communityId) {
        User user = helper.getCurrentUser().orElse(null);
        if (user == null) {
            return false;
        }
        return resolveCommunity(communityAccessPolicy.canRead(user, communityId), communityId);
    }

    @Override
    public boolean isMemberOfCommunity(UUID communityId) {
        User user = helper.getCurrentUser().orElse(null);
        if (user == null) {
            return false;
        }
        return resolveCommunity(communityAccessPolicy.isMember(user, communityId), communityId);
    }

    @Override
    public boolean canReadCommunityProduction(UUID communityId) {
        return isMemberOfCommunity(communityId);
    }

    @Override
    public boolean canListSupplies(UUID communityId) {
        return isMemberOfCommunity(communityId);
    }

    @Override
    public boolean canManageCommunity(UUID communityId) {
        User user = helper.getCurrentUser().orElse(null);
        if (user == null) {
            return false;
        }
        return resolveCommunity(communityAccessPolicy.canManage(user, communityId), communityId);
    }

    @Override
    public boolean canManageMemberships(UUID communityId) {
        return membershipAccessGuard.canManageMemberships(communityId);
    }

    @Override
    public boolean canManageMembershipInvestment(UUID communityId) {
        return membershipAccessGuard.canManageMembershipInvestment(communityId);
    }

    @Override
    public boolean canReadMembershipPayback(UUID communityId, UUID userId) {
        return membershipAccessGuard.canReadMembershipPayback(communityId, userId);
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
    public boolean canListSuppliesOfUser(UUID userId) {
        return userAccessGuard.canListSuppliesOfUser(userId);
    }

    @Override
    public boolean canDeleteUser(UUID userId) {
        return userAccessGuard.canDeleteUser(userId);
    }

    @Override
    public boolean canEnableUser(UUID userId) {
        return userAccessGuard.canEnableUser(userId);
    }

    @Override
    public boolean canDisableUser(UUID userId) {
        return userAccessGuard.canDisableUser(userId);
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
    public boolean canReadSharingAgreement(UUID plantId, UUID sharingAgreementId) {
        return plantAccessGuard.canReadSharingAgreement(plantId, sharingAgreementId);
    }

    @Override
    public boolean canManageSharingAgreement(UUID plantId) {
        return plantAccessGuard.canManageSharingAgreement(plantId);
    }

    @Override
    public boolean canListSharingAgreements(UUID plantId) {
        return plantAccessGuard.canListSharingAgreements(plantId);
    }

    @Override
    public boolean canManageSharingAgreement(UUID plantId, UUID sharingAgreementId) {
        return plantAccessGuard.canManageSharingAgreement(plantId, sharingAgreementId);
    }

    @Override
    public Set<UUID> visibleCommunityIds() {
        User user = helper.getCurrentUser().orElse(null);
        return helper.visibleCommunityIds(user);
    }

    @Override
    public Set<UUID> adminCommunityIds() {
        User user = helper.getCurrentUser().orElse(null);
        return CallerMemberships.adminCommunityIds(user);
    }

    @Override
    public boolean isCurrentUser(UUID userId) {
        User user = helper.getCurrentUser().orElse(null);
        return CallerMemberships.isCurrentUser(user, userId);
    }

    @Override
    public boolean canCreateCommunity() {
        return platformAccessGuard.canCreateCommunity();
    }

    @Override
    public boolean canUpdateCommunity(UUID communityId) {
        return platformAccessGuard.canUpdateCommunity(communityId);
    }

    @Override
    public boolean canEnableCommunity(UUID communityId) {
        return platformAccessGuard.canEnableCommunity(communityId);
    }

    @Override
    public boolean canDisableCommunity(UUID communityId) {
        return platformAccessGuard.canDisableCommunity(communityId);
    }

    @Override
    public boolean canGrantPlatformAdmin(UUID userId) {
        return platformAccessGuard.canGrantPlatformAdmin(userId);
    }

    @Override
    public boolean canRevokePlatformAdmin(UUID userId) {
        return platformAccessGuard.canRevokePlatformAdmin(userId);
    }

    private boolean resolveCommunity(AccessDecision decision, UUID communityId) {
        if (decision == AccessDecision.NOT_VISIBLE) {
            throw new CommunityNotFoundException(communityId);
        }
        return decision.isAllowed();
    }
}

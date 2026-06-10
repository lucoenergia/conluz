package org.lucoenergia.conluz.infrastructure.admin.community.access;

import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.access.CommunityAccessGuard;
import org.lucoenergia.conluz.domain.admin.community.membership.GetMembershipsRepository;
import org.lucoenergia.conluz.domain.admin.supply.SharingAgreement;
import org.lucoenergia.conluz.domain.admin.supply.SharingAgreementId;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSharingAgreementRepository;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.auth.AuthService;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.plant.get.GetPlantRepository;
import org.lucoenergia.conluz.domain.shared.PlantId;
import org.lucoenergia.conluz.domain.shared.SupplyCode;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service("communityAccessGuard")
@Transactional(readOnly = true)
public class CommunityAccessGuardImpl implements CommunityAccessGuard {

    private final AuthService authService;
    private final GetMembershipsRepository getMembershipsRepository;
    private final GetSupplyRepository getSupplyRepository;
    private final GetPlantRepository getPlantRepository;
    private final GetSharingAgreementRepository getSharingAgreementRepository;

    public CommunityAccessGuardImpl(AuthService authService,
                                    GetMembershipsRepository getMembershipsRepository,
                                    GetSupplyRepository getSupplyRepository,
                                    GetPlantRepository getPlantRepository,
                                    GetSharingAgreementRepository getSharingAgreementRepository) {
        this.authService = authService;
        this.getMembershipsRepository = getMembershipsRepository;
        this.getSupplyRepository = getSupplyRepository;
        this.getPlantRepository = getPlantRepository;
        this.getSharingAgreementRepository = getSharingAgreementRepository;
    }

    @Override
    public boolean canReadSupply(Supply supply) {
        User user = authService.getCurrentUser().orElse(null);
        if (user == null) {
            return false;
        }
        UUID communityId = supply.getCommunity() != null ? supply.getCommunity().getId() : null;
        if (hasCommunityAdminRoleIn(user, communityId)) {
            return true;
        }
        return isOwner(supply, user);
    }

    @Override
    public boolean canEditSupply(UUID supplyId) {
        User user = authService.getCurrentUser().orElse(null);
        Supply supply = getSupplyRepository.findById(SupplyId.of(supplyId)).orElse(null);
        if (user == null) {
            return false;
        }
        if (supply == null) {
            return throwNotFoundIfAuthorized(supplyId);
        }
        UUID communityId = supply.getCommunity() != null ? supply.getCommunity().getId() : null;
        if (hasCommunityAdminRoleIn(user, communityId)) {
            return true;
        }
        return isOwner(supply, user);
    }

    @Override
    public boolean canReadCommunity(UUID communityId) {
        User user = authService.getCurrentUser().orElse(null);
        if (user == null) {
            return false;
        }
        if (Boolean.TRUE.equals(user.isPlatformAdmin())) {
            return true;
        }
        return hasMembershipInCommunity(user, communityId);
    }

    @Override
    public boolean canManageCommunity(UUID communityId) {
        User user = authService.getCurrentUser().orElse(null);
        if (user == null) {
            return false;
        }
        return hasCommunityAdminRoleIn(user, communityId);
    }

    @Override
    public boolean canManageMemberships(UUID communityId) {
        User user = authService.getCurrentUser().orElse(null);
        if (user == null) {
            return false;
        }
        if (Boolean.TRUE.equals(user.isPlatformAdmin())) {
            return true;
        }
        return hasCommunityAdminRoleIn(user, communityId);
    }

    @Override
    public boolean canReadUser(UUID userId) {
        User user = authService.getCurrentUser().orElse(null);
        if (user == null) {
            return false;
        }
        if (user.isPlatformAdmin()) {
            return true;
        }
        if (user.getId().equals(userId)) {
            return true;
        }
        return isCommunityAdminOfAnyCommunityOfTargetUser(user, userId);
    }

    @Override
    public boolean canEditUser(UUID userId) {
        User user = authService.getCurrentUser().orElse(null);
        if (user == null) {
            return false;
        }
        if (user.isPlatformAdmin()) {
            return true;
        }
        return isCommunityAdminOfAnyCommunityOfTargetUser(user, userId);
    }

    @Override
    public Set<UUID> visibleCommunityIds() {
        User user = authService.getCurrentUser().orElse(null);
        if (user == null) {
            return Set.of();
        }
        if (Boolean.TRUE.equals(user.isPlatformAdmin())) {
            return null;
        }
        if (user.getMemberships() == null) {
            return Set.of();
        }
        return user.getMemberships().stream()
                .filter(m -> Boolean.TRUE.equals(m.isEnabled()))
                .map(m -> m.getCommunity().getId())
                .collect(Collectors.toSet());
    }

    @Override
    public boolean canCreateUserIn(UUID communityId) {
        User user = authService.getCurrentUser().orElse(null);
        if (user == null) {
            return false;
        }
        if (user.isPlatformAdmin()) {
            return true;
        }
        if (Boolean.TRUE.equals(user.isPlatformAdmin())) {
            return true;
        }
        if (communityId == null) {
            return false;
        }
        return hasCommunityAdminRoleIn(user, communityId);
    }

    @Override
    public boolean canListUsers() {
        User user = authService.getCurrentUser().orElse(null);
        if (user == null) {
            return false;
        }
        if (Boolean.TRUE.equals(user.isPlatformAdmin())) {
            return true;
        }
        if (user.getMemberships() == null) {
            return false;
        }
        return user.getMemberships().stream()
                .anyMatch(m ->
                        m.getRole() == CommunityRole.COMMUNITY_ADMIN && Boolean.TRUE.equals(m.isEnabled()));
    }

    @Override
    public boolean canManagePlant(UUID plantId) {
        User user = authService.getCurrentUser().orElse(null);
        if (user == null) {
            return false;
        }
        Optional<Plant> plant = getPlantRepository.findById(PlantId.of(plantId));
        if (plant.isEmpty()) {
            return false;
        }
        UUID communityId = plant.get().getSupply() != null && plant.get().getSupply().getCommunity() != null
                ? plant.get().getSupply().getCommunity().getId() : null;
        return hasCommunityAdminRoleIn(user, communityId);
    }

    @Override
    public boolean canCreatePlant(String supplyCode) {
        User user = authService.getCurrentUser().orElse(null);
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
        return hasCommunityAdminRoleIn(user, communityId);
    }

    @Override
    public boolean canManageSharingAgreement(UUID agreementId) {
        User user = authService.getCurrentUser().orElse(null);
        if (user == null) {
            return false;
        }
        Optional<SharingAgreement> agreement = getSharingAgreementRepository.findById(SharingAgreementId.of(agreementId));
        if (agreement.isEmpty()) {
            return false;
        }
        UUID communityId = agreement.get().getCommunityId();
        return hasCommunityAdminRoleIn(user, communityId);
    }

    private boolean isCommunityAdminOfAnyCommunityOfTargetUser(User caller, UUID targetUserId) {
        Set<UUID> targetCommunityIds = getMembershipsRepository.findByUserId(targetUserId).stream()
                .filter(m -> Boolean.TRUE.equals(m.isEnabled()))
                .map(m -> m.getCommunity() != null ? m.getCommunity().getId() : null)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (targetCommunityIds.isEmpty()) return false;
        if (caller.getMemberships() == null) return false;
        return caller.getMemberships().stream()
                .anyMatch(m -> targetCommunityIds.contains(m.getCommunity().getId())
                        && m.getRole() == CommunityRole.COMMUNITY_ADMIN
                        && Boolean.TRUE.equals(m.isEnabled()));
    }

    private boolean isOwner(Supply supply, User user) {
        return supply.getUser() != null && supply.getUser().getId() != null
                && supply.getUser().getId().equals(user.getId());
    }

    private boolean hasMembershipInCommunity(User user, UUID communityId) {
        if (communityId == null || user.getMemberships() == null) return false;
        return user.getMemberships().stream()
                .anyMatch(m -> communityId.equals(m.getCommunity().getId())
                        && Boolean.TRUE.equals(m.isEnabled()));
    }

    private boolean hasCommunityAdminRoleIn(User user, UUID communityId) {
        if (communityId == null || user.getMemberships() == null) return false;
        return user.getMemberships().stream()
                .anyMatch(m -> communityId.equals(m.getCommunity().getId())
                        && m.getRole() == CommunityRole.COMMUNITY_ADMIN
                        && Boolean.TRUE.equals(m.isEnabled()));
    }

    /**
     * Called when a supply lookup by UUID returns empty. Admins (who would otherwise have access)
     * receive a honest 404; everyone else receives false so Spring Security issues 403, avoiding
     * resource-existence leakage to unauthorized callers.
     */
    private boolean throwNotFoundIfAuthorized(UUID supplyId) {
        User user = authService.getCurrentUser().orElse(null);
        if (user != null && Boolean.TRUE.equals(user.isPlatformAdmin())) {
            throw new SupplyNotFoundException(SupplyId.of(supplyId));
        }
        return false;
    }
}

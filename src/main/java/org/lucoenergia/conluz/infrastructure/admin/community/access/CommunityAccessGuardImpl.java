package org.lucoenergia.conluz.infrastructure.admin.community.access;

import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.access.CommunityAccessGuard;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.admin.user.Role;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.auth.AuthService;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityMembershipJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service("communityAccessGuard")
@Transactional(readOnly = true)
public class CommunityAccessGuardImpl implements CommunityAccessGuard {

    private final AuthService authService;
    private final CommunityMembershipJpaRepository membershipJpaRepository;
    private final GetSupplyRepository getSupplyRepository;

    public CommunityAccessGuardImpl(AuthService authService,
                                    CommunityMembershipJpaRepository membershipJpaRepository, GetSupplyRepository getSupplyRepository) {
        this.authService = authService;
        this.membershipJpaRepository = membershipJpaRepository;
        this.getSupplyRepository = getSupplyRepository;
    }

    @Override
    public boolean canReadSupply(Supply supply) {
        User user = authService.getCurrentUser().orElse(null);
        if (user == null) return false;
        if (user.getRole() == Role.ADMIN) return true;
        if (isOwner(supply, user)) return true;
        return hasMembershipInCommunity(user, supply.getCommunity() != null ? supply.getCommunity().getId() : null);
    }

    @Override
    public boolean canEditSupply(UUID supplyId) {
        User user = authService.getCurrentUser().orElse(null);
        Supply supply = getSupplyRepository.findById(SupplyId.of(supplyId)).orElse(null);
        if (user == null) return false;
        if (supply == null) return throwNotFoundIfAuthorized(supplyId);
        if (user.getRole() == Role.ADMIN) return true;
        if (isOwner(supply, user)) return true;
        return hasCommunityAdminRoleIn(user, supply.getCommunity() != null ? supply.getCommunity().getId() : null);
    }

    @Override
    public boolean canReadCommunity(UUID communityId) {
        User user = authService.getCurrentUser().orElse(null);
        if (user == null) return false;
        if (user.getRole() == Role.ADMIN) return true;
        return hasMembershipInCommunity(user, communityId);
    }

    @Override
    public boolean canManageCommunity(UUID communityId) {
        User user = authService.getCurrentUser().orElse(null);
        if (user == null) return false;
        if (user.getRole() == Role.ADMIN) return true;
        return hasCommunityAdminRoleIn(user, communityId);
    }

    @Override
    public boolean canManagePlatform() {
        return authService.getCurrentUser()
                .map(User::isPlatformAdmin)
                .orElse(false);
    }

    @Override
    public boolean canManageMemberships(UUID communityId) {
        User user = authService.getCurrentUser().orElse(null);
        if (user == null) return false;
        if (user.getRole() == Role.ADMIN) return true;
        return hasCommunityAdminRoleIn(user, communityId);
    }

    @Override
    public boolean canReadUser(UUID userId) {
        User user = authService.getCurrentUser().orElse(null);
        if (user == null) return false;
        if (user.isPlatformAdmin()) return true;
        if (user.getId().equals(userId)) return true;
        return isCommunityAdminOfAnyCommunityOfTargetUser(user, userId);
    }

    @Override
    public boolean canEditUser(UUID userId) {
        User user = authService.getCurrentUser().orElse(null);
        if (user == null) return false;
        if (user.isPlatformAdmin()) return true;
        return isCommunityAdminOfAnyCommunityOfTargetUser(user, userId);
    }

    @Override
    public Set<UUID> visibleCommunityIds() {
        User user = authService.getCurrentUser().orElse(null);
        if (user == null) return Set.of();
        if (user.getRole() == Role.ADMIN || Boolean.TRUE.equals(user.isPlatformAdmin())) {
            return null;
        }
        if (user.getMemberships() == null) return Set.of();
        return user.getMemberships().stream()
                .filter(m -> Boolean.TRUE.equals(m.isEnabled()))
                .map(m -> m.getCommunity().getId())
                .collect(Collectors.toSet());
    }

    @Override
    public boolean canCreateUserIn(UUID communityId) {
        User user = authService.getCurrentUser().orElse(null);
        if (user == null) return false;
        if (user.isPlatformAdmin()) return true;
        if (user.getRole() == Role.ADMIN) return true;
        if (communityId == null) return false;
        return hasCommunityAdminRoleIn(user, communityId);
    }

    private boolean isCommunityAdminOfAnyCommunityOfTargetUser(User caller, UUID targetUserId) {
        Set<UUID> targetCommunityIds = membershipJpaRepository.findByUserId(targetUserId).stream()
                .filter(e -> Boolean.TRUE.equals(e.isEnabled()))
                .map(e -> e.getCommunity() != null ? e.getCommunity().getId() : null)
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
        if (user != null && (user.getRole() == Role.ADMIN || Boolean.TRUE.equals(user.isPlatformAdmin()))) {
            throw new SupplyNotFoundException(SupplyId.of(supplyId));
        }
        return false;
    }
}

package org.lucoenergia.conluz.infrastructure.admin.community.access;

import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.access.CommunityAccessGuard;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.user.Role;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.auth.AuthService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service("communityAccessGuard")
@Transactional(readOnly = true)
public class CommunityAccessGuardImpl implements CommunityAccessGuard {

    private final AuthService authService;

    public CommunityAccessGuardImpl(AuthService authService) {
        this.authService = authService;
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
    public boolean canEditSupply(Supply supply) {
        User user = authService.getCurrentUser().orElse(null);
        if (user == null) return false;
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
}

package org.lucoenergia.conluz.infrastructure.admin.community.access;

import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.access.UserAccessGuard;
import org.lucoenergia.conluz.domain.admin.community.membership.GetMembershipsRepository;
import org.lucoenergia.conluz.domain.admin.user.User;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class UserAccessGuardImpl implements UserAccessGuard {

    private final CommunityAccessGuardHelper helper;
    private final GetMembershipsRepository getMembershipsRepository;

    public UserAccessGuardImpl(CommunityAccessGuardHelper helper, GetMembershipsRepository getMembershipsRepository) {
        this.helper = helper;
        this.getMembershipsRepository = getMembershipsRepository;
    }

    @Override
    public boolean canReadUser(UUID userId) {
        User user = helper.getCurrentUser().orElse(null);
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
        User user = helper.getCurrentUser().orElse(null);
        if (user == null) {
            return false;
        }
        if (user.isPlatformAdmin()) {
            return true;
        }
        return isCommunityAdminOfAnyCommunityOfTargetUser(user, userId);
    }

    @Override
    public boolean canCreateUserIn(UUID communityId) {
        User user = helper.getCurrentUser().orElse(null);
        if (user == null) {
            return false;
        }
        if (Boolean.TRUE.equals(user.isPlatformAdmin())) {
            return true;
        }
        if (communityId == null) {
            return false;
        }
        return helper.hasCommunityAdminRoleIn(user, communityId);
    }

    @Override
    public boolean canListUsers() {
        User user = helper.getCurrentUser().orElse(null);
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
}

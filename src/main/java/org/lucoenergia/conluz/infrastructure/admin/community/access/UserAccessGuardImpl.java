package org.lucoenergia.conluz.infrastructure.admin.community.access;

import org.lucoenergia.conluz.domain.admin.community.CommunityNotFoundException;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.access.UserAccessGuard;
import org.lucoenergia.conluz.domain.admin.community.membership.GetMembershipsRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserNotFoundException;
import org.lucoenergia.conluz.domain.shared.UserId;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

class UserAccessGuardImpl implements UserAccessGuard {

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
        if (!canSeeUser(user, userId)) {
            throw new UserNotFoundException(UserId.of(userId));
        }
        return true;
    }

    @Override
    public boolean canEditUser(UUID userId) {
        User user = helper.getCurrentUser().orElse(null);
        if (user == null) {
            return false;
        }
        if (!canSeeUser(user, userId)) {
            throw new UserNotFoundException(UserId.of(userId));
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
        if (!helper.canSeeCommunity(user, communityId)) {
            throw new CommunityNotFoundException(communityId);
        }
        return helper.hasCommunityAdminRoleIn(user, communityId);
    }

    /**
     * Whether the caller is able to <em>see</em> the target user exists: platform admins see
     * everyone, a user sees themselves, and a community admin sees the members of the communities
     * they administer. Used to decide between a 404 (cannot see the user) and a 403 (can see but
     * lacks permission for the action).
     */
    private boolean canSeeUser(User caller, UUID userId) {
        if (Boolean.TRUE.equals(caller.isPlatformAdmin())) {
            return true;
        }
        if (caller.getId().equals(userId)) {
            return true;
        }
        return isCommunityAdminOfAnyCommunityOfTargetUser(caller, userId);
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

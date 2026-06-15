package org.lucoenergia.conluz.infrastructure.admin.community.access;

import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.get.GetCommunityRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.auth.AuthService;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

class CommunityAccessGuardHelper {

    private final AuthService authService;
    private final GetCommunityRepository getCommunityRepository;

    public CommunityAccessGuardHelper(AuthService authService, GetCommunityRepository getCommunityRepository) {
        this.authService = authService;
        this.getCommunityRepository = getCommunityRepository;
    }

    public Optional<User> getCurrentUser() {
        return authService.getCurrentUser();
    }

    public boolean hasCommunityAdminRoleIn(User user, UUID communityId) {
        if (communityId == null || user.getMemberships() == null) return false;
        return user.getMemberships().stream()
                .anyMatch(m -> communityId.equals(m.getCommunity().getId())
                        && m.getRole() == CommunityRole.COMMUNITY_ADMIN
                        && Boolean.TRUE.equals(m.isEnabled()));
    }

    public boolean hasMembershipInCommunity(User user, UUID communityId) {
        if (communityId == null || user.getMemberships() == null) return false;
        return user.getMemberships().stream()
                .anyMatch(m -> communityId.equals(m.getCommunity().getId())
                        && Boolean.TRUE.equals(m.isEnabled()));
    }

    public boolean isCurrentUser(User user, UUID userId) {
        return user != null && userId != null && userId.equals(user.getId());
    }

    public Set<UUID> visibleCommunityIds(User user) {
        if (user == null) {
            return Set.of();
        }
        if (Boolean.TRUE.equals(user.isPlatformAdmin())) {
            return getCommunityRepository.findAllIds();
        }
        if (user.getMemberships() == null) {
            return Set.of();
        }
        return user.getMemberships().stream()
                .filter(m -> Boolean.TRUE.equals(m.isEnabled()))
                .map(m -> m.getCommunity().getId())
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    public Set<UUID> adminCommunityIds(User user) {
        if (user == null) {
            return Set.of();
        }
        if (user.getMemberships() == null) {
            return Set.of();
        }
        return user.getMemberships().stream()
                .filter(m -> m.getRole() == CommunityRole.COMMUNITY_ADMIN && Boolean.TRUE.equals(m.isEnabled()))
                .map(m -> m.getCommunity().getId())
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }
}

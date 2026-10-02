package org.lucoenergia.conluz.domain.admin.community.access.policy;

import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.user.User;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The caller's membership facts, spelled exactly once. Every access policy reads the caller's
 * standing through this class, so "enabled community admin of X" has a single definition rather
 * than one per guard.
 *
 * <p>Pure: it derives everything from the {@link User} handed to it and never loads anything.</p>
 */
public final class CallerMemberships {

    private CallerMemberships() {
    }

    public static boolean isPlatformAdmin(User user) {
        return user != null && Boolean.TRUE.equals(user.isPlatformAdmin());
    }

    public static boolean hasCommunityAdminRoleIn(User user, UUID communityId) {
        if (communityId == null || user.getMemberships() == null) return false;
        return user.getMemberships().stream()
                .anyMatch(m -> communityId.equals(m.getCommunity().getId())
                        && m.getRole() == CommunityRole.COMMUNITY_ADMIN
                        && Boolean.TRUE.equals(m.isEnabled()));
    }

    public static boolean hasMembershipInCommunity(User user, UUID communityId) {
        if (communityId == null || user.getMemberships() == null) return false;
        return user.getMemberships().stream()
                .anyMatch(m -> communityId.equals(m.getCommunity().getId())
                        && Boolean.TRUE.equals(m.isEnabled()));
    }

    /**
     * Whether the user is able to <em>see</em> the community exists (platform admins see every
     * community; otherwise the user must hold an enabled membership in it). Used to decide between
     * a 404 (cannot see the community) and a 403 (can see it but lacks the required permission).
     */
    public static boolean canSeeCommunity(User user, UUID communityId) {
        if (user == null) {
            return false;
        }
        if (Boolean.TRUE.equals(user.isPlatformAdmin())) {
            return true;
        }
        return hasMembershipInCommunity(user, communityId);
    }

    public static boolean isCurrentUser(User user, UUID userId) {
        return user != null && userId != null && userId.equals(user.getId());
    }

    /**
     * Whether the user administers <em>any</em> community. Deliberately not expressed as
     * {@code !adminCommunityIds(user).isEmpty()}: this question is about holding the role, so a
     * membership whose community carries no id still counts, where {@link #adminCommunityIds(User)}
     * has to drop it for want of something to put in the set.
     */
    public static boolean hasAnyEnabledCommunityAdminRole(User user) {
        if (user == null || user.getMemberships() == null) return false;
        return user.getMemberships().stream()
                .anyMatch(m ->
                        m.getRole() == CommunityRole.COMMUNITY_ADMIN && Boolean.TRUE.equals(m.isEnabled()));
    }

    public static Set<UUID> adminCommunityIds(User user) {
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

    public static Set<UUID> enabledMembershipCommunityIds(User user) {
        if (user == null || user.getMemberships() == null) {
            return Set.of();
        }
        return enabledCommunityIds(user.getMemberships());
    }

    /**
     * The communities of an arbitrary membership list that actually count — enabled, and attached to
     * a community with an id. Used for the <em>target</em> of a decision (e.g. the user being read),
     * whose memberships the caller does not carry.
     */
    public static Set<UUID> enabledCommunityIds(List<CommunityMembership> memberships) {
        if (memberships == null) {
            return Set.of();
        }
        return memberships.stream()
                .filter(m -> Boolean.TRUE.equals(m.isEnabled()))
                .map(m -> m.getCommunity() != null ? m.getCommunity().getId() : null)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }
}

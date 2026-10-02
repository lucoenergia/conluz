package org.lucoenergia.conluz.domain.admin.user.get;

import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.user.User;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Which users a caller may see, and which of their memberships: either everything, or the caller
 * themselves plus the users with an enabled membership in a given set of communities. It carries no
 * rule of its own — the access policy decides the scope, and the query and the response only apply it.
 */
public final class UserScope {

    private static final UserScope ALL = new UserScope(null, null);

    private final UUID selfId;
    private final Set<UUID> communityIds;

    private UserScope(UUID selfId, Set<UUID> communityIds) {
        this.selfId = selfId;
        this.communityIds = communityIds;
    }

    public static UserScope all() {
        return ALL;
    }

    /**
     * @param selfId       the caller, always in scope with every one of their memberships; {@code null}
     *                     when there is no caller
     * @param communityIds the communities whose enabled members, and whose memberships, are in scope
     */
    public static UserScope visibleTo(UUID selfId, Set<UUID> communityIds) {
        return new UserScope(selfId, Set.copyOf(Objects.requireNonNull(communityIds)));
    }

    public boolean isUnrestricted() {
        return communityIds == null;
    }

    /**
     * @return the caller the scope always admits; {@code null} when it is {@linkplain #isUnrestricted()
     * unrestricted} or there is no caller.
     */
    public UUID selfId() {
        return selfId;
    }

    /**
     * @return the communities the scope is restricted to; empty when it is {@linkplain #isUnrestricted()
     * unrestricted}, which is why callers must check that first.
     */
    public Set<UUID> communityIds() {
        return communityIds == null ? Collections.emptySet() : communityIds;
    }

    /**
     * Whether the scope admits this user, judged on their enabled memberships.
     */
    public boolean includes(User user) {
        if (isUnrestricted() || isSelf(user)) {
            return true;
        }
        return user.getMemberships() != null && user.getMemberships().stream()
                .anyMatch(m -> Boolean.TRUE.equals(m.isEnabled()) && inScopeCommunity(m));
    }

    /**
     * Whether the scope admits this membership of the given user: all of them for an unrestricted
     * scope and for the caller's own record, otherwise only those in the scope's communities.
     */
    public boolean includesMembershipOf(User user, CommunityMembership membership) {
        if (isUnrestricted() || isSelf(user)) {
            return true;
        }
        return inScopeCommunity(membership);
    }

    private boolean inScopeCommunity(CommunityMembership membership) {
        UUID communityId = membership.getCommunity() != null ? membership.getCommunity().getId() : null;
        return communityId != null && communityIds.contains(communityId);
    }

    private boolean isSelf(User user) {
        return selfId != null && selfId.equals(user.getId());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserScope that)) return false;
        return Objects.equals(selfId, that.selfId) && Objects.equals(communityIds, that.communityIds);
    }

    @Override
    public int hashCode() {
        return Objects.hash(selfId, communityIds);
    }

    @Override
    public String toString() {
        return isUnrestricted() ? "UserScope[all]" : "UserScope[self=" + selfId + ", communities=" + communityIds + "]";
    }
}

package org.lucoenergia.conluz.infrastructure.admin.community.access;

import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.CommunityNotFoundException;
import org.lucoenergia.conluz.domain.admin.community.access.UserAccessGuard;
import org.lucoenergia.conluz.domain.admin.community.access.policy.AccessDecision;
import org.lucoenergia.conluz.domain.admin.community.access.policy.CallerMemberships;
import org.lucoenergia.conluz.domain.admin.community.access.policy.UserAccessPolicy;
import org.lucoenergia.conluz.domain.admin.community.membership.GetMembershipsRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserNotFoundException;
import org.lucoenergia.conluz.domain.shared.UserId;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Adapter over {@link UserAccessPolicy}: {@code NOT_VISIBLE} becomes {@link UserNotFoundException}
 * (or {@link CommunityNotFoundException} when the community is the resource at stake), and
 * {@code FORBIDDEN} becomes {@code false}.
 */
class UserAccessGuardImpl implements UserAccessGuard {

    private final CommunityAccessGuardHelper helper;
    private final GetMembershipsRepository getMembershipsRepository;
    private final UserAccessPolicy policy;

    public UserAccessGuardImpl(CommunityAccessGuardHelper helper,
                               GetMembershipsRepository getMembershipsRepository) {
        this.helper = helper;
        this.getMembershipsRepository = getMembershipsRepository;
        this.policy = new UserAccessPolicy();
    }

    @Override
    public boolean canReadUser(UUID userId) {
        User user = helper.getCurrentUser().orElse(null);
        if (user == null) {
            return false;
        }
        return resolveUser(policy.canRead(user, userId, membershipsOf(userId)), userId);
    }

    @Override
    public boolean canEditUser(UUID userId) {
        User user = helper.getCurrentUser().orElse(null);
        if (user == null) {
            return false;
        }
        return resolveUser(policy.canEdit(user, userId, membershipsOf(userId)), userId);
    }

    @Override
    public boolean canListSuppliesOfUser(UUID userId) {
        User user = helper.getCurrentUser().orElse(null);
        if (user == null) {
            return false;
        }
        return resolveUser(policy.canListSuppliesOf(user, userId, membershipsOf(userId)), userId);
    }

    @Override
    public boolean canDeleteUser(UUID userId) {
        return canEditSomeoneElse(userId);
    }

    @Override
    public boolean canEnableUser(UUID userId) {
        return canEditSomeoneElse(userId);
    }

    @Override
    public boolean canDisableUser(UUID userId) {
        return canEditSomeoneElse(userId);
    }

    @Override
    public boolean canCreateUserIn(UUID communityId) {
        User user = helper.getCurrentUser().orElse(null);
        if (user == null) {
            return false;
        }
        AccessDecision decision = policy.canCreateIn(user, communityId);
        if (decision == AccessDecision.NOT_VISIBLE) {
            throw new CommunityNotFoundException(communityId);
        }
        return decision.isAllowed();
    }

    @Override
    public boolean canListUsers() {
        User user = helper.getCurrentUser().orElse(null);
        if (user == null) {
            return false;
        }
        // Never throws: there is no object whose existence could leak.
        return policy.canList(user).isAllowed();
    }

    /**
     * The edit decision first, then the self check -- the order the SpEL's short-circuiting {@code and}
     * had, so a caller who cannot see the target still gets a 404 and a platform admin acting on
     * themselves still gets a 403.
     */
    private boolean canEditSomeoneElse(UUID userId) {
        User user = helper.getCurrentUser().orElse(null);
        if (user == null) {
            return false;
        }
        if (!resolveUser(policy.canEdit(user, userId, membershipsOf(userId)), userId)) {
            return false;
        }
        return !CallerMemberships.isCurrentUser(user, userId);
    }

    private boolean resolveUser(AccessDecision decision, UUID userId) {
        if (decision == AccessDecision.NOT_VISIBLE) {
            throw new UserNotFoundException(UserId.of(userId));
        }
        return decision.isAllowed();
    }

    /**
     * The target user's memberships, fetched at most once and only if a branch asks for them —
     * a platform admin and a self-read never do.
     */
    private Supplier<List<CommunityMembership>> membershipsOf(UUID userId) {
        return new Supplier<>() {
            private List<CommunityMembership> memberships;

            @Override
            public List<CommunityMembership> get() {
                if (memberships == null) {
                    memberships = getMembershipsRepository.findByUserId(userId);
                }
                return memberships;
            }
        };
    }
}

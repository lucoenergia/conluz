package org.lucoenergia.conluz.infrastructure.admin.community.access;

import org.lucoenergia.conluz.domain.admin.community.CommunityNotFoundException;
import org.lucoenergia.conluz.domain.admin.community.access.MembershipAccessGuard;
import org.lucoenergia.conluz.domain.admin.community.access.policy.AccessDecision;
import org.lucoenergia.conluz.domain.admin.community.access.policy.MembershipAccessPolicy;
import org.lucoenergia.conluz.domain.admin.user.User;

import java.util.UUID;

/**
 * Adapter over {@link MembershipAccessPolicy}: {@code NOT_VISIBLE} becomes
 * {@link CommunityNotFoundException} (404), {@code FORBIDDEN} becomes {@code false} (403).
 */
class MembershipAccessGuardImpl implements MembershipAccessGuard {

    private final CommunityAccessGuardHelper helper;
    private final MembershipAccessPolicy policy;

    public MembershipAccessGuardImpl(CommunityAccessGuardHelper helper, MembershipAccessPolicy policy) {
        this.helper = helper;
        this.policy = policy;
    }

    @Override
    public boolean canManageMemberships(UUID communityId) {
        User user = helper.getCurrentUser().orElse(null);
        if (user == null) {
            return false;
        }
        return resolve(policy.canManageMemberships(user, communityId), communityId);
    }

    @Override
    public boolean canManageMembershipInvestment(UUID communityId) {
        User user = helper.getCurrentUser().orElse(null);
        if (user == null) {
            return false;
        }
        return resolve(policy.canManageInvestment(user, communityId), communityId);
    }

    @Override
    public boolean canReadMembershipPayback(UUID communityId, UUID userId) {
        User user = helper.getCurrentUser().orElse(null);
        if (user == null) {
            return false;
        }
        return resolve(policy.canReadPayback(user, communityId, userId), communityId);
    }

    private boolean resolve(AccessDecision decision, UUID communityId) {
        if (decision == AccessDecision.NOT_VISIBLE) {
            throw new CommunityNotFoundException(communityId);
        }
        return decision.isAllowed();
    }
}

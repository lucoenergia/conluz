package org.lucoenergia.conluz.infrastructure.admin.community.access;

import org.lucoenergia.conluz.domain.admin.community.CommunityNotFoundException;
import org.lucoenergia.conluz.domain.admin.community.MembershipNotFoundException;
import org.lucoenergia.conluz.domain.admin.community.access.MembershipAccessGuard;
import org.lucoenergia.conluz.domain.admin.community.access.policy.AccessDecision;
import org.lucoenergia.conluz.domain.admin.community.access.policy.MembershipAccessPolicy;
import org.lucoenergia.conluz.domain.admin.community.membership.GetMembershipsRepository;
import org.lucoenergia.conluz.domain.admin.user.User;

import java.util.UUID;

/**
 * Adapter over {@link MembershipAccessPolicy}: {@code NOT_VISIBLE} becomes
 * {@link CommunityNotFoundException} (404), or {@link MembershipNotFoundException} (404) when it is the membership
 * that is not visible, and {@code FORBIDDEN} becomes {@code false} (403).
 */
class MembershipAccessGuardImpl implements MembershipAccessGuard {

    private final CommunityAccessGuardHelper helper;
    private final GetMembershipsRepository getMembershipsRepository;
    private final MembershipAccessPolicy policy;

    public MembershipAccessGuardImpl(CommunityAccessGuardHelper helper,
                                     GetMembershipsRepository getMembershipsRepository,
                                     MembershipAccessPolicy policy) {
        this.helper = helper;
        this.getMembershipsRepository = getMembershipsRepository;
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
    public boolean canRegisterMembers(UUID communityId) {
        User user = helper.getCurrentUser().orElse(null);
        if (user == null) {
            return false;
        }
        return resolve(policy.canRegisterMembers(user, communityId), communityId);
    }

    @Override
    public boolean canInviteMembers(UUID communityId) {
        User user = helper.getCurrentUser().orElse(null);
        if (user == null) {
            return false;
        }
        return resolve(policy.canInviteMembers(user, communityId), communityId);
    }

    @Override
    public boolean canChangeMemberState(UUID communityId, UUID userId) {
        User user = helper.getCurrentUser().orElse(null);
        if (user == null) {
            return false;
        }
        // Settled on the community first, so that not-visible there is told apart from not-visible on the membership
        AccessDecision community = policy.canChangeMemberStates(user, communityId);
        if (community != AccessDecision.ALLOWED) {
            return resolve(community, communityId);
        }
        AccessDecision decision = policy.canChangeMemberState(user, communityId,
                () -> getMembershipsRepository.findByUserIdAndCommunityId(userId, communityId).orElse(null));
        if (decision == AccessDecision.NOT_VISIBLE) {
            throw new MembershipNotFoundException(communityId, userId);
        }
        return decision.isAllowed();
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
    public boolean canReadMembershipPrivateData(UUID communityId, UUID userId) {
        User user = helper.getCurrentUser().orElse(null);
        if (user == null) {
            return false;
        }
        return resolve(policy.canReadMembershipPrivateData(user, communityId, userId), communityId);
    }

    private boolean resolve(AccessDecision decision, UUID communityId) {
        if (decision == AccessDecision.NOT_VISIBLE) {
            throw new CommunityNotFoundException(communityId);
        }
        return decision.isAllowed();
    }
}

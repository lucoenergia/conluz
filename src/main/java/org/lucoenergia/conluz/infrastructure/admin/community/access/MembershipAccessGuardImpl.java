package org.lucoenergia.conluz.infrastructure.admin.community.access;

import org.lucoenergia.conluz.domain.admin.community.CommunityNotFoundException;
import org.lucoenergia.conluz.domain.admin.community.access.MembershipAccessGuard;
import org.lucoenergia.conluz.domain.admin.user.User;

import java.util.UUID;

class MembershipAccessGuardImpl implements MembershipAccessGuard {

    private final CommunityAccessGuardHelper helper;

    public MembershipAccessGuardImpl(CommunityAccessGuardHelper helper) {
        this.helper = helper;
    }

    @Override
    public boolean canManageMemberships(UUID communityId) {
        User user = helper.getCurrentUser().orElse(null);
        if (user == null) {
            return false;
        }
        if (!helper.canSeeCommunity(user, communityId)) {
            throw new CommunityNotFoundException(communityId);
        }
        if (Boolean.TRUE.equals(user.isPlatformAdmin())) {
            return true;
        }
        return helper.hasCommunityAdminRoleIn(user, communityId);
    }

    @Override
    public boolean canManageMembershipInvestment(UUID communityId) {
        User user = helper.getCurrentUser().orElse(null);
        if (user == null || communityId == null) {
            return false;
        }
        // No platform-admin bypass, deliberately, and no 403 branch: anyone who is not a community
        // admin here is told the community is not there rather than that they may not touch it.
        if (!helper.hasCommunityAdminRoleIn(user, communityId)) {
            throw new CommunityNotFoundException(communityId);
        }
        return true;
    }
}

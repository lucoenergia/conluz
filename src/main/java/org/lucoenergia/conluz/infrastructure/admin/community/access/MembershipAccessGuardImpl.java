package org.lucoenergia.conluz.infrastructure.admin.community.access;

import org.lucoenergia.conluz.domain.admin.community.access.MembershipAccessGuard;
import org.lucoenergia.conluz.domain.admin.user.User;

import java.util.UUID;

public class MembershipAccessGuardImpl implements MembershipAccessGuard {

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
        if (Boolean.TRUE.equals(user.isPlatformAdmin())) {
            return true;
        }
        return helper.hasCommunityAdminRoleIn(user, communityId);
    }
}

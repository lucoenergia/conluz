package org.lucoenergia.conluz.infrastructure.admin.community.access;

import org.lucoenergia.conluz.domain.admin.community.access.PlatformAccessGuard;
import org.lucoenergia.conluz.domain.admin.community.access.policy.PlatformAccessPolicy;
import org.lucoenergia.conluz.domain.admin.user.User;

import java.util.UUID;

/**
 * Adapter over {@link PlatformAccessPolicy}. Unlike its siblings it has no exception mapping to do:
 * the policy never answers not-visible, so every denial is simply {@code false} — which is exactly
 * what {@code hasRole('PLATFORM_ADMIN')} did before these methods replaced it.
 */
class PlatformAccessGuardImpl implements PlatformAccessGuard {

    private final CommunityAccessGuardHelper helper;
    private final PlatformAccessPolicy policy;

    public PlatformAccessGuardImpl(CommunityAccessGuardHelper helper, PlatformAccessPolicy policy) {
        this.helper = helper;
        this.policy = policy;
    }

    @Override
    public boolean canCreateCommunity() {
        return administersPlatform();
    }

    @Override
    public boolean canUpdateCommunity(UUID communityId) {
        return administersPlatform();
    }

    @Override
    public boolean canEnableCommunity(UUID communityId) {
        return administersPlatform();
    }

    @Override
    public boolean canDisableCommunity(UUID communityId) {
        return administersPlatform();
    }

    @Override
    public boolean canGrantPlatformAdmin(UUID userId) {
        return administersPlatform();
    }

    @Override
    public boolean canRevokePlatformAdmin(UUID userId) {
        User user = helper.getCurrentUser().orElse(null);
        if (user == null) {
            return false;
        }
        return policy.canAdministerOtherUser(user, userId).isAllowed();
    }

    private boolean administersPlatform() {
        User user = helper.getCurrentUser().orElse(null);
        if (user == null) {
            return false;
        }
        return policy.canAdministerPlatform(user).isAllowed();
    }
}

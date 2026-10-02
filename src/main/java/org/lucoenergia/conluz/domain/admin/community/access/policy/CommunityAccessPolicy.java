package org.lucoenergia.conluz.domain.admin.community.access.policy;

import org.lucoenergia.conluz.domain.admin.user.User;

import java.util.UUID;

/**
 * Access rules for a community itself. Pure: the caller is already resolved and the community is
 * identified by id, so nothing is loaded and nothing is thrown.
 */
public class CommunityAccessPolicy {

    /**
     * Reading a community is open to anyone who can see it — every platform admin, and every
     * enabled member.
     */
    public AccessDecision canRead(User caller, UUID communityId) {
        if (!CallerMemberships.canSeeCommunity(caller, communityId)) {
            return AccessDecision.NOT_VISIBLE;
        }
        return AccessDecision.ALLOWED;
    }

    /**
     * Stricter than {@link #canRead}: a platform admin who is not a member can see the community but
     * is not one of its members, so they are forbidden rather than not-found. Used for communal data
     * only members may read.
     */
    public AccessDecision isMember(User caller, UUID communityId) {
        if (!CallerMemberships.canSeeCommunity(caller, communityId)) {
            return AccessDecision.NOT_VISIBLE;
        }
        return CallerMemberships.hasMembershipInCommunity(caller, communityId)
                ? AccessDecision.ALLOWED
                : AccessDecision.FORBIDDEN;
    }

    /**
     * Managing a community belongs to its enabled community admins. A platform admin who is not one
     * can see the community, so they are forbidden rather than not-found.
     */
    public AccessDecision canManage(User caller, UUID communityId) {
        if (!CallerMemberships.canSeeCommunity(caller, communityId)) {
            return AccessDecision.NOT_VISIBLE;
        }
        return CallerMemberships.hasCommunityAdminRoleIn(caller, communityId)
                ? AccessDecision.ALLOWED
                : AccessDecision.FORBIDDEN;
    }
}

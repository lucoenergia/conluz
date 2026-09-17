package org.lucoenergia.conluz.domain.admin.community.access.policy;

import org.lucoenergia.conluz.domain.admin.user.User;

import java.util.UUID;

/**
 * Access rules for the memberships of a community, and for the personal financial data hanging off
 * one. Pure: nothing is loaded and nothing is thrown.
 */
public class MembershipAccessPolicy {

    /**
     * Administering the roster of a community: its enabled community admins, plus any platform
     * admin. A member can see the community, so they are forbidden rather than not-found.
     */
    public AccessDecision canManageMemberships(User caller, UUID communityId) {
        if (!CallerMemberships.canSeeCommunity(caller, communityId)) {
            return AccessDecision.NOT_VISIBLE;
        }
        if (CallerMemberships.isPlatformAdmin(caller)) {
            return AccessDecision.ALLOWED;
        }
        return CallerMemberships.hasCommunityAdminRoleIn(caller, communityId)
                ? AccessDecision.ALLOWED
                : AccessDecision.FORBIDDEN;
    }

    /**
     * Setting or clearing the investment recorded on a membership. Stricter than
     * {@link #canManageMemberships}: an investment is the member's own money, and administering the
     * platform is not the same as administering this community's finances, so there is no
     * platform-admin bypass.
     *
     * <p>There is deliberately no {@link AccessDecision#FORBIDDEN} outcome for a caller who merely
     * lacks the role: a 403 would tell them an investment exists here to be written. The only
     * forbidden case is a missing community id, which is not a statement about any community.</p>
     */
    public AccessDecision canManageInvestment(User caller, UUID communityId) {
        if (caller == null || communityId == null) {
            return AccessDecision.FORBIDDEN;
        }
        return CallerMemberships.hasCommunityAdminRoleIn(caller, communityId)
                ? AccessDecision.ALLOWED
                : AccessDecision.NOT_VISIBLE;
    }

    /**
     * Reading the payback progress of a membership: either the caller is {@code userId} themselves
     * <em>and</em> holds an enabled membership in the community, or they are one of its enabled
     * community admins.
     *
     * <p>The self branch requires an enabled membership, not merely being the named user: a disabled
     * membership already makes its community invisible to its holder everywhere else, and payback
     * must not be the one endpoint where it does not. As with
     * {@link #canManageInvestment}, there is no forbidden-by-role outcome — and no platform-admin
     * bypass, so a platform admin reaches their own payback through the self branch like anyone
     * else.</p>
     *
     * <p>Whether the membership exists is not decided here; the service performs that lookup.</p>
     */
    public AccessDecision canReadPayback(User caller, UUID communityId, UUID userId) {
        if (caller == null || communityId == null || userId == null) {
            return AccessDecision.FORBIDDEN;
        }
        boolean self = CallerMemberships.isCurrentUser(caller, userId)
                && CallerMemberships.hasMembershipInCommunity(caller, communityId);
        boolean communityAdmin = CallerMemberships.hasCommunityAdminRoleIn(caller, communityId);

        return self || communityAdmin ? AccessDecision.ALLOWED : AccessDecision.NOT_VISIBLE;
    }
}

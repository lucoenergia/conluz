package org.lucoenergia.conluz.domain.admin.community.access.policy;

import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.user.User;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Access rules for the memberships of a community, and for the personal financial data hanging off
 * one. Pure: nothing is loaded and nothing is thrown.
 */
public class MembershipAccessPolicy {

    /**
     * Administering the roster of a community: only its active community admins. There is no platform-admin bypass
     * (#446): the roster carries the members' records and personal IDs, which only the community's own administration
     * reads; the platform admin reaches a community's admins through {@code …/admins}. A member, or a platform admin
     * who is not an admin of the community, can see the community, so they are forbidden rather than not-found.
     */
    public AccessDecision canManageMemberships(User caller, UUID communityId) {
        if (!CallerMemberships.canSeeCommunity(caller, communityId)) {
            return AccessDecision.NOT_VISIBLE;
        }
        return CallerMemberships.hasCommunityAdminRoleIn(caller, communityId)
                ? AccessDecision.ALLOWED
                : AccessDecision.FORBIDDEN;
    }

    /**
     * Registering a member in a community: only its active community admins. There is no platform-admin
     * bypass: registering creates a membership, and possibly an account, from personal data that only the
     * community's own administration enters (ADR-0003 §3). A member can see the community, so they are
     * forbidden rather than not-found.
     */
    public AccessDecision canRegisterMembers(User caller, UUID communityId) {
        if (!CallerMemberships.canSeeCommunity(caller, communityId)) {
            return AccessDecision.NOT_VISIBLE;
        }
        return CallerMemberships.hasCommunityAdminRoleIn(caller, communityId)
                ? AccessDecision.ALLOWED
                : AccessDecision.FORBIDDEN;
    }

    /**
     * Inviting a registered member to accept their membership: the same people who may register members, for the
     * same reason. The invitation goes to the contact email the community entered in its own member record.
     */
    public AccessDecision canInviteMembers(User caller, UUID communityId) {
        return canRegisterMembers(caller, communityId);
    }

    /**
     * Enabling and disabling memberships of a community, as far as the community alone decides it: only its active
     * community admins, with no platform-admin bypass. Disabling a member is a decision of the community, taken on
     * the membership, not on the account the person shares with their other communities (#415). A member can see the
     * community, so they are forbidden rather than not-found.
     *
     * <p>The membership itself is decided by {@link #canChangeMemberState}, which starts here.</p>
     */
    public AccessDecision canChangeMemberStates(User caller, UUID communityId) {
        if (!CallerMemberships.canSeeCommunity(caller, communityId)) {
            return AccessDecision.NOT_VISIBLE;
        }
        return CallerMemberships.hasCommunityAdminRoleIn(caller, communityId)
                ? AccessDecision.ALLOWED
                : AccessDecision.FORBIDDEN;
    }

    /**
     * Enabling or disabling one membership: {@link #canChangeMemberStates} for its community, and then only a
     * membership with the {@code COMMUNITY_MEMBER} role, pending or accepted, enabled or not. A membership with any
     * other role is not visible, as one that does not exist: admin memberships are the platform admin's to suspend
     * (#395).
     *
     * <p>The target is the user's membership of {@code communityId}, {@code null} when they have none. It arrives as
     * a {@link Supplier} so that a caller refused on the community never pays for loading it; a capability assembler
     * passes the membership it already holds.</p>
     */
    public AccessDecision canChangeMemberState(User caller, UUID communityId,
                                               Supplier<CommunityMembership> targetMembership) {
        AccessDecision community = canChangeMemberStates(caller, communityId);
        if (community != AccessDecision.ALLOWED) {
            return community;
        }
        CommunityMembership target = targetMembership.get();
        return target != null && target.getRole() == CommunityRole.COMMUNITY_MEMBER
                ? AccessDecision.ALLOWED
                : AccessDecision.NOT_VISIBLE;
    }

    /**
     * Editing the member records of a community: only its active community admins, with no platform-admin bypass.
     * A member record is what the community itself knows about a member (ADR-0003 §1), so only the community's own
     * administration changes it. Any membership of the community may be edited, whatever its role and state, so the
     * target plays no part here. A member can see the community, so they are forbidden rather than not-found.
     */
    public AccessDecision canEditMemberRecords(User caller, UUID communityId) {
        if (!CallerMemberships.canSeeCommunity(caller, communityId)) {
            return AccessDecision.NOT_VISIBLE;
        }
        return CallerMemberships.hasCommunityAdminRoleIn(caller, communityId)
                ? AccessDecision.ALLOWED
                : AccessDecision.FORBIDDEN;
    }

    /**
     * Setting or clearing the investment recorded on a membership: only the community's active admins.
     * An investment is the member's own money, and administering the platform is not the same as
     * administering this community's finances, so there is no platform-admin bypass.
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
     * Reading the private data of a membership (its payback progress and its energy metrics): either the caller is {@code userId} themselves
     * <em>and</em> holds an active membership in the community, or they are one of its active
     * community admins.
     *
     * <p>The self branch requires an active membership, not merely being the named user: a pending or disabled
     * membership already makes its community invisible to its holder everywhere else, and a
     * membership's private figures must not be the one place where it does not. As with
     * {@link #canManageInvestment}, there is no forbidden-by-role outcome — and no platform-admin
     * bypass, so a platform admin reaches their own private data through the self branch like anyone
     * else.</p>
     *
     * <p>Whether the membership exists is not decided here; the service performs that lookup.</p>
     */
    public AccessDecision canReadMembershipPrivateData(User caller, UUID communityId, UUID userId) {
        if (caller == null || communityId == null || userId == null) {
            return AccessDecision.FORBIDDEN;
        }
        boolean self = CallerMemberships.isCurrentUser(caller, userId)
                && CallerMemberships.hasMembershipInCommunity(caller, communityId);
        boolean communityAdmin = CallerMemberships.hasCommunityAdminRoleIn(caller, communityId);

        return self || communityAdmin ? AccessDecision.ALLOWED : AccessDecision.NOT_VISIBLE;
    }
}

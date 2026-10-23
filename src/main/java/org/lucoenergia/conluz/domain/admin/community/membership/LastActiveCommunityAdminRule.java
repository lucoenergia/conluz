package org.lucoenergia.conluz.domain.admin.community.membership;

import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;

import java.util.Collection;
import java.util.function.LongSupplier;

/**
 * The safety rail that keeps a community from being left without an admin who can act for it (MEM-033). Demoting an
 * admin, deleting their membership, and telling a client whether either would be accepted all read it from here, so
 * they cannot disagree.
 */
public final class LastActiveCommunityAdminRule {

    private LastActiveCommunityAdminRule() {
    }

    /**
     * Whether the membership counts towards the rail: an admin membership that is active, held by an account that is
     * enabled. A pending or suspended admin, or one whose account is disabled, cannot act for the community.
     */
    public static boolean countsAsActiveAdmin(CommunityMembership membership) {
        return membership.getRole() == CommunityRole.COMMUNITY_ADMIN
                && membership.isActive()
                && membership.getUser().isEnabled();
    }

    /**
     * How many of the given memberships of one community count towards the rail.
     */
    public static long activeAdmins(Collection<CommunityMembership> memberships) {
        return memberships.stream().filter(LastActiveCommunityAdminRule::countsAsActiveAdmin).count();
    }

    /**
     * Demoting or deleting a membership that does not count towards the rail leaves the number of active admins as
     * it is, so the count is read only when the target counts.
     *
     * @param target       the membership that would be demoted or deleted
     * @param activeAdmins how many memberships of the target's community count towards the rail
     * @return whether demoting or deleting {@code target} must be refused with
     * {@link org.lucoenergia.conluz.domain.admin.community.MembershipLastActiveAdminException}
     */
    public static boolean blocksRemoval(CommunityMembership target, LongSupplier activeAdmins) {
        return countsAsActiveAdmin(target) && activeAdmins.getAsLong() <= 1;
    }
}

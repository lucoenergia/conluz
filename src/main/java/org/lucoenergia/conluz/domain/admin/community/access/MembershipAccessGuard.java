package org.lucoenergia.conluz.domain.admin.community.access;

import java.util.UUID;

public interface MembershipAccessGuard {

    boolean canManageMemberships(UUID communityId);

    /**
     * Whether the current user may set or clear the investment recorded on a membership of this
     * community. Stricter than {@link #canManageMemberships(UUID)}, which lets any platform admin
     * through: an investment is the member's own money, and administering the platform is not the
     * same as administering this community's finances. Only an enabled {@code COMMUNITY_ADMIN} of
     * {@code communityId} qualifies.
     *
     * <p>Maps denials by visibility, never by role:
     * <ul>
     *     <li>anonymous → {@code false} (→ 401),</li>
     *     <li>anyone who is not a community admin here, including a platform admin who is not →
     *         throws {@code CommunityNotFoundException} (→ 404).</li>
     * </ul>
     * There is no 403 outcome. A caller who cannot administer this community has no business
     * learning that an investment is recorded on one of its memberships, and a 403 would tell
     * them the membership exists.
     *
     * <p>Whether the membership itself exists is not decided here: the service performs that
     * lookup and answers 404 on its own. This method answers only "may this caller write an
     * investment in this community".
     */
    boolean canManageMembershipInvestment(UUID communityId);

    /**
     * Whether the current user may read the payback progress of this membership: either they are
     * {@code userId} themselves and hold an enabled membership in {@code communityId}, or they are
     * an enabled {@code COMMUNITY_ADMIN} of it.
     *
     * <p>Deliberately not expressed through {@code canReadCommunity}, which grants every platform
     * admin access to every community. Payback is a member's own financial position, so
     * administering the platform does not confer it; a platform admin reaches their own payback
     * here through the self branch, like anyone else.
     *
     * <p>Maps denials by visibility, never by role:
     * <ul>
     *     <li>anonymous → {@code false} (→ 401),</li>
     *     <li>everyone else who qualifies for neither branch → throws
     *         {@code CommunityNotFoundException} (→ 404).</li>
     * </ul>
     * There is no 403 outcome, following {@code canReadSupply}: a 403 would tell a caller who may
     * not read this membership that it nevertheless exists.
     *
     * <p>Authorization only. Whether the membership exists is the service's lookup, so the two
     * concerns do not have to be kept in step in two places.
     */
    boolean canReadMembershipPayback(UUID communityId, UUID userId);
}

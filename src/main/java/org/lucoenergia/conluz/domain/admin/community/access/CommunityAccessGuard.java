package org.lucoenergia.conluz.domain.admin.community.access;

import java.util.Set;
import java.util.UUID;

public interface CommunityAccessGuard extends
        SupplyAccessGuard,
        MembershipAccessGuard,
        UserAccessGuard,
        PlantAccessGuard,
        PlatformAccessGuard {

    boolean canReadCommunity(UUID communityId);

    /**
     * Whether the current user is an active member of the community. Stricter than
     * {@link #canReadCommunity(UUID)}: a platform admin who is not a member is <em>not</em> granted
     * access. Maps denials by visibility:
     * <ul>
     *     <li>anonymous → {@code false} (→ 401),</li>
     *     <li>authenticated but cannot see the community → throws {@code CommunityNotFoundException}
     *         (→ 404), so its existence is not leaked,</li>
     *     <li>can see the community but is not a member (e.g. a non-member platform admin) →
     *         {@code false} (→ 403),</li>
     *     <li>active member → {@code true} (→ 200).</li>
     * </ul>
     * Used for communal data (e.g. Datadis production) that only members of the community may read.
     */
    boolean isMemberOfCommunity(UUID communityId);

    /**
     * Whether the current user may read the community's communal production data. The same decision
     * as {@link #isMemberOfCommunity(UUID)}, and so the same 401/404/403/200 mapping; named for the
     * endpoints it guards so the decision can be reported as a capability on the community.
     */
    boolean canReadCommunityProduction(UUID communityId);

    /**
     * Whether the current user may list the community's supplies. Also the same decision as
     * {@link #isMemberOfCommunity(UUID)}; kept apart from
     * {@link #canReadCommunityProduction(UUID)} because listing supplies and reading production are
     * different things to be allowed to do, however alike the rule behind them is today.
     */
    boolean canListSupplies(UUID communityId);

    boolean canManageCommunity(UUID communityId);

    /**
     * Whether the current user may replace the community's contact channels: they are an active
     * {@code COMMUNITY_ADMIN} of it (CTC-002). Maps denials by visibility:
     * <ul>
     *     <li>anonymous → {@code false} (→ 401),</li>
     *     <li>not an active member of the community, a platform admin included → throws
     *         {@code CommunityNotFoundException} (→ 404),</li>
     *     <li>an active member who is not a community admin → {@code false} (→ 403).</li>
     * </ul>
     * Allows the same callers as {@link #canManageCommunity(UUID)}, so it is reported by the community's
     * {@code canManage} capability; it differs only in answering 404 rather than 403 to a platform admin who is not a
     * member.
     */
    boolean canManageCommunityContact(UUID communityId);

    Set<UUID> visibleCommunityIds();

    /**
     * Community ids the current user administers. Returns the set of community ids where the user holds an enabled
     * {@code COMMUNITY_ADMIN} membership (possibly empty).
     */
    Set<UUID> adminCommunityIds();

    /**
     * @return true when the authenticated user's id equals {@code userId}.
     */
    boolean isCurrentUser(UUID userId);
}

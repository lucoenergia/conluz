package org.lucoenergia.conluz.domain.admin.community.access;

import java.util.Set;
import java.util.UUID;

public interface CommunityAccessGuard extends
        SupplyAccessGuard,
        MembershipAccessGuard,
        UserAccessGuard,
        PlantAccessGuard {

    boolean canReadCommunity(UUID communityId);

    /**
     * Whether the current user is an enabled member of the community. Stricter than
     * {@link #canReadCommunity(UUID)}: a platform admin who is not a member is <em>not</em> granted
     * access. Maps denials by visibility:
     * <ul>
     *     <li>anonymous → {@code false} (→ 401),</li>
     *     <li>authenticated but cannot see the community → throws {@code CommunityNotFoundException}
     *         (→ 404), so its existence is not leaked,</li>
     *     <li>can see the community but is not a member (e.g. a non-member platform admin) →
     *         {@code false} (→ 403),</li>
     *     <li>enabled member → {@code true} (→ 200).</li>
     * </ul>
     * Used for communal data (e.g. Datadis production) that only members of the community may read.
     */
    boolean isMemberOfCommunity(UUID communityId);

    boolean canManageCommunity(UUID communityId);

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

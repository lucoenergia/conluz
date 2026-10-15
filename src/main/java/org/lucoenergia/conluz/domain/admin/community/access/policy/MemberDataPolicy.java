package org.lucoenergia.conluz.domain.admin.community.access.policy;

import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.membership.MemberData;
import org.lucoenergia.conluz.domain.admin.user.User;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Where the member data a caller reads about a user comes from (MEM-026, ADR-0003 §1).
 *
 * <p>Like {@link UserAccessPolicy}, the target's memberships arrive as a {@link Supplier}, so the branches that
 * read the account never pay for loading them.</p>
 */
public class MemberDataPolicy {

    /**
     * Communities are compared by the canonical text of their id, the order PostgreSQL gives {@code uuid}.
     * {@link UUID#compareTo} compares signed longs and would disagree for ids whose first hex digit is 8 or more.
     */
    private static final Comparator<CommunityMembership> LOWEST_COMMUNITY_ID =
            Comparator.comparing(m -> m.getCommunity().getId().toString());

    /**
     * A user embedded in a response scoped to one community shows their member record in that community, whoever
     * the caller is.
     *
     * @param memberRecord the user's membership in that community, or {@code null} if they have none
     */
    public MemberData inCommunity(CommunityMembership memberRecord) {
        return memberRecord != null ? MemberData.ofRecord(memberRecord) : MemberData.absent();
    }

    /**
     * A user read outside any community shows their account to themselves and to the platform admin. Any other
     * caller sees the user's member record in a community the caller administers, the one with the lowest id when
     * there are several, and no member data when there is none.
     */
    public MemberData forUser(User caller, User target, Supplier<List<CommunityMembership>> targetMemberships) {
        if (CallerMemberships.isCurrentUser(caller, target.getId()) || CallerMemberships.isPlatformAdmin(caller)) {
            return MemberData.ofAccount(target);
        }
        Set<UUID> administered = CallerMemberships.adminCommunityIds(caller);
        if (administered.isEmpty()) {
            return MemberData.absent();
        }
        List<CommunityMembership> memberships = targetMemberships.get();
        if (memberships == null) {
            return MemberData.absent();
        }
        return memberships.stream()
                .filter(m -> m.getCommunity() != null && administered.contains(m.getCommunity().getId()))
                .min(LOWEST_COMMUNITY_ID)
                .map(MemberData::ofRecord)
                .orElse(MemberData.absent());
    }
}

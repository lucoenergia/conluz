package org.lucoenergia.conluz.domain.admin.community.membership;

import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.shared.UserId;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface GetMembershipsRepository {

    List<CommunityMembership> findByCommunityId(UUID communityId);

    List<CommunityMembership> findByUserId(UUID userId);

    /**
     * The memberships of a community with the given role, in every state, with their accounts, by full name and then
     * user id.
     *
     * @throws org.lucoenergia.conluz.domain.admin.community.CommunityNotFoundException if the community does not
     *         exist
     */
    List<CommunityMembership> findByCommunityIdAndRole(UUID communityId, CommunityRole role);

    /**
     * The one membership of a user in a community, if it exists. Empty is the ordinary answer for
     * a user who simply does not belong to that community, not an error.
     */
    Optional<CommunityMembership> findByUserIdAndCommunityId(UUID userId, UUID communityId);

    /**
     * Retrieves the memberships of the given users grouped by user ID, using a single batch
     * query to avoid N+1 lookups. Users without memberships (or missing IDs) are simply absent
     * from the resulting map.
     *
     * @param userIds the user IDs to load memberships for
     * @return a map from user ID to the user's memberships
     */
    Map<UUID, List<CommunityMembership>> findByUserIds(Collection<UUID> userIds);

    /**
     * The users whose memberships of the community are eligible for a first invitation: awaiting acceptance, with a
     * contact email, and never invited. In ascending user id, the order their rows are locked in.
     *
     * @param limit the most to return
     */
    List<UserId> findFirstInvitationCandidates(UUID communityId, int limit);

    /**
     * Counts the memberships of the community eligible for a first invitation, as
     * {@link #findFirstInvitationCandidates} selects them.
     */
    long countFirstInvitationCandidates(UUID communityId);
}

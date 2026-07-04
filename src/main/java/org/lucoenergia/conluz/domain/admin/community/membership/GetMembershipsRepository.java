package org.lucoenergia.conluz.domain.admin.community.membership;

import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface GetMembershipsRepository {

    List<CommunityMembership> findByCommunityId(UUID communityId);

    List<CommunityMembership> findByUserId(UUID userId);

    /**
     * Retrieves the memberships of the given users grouped by user ID, using a single batch
     * query to avoid N+1 lookups. Users without memberships (or missing IDs) are simply absent
     * from the resulting map.
     *
     * @param userIds the user IDs to load memberships for
     * @return a map from user ID to the user's memberships
     */
    Map<UUID, List<CommunityMembership>> findByUserIds(Collection<UUID> userIds);
}

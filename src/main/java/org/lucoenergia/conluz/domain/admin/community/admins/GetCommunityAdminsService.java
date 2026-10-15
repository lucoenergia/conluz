package org.lucoenergia.conluz.domain.admin.community.admins;

import java.util.List;
import java.util.UUID;

/**
 * The memberships of a community with the community admin role, and only those (ADR-0003 §3): nothing about its
 * members, and nothing from any other membership of the same people.
 */
public interface GetCommunityAdminsService {

    /**
     * @return the community's admin memberships, in every state, by full name
     * @throws org.lucoenergia.conluz.domain.admin.community.CommunityNotFoundException if the community does not
     *         exist
     */
    List<CommunityAdmin> findByCommunityId(UUID communityId);
}

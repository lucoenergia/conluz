package org.lucoenergia.conluz.domain.admin.community.membership;

import org.lucoenergia.conluz.domain.shared.UserId;

import java.util.UUID;

public interface DeleteMembershipService {

    /**
     * @param deletedBy the community admin deleting it
     */
    void delete(UUID communityId, UUID userId, UserId deletedBy);
}

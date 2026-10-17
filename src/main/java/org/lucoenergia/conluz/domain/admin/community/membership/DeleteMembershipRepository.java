package org.lucoenergia.conluz.domain.admin.community.membership;

import org.lucoenergia.conluz.domain.shared.UserId;

import java.util.UUID;

public interface DeleteMembershipRepository {

    void delete(UUID communityId, UUID userId);

    /**
     * Deletes every membership of the user, with their member records and invitation tokens. Deleting none is not
     * an error.
     */
    void deleteByUser(UserId userId);
}

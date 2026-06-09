package org.lucoenergia.conluz.domain.admin.community.membership;

import java.util.UUID;

public interface DeleteMembershipRepository {

    void delete(UUID communityId, UUID userId);
}

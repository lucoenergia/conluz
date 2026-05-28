package org.lucoenergia.conluz.domain.admin.community.membership;

import java.util.UUID;

public interface DeleteMembershipService {

    void delete(UUID communityId, UUID userId);
}

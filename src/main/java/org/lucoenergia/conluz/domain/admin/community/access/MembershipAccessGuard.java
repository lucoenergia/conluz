package org.lucoenergia.conluz.domain.admin.community.access;

import java.util.UUID;

public interface MembershipAccessGuard {

    boolean canManageMemberships(UUID communityId);
}

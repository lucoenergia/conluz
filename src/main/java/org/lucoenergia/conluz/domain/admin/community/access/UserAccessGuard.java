package org.lucoenergia.conluz.domain.admin.community.access;

import java.util.UUID;

public interface UserAccessGuard {

    boolean canReadUser(UUID userId);

    boolean canEditUser(UUID userId);

    boolean canCreateUserIn(UUID communityId);

    boolean canListUsers();
}

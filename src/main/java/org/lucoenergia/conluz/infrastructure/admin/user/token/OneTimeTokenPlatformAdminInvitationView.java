package org.lucoenergia.conluz.infrastructure.admin.user.token;

import org.lucoenergia.conluz.domain.admin.user.token.OneTimeTokenPlatformAdminInvitation;
import org.lucoenergia.conluz.domain.shared.UserId;

import java.util.UUID;

public interface OneTimeTokenPlatformAdminInvitationView {

    UUID getInvitationId();

    UUID getUserId();

    default OneTimeTokenPlatformAdminInvitation toDomain() {
        return new OneTimeTokenPlatformAdminInvitation(getInvitationId(), UserId.of(getUserId()));
    }
}

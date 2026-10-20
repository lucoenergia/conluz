package org.lucoenergia.conluz.infrastructure.admin.user.platformadmin.invitation;

import org.lucoenergia.conluz.domain.admin.user.platformadmin.PendingPlatformAdmin;

import java.time.Instant;
import java.util.UUID;

public interface PendingPlatformAdminView {

    UUID getUserId();

    String getFullName();

    String getEmail();

    Instant getInvitedAt();

    Instant getLinkExpiresAt();

    default PendingPlatformAdmin toDomain() {
        return new PendingPlatformAdmin(getUserId(), getFullName(), getEmail(), getInvitedAt(), getLinkExpiresAt());
    }
}

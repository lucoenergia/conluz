package org.lucoenergia.conluz.infrastructure.admin.user.platformadmin;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.PendingPlatformAdmin;

import java.time.Instant;

/**
 * The state of a pending invitation to the platform-admin role. Never carries its confirmation code.
 */
@Schema(description = "A pending invitation to the platform-admin role.",
        requiredProperties = {"invitedAt", "expiresAt"})
public class PlatformAdminInvitationResponse {

    @Schema(description = "When the person was invited.", example = "2026-01-01T00:00:00Z")
    private final Instant invitedAt;

    @Schema(description = "When the invitation's current link expires. Null when it has no link that can still be "
            + "used: it expired, or it was revoked, by hand or after 5 wrong confirmation codes. Resending issues a "
            + "new one.",
            example = "2026-01-08T00:00:00Z", types = {"string", "null"})
    private final Instant expiresAt;

    public PlatformAdminInvitationResponse(PendingPlatformAdmin pending) {
        this.invitedAt = pending.invitedAt();
        this.expiresAt = pending.linkExpiresAt();
    }

    public Instant getInvitedAt() {
        return invitedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}

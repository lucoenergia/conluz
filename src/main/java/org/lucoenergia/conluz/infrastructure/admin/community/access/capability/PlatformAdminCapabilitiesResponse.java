package org.lucoenergia.conluz.infrastructure.admin.community.access.capability;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * What the caller may do with one platform admin of the list.
 *
 * <p>Every field answers "may this caller do this?", and nothing else. A capability is never a
 * statement about the resource's <em>state</em>: whether a revoke would be accepted right now, once
 * the caller may ask for it, is the platform admin's {@code revocable} field. Clients read both.</p>
 */
@Schema(description = "What the caller may do with this platform admin.", requiredProperties = {"canRevoke", "canResendInvitation", "canRevokeInvitation"})
public class PlatformAdminCapabilitiesResponse {

    @Schema(description = "Whether the caller may revoke this user's platform-admin flag (POST /api/v1/users/{userId}/revoke-platform-admin). Always false when the user is the caller: an admin may not strip their own flag. Whether the revoke would be accepted right now is the platform admin's revocable field.")
    private final boolean canRevoke;

    @Schema(description = "Whether the caller may resend this row's pending invitation (POST /api/v1/platform-admins/{userId}/invitation/resend). Authorization only: true on every row for a platform admin, including rows without an invitation, where the resend is refused. Whether the row has a pending invitation is its invitation field.")
    private final boolean canResendInvitation;

    @Schema(description = "Whether the caller may revoke this row's pending invitation link (POST /api/v1/platform-admins/{userId}/invitation/revoke). Authorization only: true on every row for a platform admin, including rows without an invitation, where the revoke is refused. Whether the row has a pending invitation is its invitation field.")
    private final boolean canRevokeInvitation;

    private PlatformAdminCapabilitiesResponse(Builder builder) {
        this.canRevoke = builder.canRevoke;
        this.canResendInvitation = builder.canResendInvitation;
        this.canRevokeInvitation = builder.canRevokeInvitation;
    }

    public boolean isCanRevoke() {
        return canRevoke;
    }

    public boolean isCanResendInvitation() {
        return canResendInvitation;
    }

    public boolean isCanRevokeInvitation() {
        return canRevokeInvitation;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Named setters rather than a constructor, like every other capabilities response, so a field
     * added later cannot be swapped with this one.
     */
    public static final class Builder {

        private boolean canRevoke;
        private boolean canResendInvitation;
        private boolean canRevokeInvitation;

        public Builder withCanRevoke(boolean canRevoke) {
            this.canRevoke = canRevoke;
            return this;
        }

        public Builder withCanResendInvitation(boolean canResendInvitation) {
            this.canResendInvitation = canResendInvitation;
            return this;
        }

        public Builder withCanRevokeInvitation(boolean canRevokeInvitation) {
            this.canRevokeInvitation = canRevokeInvitation;
            return this;
        }

        public PlatformAdminCapabilitiesResponse build() {
            return new PlatformAdminCapabilitiesResponse(this);
        }
    }
}

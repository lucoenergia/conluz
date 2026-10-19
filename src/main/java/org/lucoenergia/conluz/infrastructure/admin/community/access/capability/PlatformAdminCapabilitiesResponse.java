package org.lucoenergia.conluz.infrastructure.admin.community.access.capability;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * What the caller may do with one platform admin of the list.
 *
 * <p>Every field answers "may this caller do this?", and nothing else. A capability is never a
 * statement about the resource's <em>state</em>: whether a revoke would be accepted right now, once
 * the caller may ask for it, is the platform admin's {@code revocable} field. Clients read both.</p>
 */
@Schema(description = "What the caller may do with this platform admin.", requiredProperties = {"canRevoke"})
public class PlatformAdminCapabilitiesResponse {

    @Schema(description = "Whether the caller may revoke this user's platform-admin flag (POST /api/v1/users/{userId}/revoke-platform-admin). Always false when the user is the caller: an admin may not strip their own flag. Whether the revoke would be accepted right now is the platform admin's revocable field.")
    private final boolean canRevoke;

    private PlatformAdminCapabilitiesResponse(Builder builder) {
        this.canRevoke = builder.canRevoke;
    }

    public boolean isCanRevoke() {
        return canRevoke;
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

        public Builder withCanRevoke(boolean canRevoke) {
            this.canRevoke = canRevoke;
            return this;
        }

        public PlatformAdminCapabilitiesResponse build() {
            return new PlatformAdminCapabilitiesResponse(this);
        }
    }
}

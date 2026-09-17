package org.lucoenergia.conluz.infrastructure.admin.community.access.capability;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * What the caller may do on the platform as a whole, independently of any one resource.
 *
 * <p>Every field answers "may this caller do this?", and nothing else. A capability is never a
 * statement about the resource's <em>state</em>: an agreement that is already published still
 * reports {@code canManage = true} to an admin, because the admin is allowed to manage it and it is
 * the agreement's {@code status} that says the action would be rejected right now. Clients read
 * both.</p>
 *
 * <p>Returned only by {@code GET /api/v1/users/current}: these are facts about the caller, not
 * about a user, so they do not belong on {@link org.lucoenergia.conluz.infrastructure.admin.user.UserResponse}.</p>
 */
@Schema(description = "What the caller may do on the platform as a whole.", requiredProperties = {"canCreateCommunity", "canListUsers"})
public class PlatformCapabilitiesResponse {

    @Schema(description = "Whether the caller may create a community (POST /api/v1/communities).")
    private final boolean canCreateCommunity;

    @Schema(description = "Whether the caller may list users (GET /api/v1/users).")
    private final boolean canListUsers;

    private PlatformCapabilitiesResponse(Builder builder) {
        this.canCreateCommunity = builder.canCreateCommunity;
        this.canListUsers = builder.canListUsers;
    }

    public boolean isCanCreateCommunity() {
        return canCreateCommunity;
    }

    public boolean isCanListUsers() {
        return canListUsers;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Named setters rather than a constructor: every field is a boolean, so a positional
     * constructor would let two capabilities be swapped without the compiler noticing.
     */
    public static final class Builder {

        private boolean canCreateCommunity;
        private boolean canListUsers;

        public Builder withCanCreateCommunity(boolean canCreateCommunity) {
            this.canCreateCommunity = canCreateCommunity;
            return this;
        }

        public Builder withCanListUsers(boolean canListUsers) {
            this.canListUsers = canListUsers;
            return this;
        }

        public PlatformCapabilitiesResponse build() {
            return new PlatformCapabilitiesResponse(this);
        }
    }
}

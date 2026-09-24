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
@Schema(description = "What the caller may do on the platform as a whole.", requiredProperties = {"canCreateCommunity", "canListUsers", "canAdministerPlatform", "canCreateUsers"})
public class PlatformCapabilitiesResponse {

    @Schema(description = "Whether the caller may create a community (POST /api/v1/communities).")
    private final boolean canCreateCommunity;

    @Schema(description = "Whether the caller may list users (GET /api/v1/users).")
    private final boolean canListUsers;

    @Schema(description = "Whether the caller may use the platform administration surfaces -- the communities administration page, the platform overview and the landing route. No single endpoint asks this question: it gates a surface, and the endpoints behind that surface report their own capabilities.")
    private final boolean canAdministerPlatform;

    @Schema(description = "Whether the caller may create a user attached to no community (POST /api/v1/users with no communityId). CommunityCapabilitiesResponse.canCreateUsers answers the same question for one community.")
    private final boolean canCreateUsers;

    private PlatformCapabilitiesResponse(Builder builder) {
        this.canCreateCommunity = builder.canCreateCommunity;
        this.canListUsers = builder.canListUsers;
        this.canAdministerPlatform = builder.canAdministerPlatform;
        this.canCreateUsers = builder.canCreateUsers;
    }

    public boolean isCanCreateCommunity() {
        return canCreateCommunity;
    }

    public boolean isCanListUsers() {
        return canListUsers;
    }

    public boolean isCanAdministerPlatform() {
        return canAdministerPlatform;
    }

    public boolean isCanCreateUsers() {
        return canCreateUsers;
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
        private boolean canAdministerPlatform;
        private boolean canCreateUsers;

        public Builder withCanCreateCommunity(boolean canCreateCommunity) {
            this.canCreateCommunity = canCreateCommunity;
            return this;
        }

        public Builder withCanListUsers(boolean canListUsers) {
            this.canListUsers = canListUsers;
            return this;
        }

        public Builder withCanAdministerPlatform(boolean canAdministerPlatform) {
            this.canAdministerPlatform = canAdministerPlatform;
            return this;
        }

        public Builder withCanCreateUsers(boolean canCreateUsers) {
            this.canCreateUsers = canCreateUsers;
            return this;
        }

        public PlatformCapabilitiesResponse build() {
            return new PlatformCapabilitiesResponse(this);
        }
    }
}

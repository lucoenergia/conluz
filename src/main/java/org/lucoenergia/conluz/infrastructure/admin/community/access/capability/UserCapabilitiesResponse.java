package org.lucoenergia.conluz.infrastructure.admin.community.access.capability;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * What the caller may do with this user.
 *
 * <p>Every field answers "may this caller do this?", and nothing else. A capability is never a
 * statement about the resource's <em>state</em>: an agreement that is already published still
 * reports {@code canManage = true} to an admin, because the admin is allowed to manage it and it is
 * the agreement's {@code status} that says the action would be rejected right now. Clients read
 * both.</p>
 */
@Schema(description = "What the caller may do with this user.", requiredProperties = {"canRead", "canEdit", "canDelete", "canEnable", "canDisable", "canGrantPlatformAdmin", "canRevokePlatformAdmin", "canListSupplies"})
public class UserCapabilitiesResponse {

    @Schema(description = "Whether the caller may read this user (GET /api/v1/users/{userId}).")
    private final boolean canRead;

    @Schema(description = "Whether the caller may edit this user administratively (PUT /api/v1/users/{userId}). This is false for an ordinary member looking at their own record: changing one's own contact details goes through PUT /api/v1/users/profile, which any authenticated caller may use.")
    private final boolean canEdit;

    @Schema(description = "Whether the caller may delete this user. Always false when the user is the caller: nobody may delete themselves, admins included.")
    private final boolean canDelete;

    @Schema(description = "Whether the caller may enable this user. Always false when the user is the caller.")
    private final boolean canEnable;

    @Schema(description = "Whether the caller may disable this user. Always false when the user is the caller.")
    private final boolean canDisable;

    @Schema(description = "Whether the caller may grant this user the platform-admin flag.")
    private final boolean canGrantPlatformAdmin;

    @Schema(description = "Whether the caller may revoke this user's platform-admin flag. Always false when the user is the caller: an admin may not strip their own flag.")
    private final boolean canRevokePlatformAdmin;

    @Schema(description = "Whether the caller may list this user's supplies (GET /api/v1/users/{userId}/supplies). Deliberately narrower than canRead: reading a user is not reading their supply data, so a platform admin who administers none of their communities may not.")
    private final boolean canListSupplies;

    private UserCapabilitiesResponse(Builder builder) {
        this.canRead = builder.canRead;
        this.canEdit = builder.canEdit;
        this.canDelete = builder.canDelete;
        this.canEnable = builder.canEnable;
        this.canDisable = builder.canDisable;
        this.canGrantPlatformAdmin = builder.canGrantPlatformAdmin;
        this.canRevokePlatformAdmin = builder.canRevokePlatformAdmin;
        this.canListSupplies = builder.canListSupplies;
    }

    public boolean isCanRead() {
        return canRead;
    }

    public boolean isCanEdit() {
        return canEdit;
    }

    public boolean isCanDelete() {
        return canDelete;
    }

    public boolean isCanEnable() {
        return canEnable;
    }

    public boolean isCanDisable() {
        return canDisable;
    }

    public boolean isCanGrantPlatformAdmin() {
        return canGrantPlatformAdmin;
    }

    public boolean isCanRevokePlatformAdmin() {
        return canRevokePlatformAdmin;
    }

    public boolean isCanListSupplies() {
        return canListSupplies;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Named setters rather than a constructor: every field is a boolean, so a positional
     * constructor would let two capabilities be swapped without the compiler noticing.
     */
    public static final class Builder {

        private boolean canRead;
        private boolean canEdit;
        private boolean canDelete;
        private boolean canEnable;
        private boolean canDisable;
        private boolean canGrantPlatformAdmin;
        private boolean canRevokePlatformAdmin;
        private boolean canListSupplies;

        public Builder withCanRead(boolean canRead) {
            this.canRead = canRead;
            return this;
        }

        public Builder withCanEdit(boolean canEdit) {
            this.canEdit = canEdit;
            return this;
        }

        public Builder withCanDelete(boolean canDelete) {
            this.canDelete = canDelete;
            return this;
        }

        public Builder withCanEnable(boolean canEnable) {
            this.canEnable = canEnable;
            return this;
        }

        public Builder withCanDisable(boolean canDisable) {
            this.canDisable = canDisable;
            return this;
        }

        public Builder withCanGrantPlatformAdmin(boolean canGrantPlatformAdmin) {
            this.canGrantPlatformAdmin = canGrantPlatformAdmin;
            return this;
        }

        public Builder withCanRevokePlatformAdmin(boolean canRevokePlatformAdmin) {
            this.canRevokePlatformAdmin = canRevokePlatformAdmin;
            return this;
        }

        public Builder withCanListSupplies(boolean canListSupplies) {
            this.canListSupplies = canListSupplies;
            return this;
        }

        public UserCapabilitiesResponse build() {
            return new UserCapabilitiesResponse(this);
        }
    }
}

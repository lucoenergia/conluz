package org.lucoenergia.conluz.infrastructure.admin.community.access.capability;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * What the caller may do with this membership.
 *
 * <p>Every field answers "may this caller do this?", and nothing else. A capability is never a
 * statement about the resource's <em>state</em>: an agreement that is already published still
 * reports {@code canManage = true} to an admin, because the admin is allowed to manage it and it is
 * the agreement's {@code status} that says the action would be rejected right now. Clients read
 * both.</p>
 */
@Schema(description = "What the caller may do with this membership.", requiredProperties = {"canUpdateRole", "canDelete", "canManageInvestment", "canReadPayback", "canInvite", "canEnable", "canDisable"})
public class MembershipCapabilitiesResponse {

    @Schema(description = "Whether the caller may change this membership's role.")
    private final boolean canUpdateRole;

    @Schema(description = "Whether the caller may remove this membership from the community.")
    private final boolean canDelete;

    @Schema(description = "Whether the caller may set or clear the investment recorded on this membership.")
    private final boolean canManageInvestment;

    @Schema(description = "Whether the caller may read this membership's payback progress. Either an admin of the community, or the member themselves.")
    private final boolean canReadPayback;

    @Schema(description = "Whether the caller may invite this member to accept the membership, and resend or revoke its invitation. Whether one can be sent right now depends on the membership's state, its contact email and how many invitations it has been sent in the last 24 hours.")
    private final boolean canInvite;

    @Schema(description = "Whether the caller may enable this membership (POST /api/v1/communities/{communityId}/memberships/{userId}/enable). Only an active admin of the community, and only on a membership with the COMMUNITY_MEMBER role. Whether it is disabled right now is the membership's enabled field.")
    private final boolean canEnable;

    @Schema(description = "Whether the caller may disable this membership (POST /api/v1/communities/{communityId}/memberships/{userId}/disable). Only an active admin of the community, and only on a membership with the COMMUNITY_MEMBER role. Whether it is enabled right now is the membership's enabled field.")
    private final boolean canDisable;

    private MembershipCapabilitiesResponse(Builder builder) {
        this.canUpdateRole = builder.canUpdateRole;
        this.canDelete = builder.canDelete;
        this.canManageInvestment = builder.canManageInvestment;
        this.canReadPayback = builder.canReadPayback;
        this.canInvite = builder.canInvite;
        this.canEnable = builder.canEnable;
        this.canDisable = builder.canDisable;
    }

    public boolean isCanUpdateRole() {
        return canUpdateRole;
    }

    public boolean isCanDelete() {
        return canDelete;
    }

    public boolean isCanManageInvestment() {
        return canManageInvestment;
    }

    public boolean isCanReadPayback() {
        return canReadPayback;
    }

    public boolean isCanInvite() {
        return canInvite;
    }

    public boolean isCanEnable() {
        return canEnable;
    }

    public boolean isCanDisable() {
        return canDisable;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Named setters rather than a constructor: every field is a boolean, so a positional
     * constructor would let two capabilities be swapped without the compiler noticing.
     */
    public static final class Builder {

        private boolean canUpdateRole;
        private boolean canDelete;
        private boolean canManageInvestment;
        private boolean canReadPayback;
        private boolean canInvite;
        private boolean canEnable;
        private boolean canDisable;

        public Builder withCanUpdateRole(boolean canUpdateRole) {
            this.canUpdateRole = canUpdateRole;
            return this;
        }

        public Builder withCanDelete(boolean canDelete) {
            this.canDelete = canDelete;
            return this;
        }

        public Builder withCanManageInvestment(boolean canManageInvestment) {
            this.canManageInvestment = canManageInvestment;
            return this;
        }

        public Builder withCanReadPayback(boolean canReadPayback) {
            this.canReadPayback = canReadPayback;
            return this;
        }

        public Builder withCanInvite(boolean canInvite) {
            this.canInvite = canInvite;
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

        public MembershipCapabilitiesResponse build() {
            return new MembershipCapabilitiesResponse(this);
        }
    }
}

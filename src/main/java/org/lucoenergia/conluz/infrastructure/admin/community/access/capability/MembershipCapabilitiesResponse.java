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
@Schema(description = "What the caller may do with this membership.", requiredProperties = {"canUpdateRole", "canDelete", "canManageInvestment", "canReadPayback"})
public class MembershipCapabilitiesResponse {

    @Schema(description = "Whether the caller may change this membership's role.")
    private final boolean canUpdateRole;

    @Schema(description = "Whether the caller may remove this membership from the community.")
    private final boolean canDelete;

    @Schema(description = "Whether the caller may set or clear the investment recorded on this membership.")
    private final boolean canManageInvestment;

    @Schema(description = "Whether the caller may read this membership's payback progress. Either an admin of the community, or the member themselves.")
    private final boolean canReadPayback;

    private MembershipCapabilitiesResponse(Builder builder) {
        this.canUpdateRole = builder.canUpdateRole;
        this.canDelete = builder.canDelete;
        this.canManageInvestment = builder.canManageInvestment;
        this.canReadPayback = builder.canReadPayback;
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

        public MembershipCapabilitiesResponse build() {
            return new MembershipCapabilitiesResponse(this);
        }
    }
}

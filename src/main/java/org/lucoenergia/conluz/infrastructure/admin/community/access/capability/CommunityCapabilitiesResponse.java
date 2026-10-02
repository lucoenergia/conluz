package org.lucoenergia.conluz.infrastructure.admin.community.access.capability;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * What the caller may do with this community.
 *
 * <p>Every field answers "may this caller do this?", and nothing else. A capability is never a
 * statement about the resource's <em>state</em>: an agreement that is already published still
 * reports {@code canManage = true} to an admin, because the admin is allowed to manage it and it is
 * the agreement's {@code status} that says the action would be rejected right now. Clients read
 * both.</p>
 */
@Schema(description = "What the caller may do with this community.", requiredProperties = {"canRead", "canUpdate", "canEnable", "canDisable", "canManage", "canManageMemberships", "canManageMembershipInvestment", "canListPlants", "canCreatePlants", "canCreateUsers", "canReadProduction", "canListSupplies"})
public class CommunityCapabilitiesResponse {

    @Schema(description = "Whether the caller may read this community (GET /api/v1/communities/{communityId}).")
    private final boolean canRead;

    @Schema(description = "Whether the caller may update this community (PUT /api/v1/communities/{communityId}). A platform-wide decision: being a community admin does not confer it.")
    private final boolean canUpdate;

    @Schema(description = "Whether the caller may enable this community (POST /api/v1/communities/{communityId}/enable). A platform-wide decision.")
    private final boolean canEnable;

    @Schema(description = "Whether the caller may disable this community (POST /api/v1/communities/{communityId}/disable). A platform-wide decision.")
    private final boolean canDisable;

    @Schema(description = "Whether the caller administers this community: creating and importing its supplies, reading and writing its Datadis and Shelly configuration, and triggering its synchronisation endpoints.")
    private final boolean canManage;

    @Schema(description = "Whether the caller may administer this community's roster: creating, deleting and re-roling its memberships.")
    private final boolean canManageMemberships;

    @Schema(description = "Whether the caller may set or clear the investment recorded on this community's memberships. Stricter than canManageMemberships: an investment is the member's own money, so there is no platform-admin bypass.")
    private final boolean canManageMembershipInvestment;

    @Schema(description = "Whether the caller may list this community's plants (GET /api/v1/communities/{communityId}/plants).")
    private final boolean canListPlants;

    @Schema(description = "Whether the caller may create plants in this community. Creating one also requires a supply the caller can see, so a true here is necessary but not sufficient for any particular supply -- SupplyCapabilitiesResponse.canCreatePlant answers that.")
    private final boolean canCreatePlants;

    @Schema(description = "Whether the caller may create users in this community (POST /api/v1/users, POST /api/v1/users/import).")
    private final boolean canCreateUsers;

    @Schema(description = "Whether the caller may read this community's communal production data. Membership, not administration: a platform admin who is not a member may not.")
    private final boolean canReadProduction;

    @Schema(description = "Whether the caller may list this community's supplies (GET /api/v1/communities/{communityId}/supplies). A non-admin member gets only their own.")
    private final boolean canListSupplies;

    private CommunityCapabilitiesResponse(Builder builder) {
        this.canRead = builder.canRead;
        this.canUpdate = builder.canUpdate;
        this.canEnable = builder.canEnable;
        this.canDisable = builder.canDisable;
        this.canManage = builder.canManage;
        this.canManageMemberships = builder.canManageMemberships;
        this.canManageMembershipInvestment = builder.canManageMembershipInvestment;
        this.canListPlants = builder.canListPlants;
        this.canCreatePlants = builder.canCreatePlants;
        this.canCreateUsers = builder.canCreateUsers;
        this.canReadProduction = builder.canReadProduction;
        this.canListSupplies = builder.canListSupplies;
    }

    public boolean isCanRead() {
        return canRead;
    }

    public boolean isCanUpdate() {
        return canUpdate;
    }

    public boolean isCanEnable() {
        return canEnable;
    }

    public boolean isCanDisable() {
        return canDisable;
    }

    public boolean isCanManage() {
        return canManage;
    }

    public boolean isCanManageMemberships() {
        return canManageMemberships;
    }

    public boolean isCanManageMembershipInvestment() {
        return canManageMembershipInvestment;
    }

    public boolean isCanListPlants() {
        return canListPlants;
    }

    public boolean isCanCreatePlants() {
        return canCreatePlants;
    }

    public boolean isCanCreateUsers() {
        return canCreateUsers;
    }

    public boolean isCanReadProduction() {
        return canReadProduction;
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
        private boolean canUpdate;
        private boolean canEnable;
        private boolean canDisable;
        private boolean canManage;
        private boolean canManageMemberships;
        private boolean canManageMembershipInvestment;
        private boolean canListPlants;
        private boolean canCreatePlants;
        private boolean canCreateUsers;
        private boolean canReadProduction;
        private boolean canListSupplies;

        public Builder withCanRead(boolean canRead) {
            this.canRead = canRead;
            return this;
        }

        public Builder withCanUpdate(boolean canUpdate) {
            this.canUpdate = canUpdate;
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

        public Builder withCanManage(boolean canManage) {
            this.canManage = canManage;
            return this;
        }

        public Builder withCanManageMemberships(boolean canManageMemberships) {
            this.canManageMemberships = canManageMemberships;
            return this;
        }

        public Builder withCanManageMembershipInvestment(boolean canManageMembershipInvestment) {
            this.canManageMembershipInvestment = canManageMembershipInvestment;
            return this;
        }

        public Builder withCanListPlants(boolean canListPlants) {
            this.canListPlants = canListPlants;
            return this;
        }

        public Builder withCanCreatePlants(boolean canCreatePlants) {
            this.canCreatePlants = canCreatePlants;
            return this;
        }

        public Builder withCanCreateUsers(boolean canCreateUsers) {
            this.canCreateUsers = canCreateUsers;
            return this;
        }

        public Builder withCanReadProduction(boolean canReadProduction) {
            this.canReadProduction = canReadProduction;
            return this;
        }

        public Builder withCanListSupplies(boolean canListSupplies) {
            this.canListSupplies = canListSupplies;
            return this;
        }

        public CommunityCapabilitiesResponse build() {
            return new CommunityCapabilitiesResponse(this);
        }
    }
}

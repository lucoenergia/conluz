package org.lucoenergia.conluz.infrastructure.admin.community.access.capability;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * What the caller may do with this plant.
 *
 * <p>Every field answers "may this caller do this?", and nothing else. A capability is never a
 * statement about the resource's <em>state</em>: an agreement that is already published still
 * reports {@code canManage = true} to an admin, because the admin is allowed to manage it and it is
 * the agreement's {@code status} that says the action would be rejected right now. Clients read
 * both.</p>
 */
@Schema(description = "What the caller may do with this plant.", requiredProperties = {"canRead", "canManage", "canListSharingAgreements", "canManageSharingAgreements", "canReadSupply"})
public class PlantCapabilitiesResponse {

    @Schema(description = "Whether the caller may read this plant. Open to any enabled member of its community.")
    private final boolean canRead;

    @Schema(description = "Whether the caller may update or delete this plant, and read and write its Huawei configuration.")
    private final boolean canManage;

    @Schema(description = "Whether the caller may list this plant's sharing agreements. Admin-only: their contents are not member-readable.")
    private final boolean canListSharingAgreements;

    @Schema(description = "Whether the caller may create a sharing agreement under this plant.")
    private final boolean canManageSharingAgreements;

    @Schema(description = "Whether the caller may open the supply referenced by this plant (GET /api/v1/supplies/{supplyId}). Listing plants is open to any member, but the supply behind one is not, so the reference carries no owner and this says whether following it would succeed.")
    private final boolean canReadSupply;

    private PlantCapabilitiesResponse(Builder builder) {
        this.canRead = builder.canRead;
        this.canManage = builder.canManage;
        this.canListSharingAgreements = builder.canListSharingAgreements;
        this.canManageSharingAgreements = builder.canManageSharingAgreements;
        this.canReadSupply = builder.canReadSupply;
    }

    public boolean isCanRead() {
        return canRead;
    }

    public boolean isCanManage() {
        return canManage;
    }

    public boolean isCanListSharingAgreements() {
        return canListSharingAgreements;
    }

    public boolean isCanManageSharingAgreements() {
        return canManageSharingAgreements;
    }

    public boolean isCanReadSupply() {
        return canReadSupply;
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
        private boolean canManage;
        private boolean canListSharingAgreements;
        private boolean canManageSharingAgreements;
        private boolean canReadSupply;

        public Builder withCanRead(boolean canRead) {
            this.canRead = canRead;
            return this;
        }

        public Builder withCanManage(boolean canManage) {
            this.canManage = canManage;
            return this;
        }

        public Builder withCanListSharingAgreements(boolean canListSharingAgreements) {
            this.canListSharingAgreements = canListSharingAgreements;
            return this;
        }

        public Builder withCanManageSharingAgreements(boolean canManageSharingAgreements) {
            this.canManageSharingAgreements = canManageSharingAgreements;
            return this;
        }

        public Builder withCanReadSupply(boolean canReadSupply) {
            this.canReadSupply = canReadSupply;
            return this;
        }

        public PlantCapabilitiesResponse build() {
            return new PlantCapabilitiesResponse(this);
        }
    }
}

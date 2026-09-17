package org.lucoenergia.conluz.infrastructure.admin.community.access.capability;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * What the caller may do with this supply.
 *
 * <p>Every field answers "may this caller do this?", and nothing else. A capability is never a
 * statement about the resource's <em>state</em>: an agreement that is already published still
 * reports {@code canManage = true} to an admin, because the admin is allowed to manage it and it is
 * the agreement's {@code status} that says the action would be rejected right now. Clients read
 * both.</p>
 */
@Schema(description = "What the caller may do with this supply.", requiredProperties = {"canRead", "canEdit", "canReadPartitionCoefficients", "canCreatePlant"})
public class SupplyCapabilitiesResponse {

    @Schema(description = "Whether the caller may read this supply and its consumption and production series.")
    private final boolean canRead;

    @Schema(description = "Whether the caller may update, enable or disable this supply. Narrower than canRead: the owner can read their supply but only a community admin may change it.")
    private final boolean canEdit;

    @Schema(description = "Whether the caller may read this supply's partition coefficients. The same access reading the supply requires -- the coefficients describe the owner's own share.")
    private final boolean canReadPartitionCoefficients;

    @Schema(description = "Whether the caller may create a plant on this supply (POST /api/v1/plants).")
    private final boolean canCreatePlant;

    private SupplyCapabilitiesResponse(Builder builder) {
        this.canRead = builder.canRead;
        this.canEdit = builder.canEdit;
        this.canReadPartitionCoefficients = builder.canReadPartitionCoefficients;
        this.canCreatePlant = builder.canCreatePlant;
    }

    public boolean isCanRead() {
        return canRead;
    }

    public boolean isCanEdit() {
        return canEdit;
    }

    public boolean isCanReadPartitionCoefficients() {
        return canReadPartitionCoefficients;
    }

    public boolean isCanCreatePlant() {
        return canCreatePlant;
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
        private boolean canReadPartitionCoefficients;
        private boolean canCreatePlant;

        public Builder withCanRead(boolean canRead) {
            this.canRead = canRead;
            return this;
        }

        public Builder withCanEdit(boolean canEdit) {
            this.canEdit = canEdit;
            return this;
        }

        public Builder withCanReadPartitionCoefficients(boolean canReadPartitionCoefficients) {
            this.canReadPartitionCoefficients = canReadPartitionCoefficients;
            return this;
        }

        public Builder withCanCreatePlant(boolean canCreatePlant) {
            this.canCreatePlant = canCreatePlant;
            return this;
        }

        public SupplyCapabilitiesResponse build() {
            return new SupplyCapabilitiesResponse(this);
        }
    }
}

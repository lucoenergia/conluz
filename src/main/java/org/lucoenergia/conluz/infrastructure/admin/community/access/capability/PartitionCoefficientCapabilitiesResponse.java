package org.lucoenergia.conluz.infrastructure.admin.community.access.capability;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * What the caller may do from this partition coefficient period.
 *
 * <p>A period is not a resource a caller acts on, but it embeds references a client navigates to,
 * and references carry no capabilities of their own. So, as {@code PlantCapabilitiesResponse.canReadSupply}
 * does for a plant's supply, the period says whether following its reference would succeed.</p>
 */
@Schema(description = "What the caller may do from this partition coefficient period.",
        requiredProperties = {"canReadSharingAgreement"})
public class PartitionCoefficientCapabilitiesResponse {

    @Schema(description = "Whether the caller may open the sharing agreement referenced by this period "
            + "(GET /api/v1/plants/{plantId}/sharing-agreements/{sharingAgreementId}). The reference "
            + "carries no capabilities of its own, so the period says whether following it would succeed.")
    private final boolean canReadSharingAgreement;

    private PartitionCoefficientCapabilitiesResponse(Builder builder) {
        this.canReadSharingAgreement = builder.canReadSharingAgreement;
    }

    public boolean isCanReadSharingAgreement() {
        return canReadSharingAgreement;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Named setters rather than a constructor, as on every other capabilities response: a boolean
     * added later could otherwise be swapped with this one without the compiler noticing.
     */
    public static final class Builder {

        private boolean canReadSharingAgreement;

        public Builder withCanReadSharingAgreement(boolean canReadSharingAgreement) {
            this.canReadSharingAgreement = canReadSharingAgreement;
            return this;
        }

        public PartitionCoefficientCapabilitiesResponse build() {
            return new PartitionCoefficientCapabilitiesResponse(this);
        }
    }
}

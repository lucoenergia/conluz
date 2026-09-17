package org.lucoenergia.conluz.infrastructure.admin.supply.partitioncoefficient;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyPartitionCoefficientDetail;
import org.lucoenergia.conluz.infrastructure.shared.web.reference.CommunityReferenceResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.reference.PlantReferenceResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.reference.SharingAgreementReferenceResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.reference.SupplyReferenceResponse;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(requiredProperties = {"id", "supply", "community", "plant", "sharingAgreement", "coefficient", "validFrom",
        "validTo", "createdAt"})
public class PartitionCoefficientResponse {

    @Schema(description = "Internal unique identifier", example = "b3d1a2f0-1234-5678-abcd-000000000001")
    private final UUID id;

    @Schema(description = "Supply this coefficient belongs to")
    private final SupplyReferenceResponse supply;

    @Schema(description = "Community the supply belongs to")
    private final CommunityReferenceResponse community;

    @Schema(description = "Plant this coefficient belongs to. Disambiguates a supply's timeline " +
            "when it participates in more than one plant.")
    private final PlantReferenceResponse plant;

    @Schema(description = "Sharing agreement that authored this coefficient.")
    private final SharingAgreementReferenceResponse sharingAgreement;

    @Schema(description = "Partition coefficient value", example = "0.030763")
    private final BigDecimal coefficient;

    @Schema(description = "Start of the period during which this coefficient is active (inclusive). " +
            "Null means this is a pending coefficient, materialised but not yet activated.",
            example = "2024-05-23T00:00:00Z", types = {"string", "null"})
    private final Instant validFrom;

    @Schema(description = "End of the period (exclusive). Null means the period is still open; " +
            "combined with a non-null validFrom that makes this the currently active coefficient " +
            "for its plant.",
            example = "2025-01-01T00:00:00Z", types = {"string", "null"})
    private final Instant validTo;

    @Schema(description = "Timestamp when this record was created", example = "2024-05-23T10:30:00Z")
    private final Instant createdAt;

    public PartitionCoefficientResponse(SupplyPartitionCoefficientDetail detail) {
        this.id = detail.getId();
        this.supply = new SupplyReferenceResponse(detail.getSupply().id(), detail.getSupply().code(),
                detail.getSupply().name());
        this.community = new CommunityReferenceResponse(detail.getCommunity().id(),
                detail.getCommunity().name());
        this.plant = new PlantReferenceResponse(detail.getPlant().id(), detail.getPlant().name());
        this.sharingAgreement = new SharingAgreementReferenceResponse(detail.getSharingAgreement().id(),
                detail.getSharingAgreement().name(), detail.getSharingAgreement().status());
        this.coefficient = detail.getCoefficientValue();
        this.validFrom = detail.getValidFrom();
        this.validTo = detail.getValidTo();
        this.createdAt = detail.getCreatedAt();
    }

    public UUID getId() {
        return id;
    }

    public SupplyReferenceResponse getSupply() {
        return supply;
    }

    public CommunityReferenceResponse getCommunity() {
        return community;
    }

    public PlantReferenceResponse getPlant() {
        return plant;
    }

    public SharingAgreementReferenceResponse getSharingAgreement() {
        return sharingAgreement;
    }

    public BigDecimal getCoefficient() {
        return coefficient;
    }

    public Instant getValidFrom() {
        return validFrom;
    }

    public Instant getValidTo() {
        return validTo;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

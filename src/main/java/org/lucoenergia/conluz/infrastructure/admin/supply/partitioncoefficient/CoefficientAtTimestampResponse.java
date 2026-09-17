package org.lucoenergia.conluz.infrastructure.admin.supply.partitioncoefficient;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyPartitionCoefficientDetail;
import org.lucoenergia.conluz.infrastructure.shared.web.reference.CommunityReferenceResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.reference.PlantReferenceResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.reference.SupplyReferenceResponse;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(requiredProperties = {"supply", "community", "plant", "timestamp", "coefficient"})
public class CoefficientAtTimestampResponse {

    @Schema(description = "Supply the coefficient belongs to")
    private final SupplyReferenceResponse supply;

    @Schema(description = "Community the supply belongs to")
    private final CommunityReferenceResponse community;

    @Schema(description = "Plant the coefficient applies in. A supply participating in several " +
            "plants has one coefficient per plant at any instant.")
    private final PlantReferenceResponse plant;

    @Schema(description = "Queried timestamp", example = "2025-01-15T12:00:00Z")
    private final Instant timestamp;

    @Schema(description = "Coefficient active in this plant at the queried timestamp", example = "0.030763")
    private final BigDecimal coefficient;

    public CoefficientAtTimestampResponse(SupplyPartitionCoefficientDetail detail, Instant timestamp) {
        this.supply = new SupplyReferenceResponse(detail.getSupply().id(), detail.getSupply().code(),
                detail.getSupply().name());
        this.community = new CommunityReferenceResponse(detail.getCommunity().id(),
                detail.getCommunity().name());
        this.plant = new PlantReferenceResponse(detail.getPlant().id(), detail.getPlant().name());
        this.timestamp = timestamp;
        this.coefficient = detail.getCoefficientValue();
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

    public Instant getTimestamp() {
        return timestamp;
    }

    public BigDecimal getCoefficient() {
        return coefficient;
    }
}

package org.lucoenergia.conluz.infrastructure.admin.supply.partitioncoefficient;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyPartitionCoefficient;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class PartitionCoefficientResponse {

    @Schema(description = "Internal unique identifier", example = "b3d1a2f0-1234-5678-abcd-000000000001")
    private final UUID id;

    @Schema(description = "Supply this coefficient belongs to", example = "ebbe60d1-f9db-455c-8c2d-c34ae7a1c23c")
    private final UUID supplyId;

    @Schema(description = "Partition coefficient value", example = "3.076300")
    private final BigDecimal coefficient;

    @Schema(description = "Start of the period during which this coefficient is active (inclusive)",
            example = "2024-05-23T00:00:00Z")
    private final Instant validFrom;

    @Schema(description = "End of the period (exclusive). Null means this is the currently active coefficient.",
            example = "2025-01-01T00:00:00Z", nullable = true)
    private final Instant validTo;

    @Schema(description = "Timestamp when this record was created", example = "2024-05-23T10:30:00Z")
    private final Instant createdAt;

    public PartitionCoefficientResponse(SupplyPartitionCoefficient domain) {
        this.id = domain.getId();
        this.supplyId = domain.getSupplyId();
        this.coefficient = domain.getCoefficient();
        this.validFrom = domain.getValidFrom();
        this.validTo = domain.getValidTo();
        this.createdAt = domain.getCreatedAt();
    }

    public UUID getId() {
        return id;
    }

    public UUID getSupplyId() {
        return supplyId;
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

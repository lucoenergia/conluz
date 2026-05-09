package org.lucoenergia.conluz.infrastructure.admin.supply.partitioncoefficient;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class CoefficientAtTimestampResponse {

    @Schema(description = "Supply UUID", example = "ebbe60d1-f9db-455c-8c2d-c34ae7a1c23c")
    private final UUID supplyId;

    @Schema(description = "Queried timestamp", example = "2025-01-15T12:00:00Z")
    private final Instant timestamp;

    @Schema(description = "Coefficient active at the queried timestamp", example = "3.076300")
    private final BigDecimal coefficient;

    public CoefficientAtTimestampResponse(UUID supplyId, Instant timestamp, BigDecimal coefficient) {
        this.supplyId = supplyId;
        this.timestamp = timestamp;
        this.coefficient = coefficient;
    }

    public UUID getSupplyId() {
        return supplyId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public BigDecimal getCoefficient() {
        return coefficient;
    }
}

package org.lucoenergia.conluz.infrastructure.admin.supply.partitioncoefficient;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(requiredProperties = {"coefficient", "effectiveAt"})
public class RegisterPartitionCoefficientBody {

    @NotNull
    @PositiveOrZero
    @Schema(description = "New partition coefficient value (same unit as supply.partitionCoefficient)",
            example = "3.076300")
    private BigDecimal coefficient;

    @NotNull
    @Schema(description = "Instant from which this coefficient becomes effective (ISO-8601)",
            example = "2025-06-01T00:00:00Z")
    private Instant effectiveAt;

    public BigDecimal getCoefficient() {
        return coefficient;
    }

    public void setCoefficient(BigDecimal coefficient) {
        this.coefficient = coefficient;
    }

    public Instant getEffectiveAt() {
        return effectiveAt;
    }

    public void setEffectiveAt(Instant effectiveAt) {
        this.effectiveAt = effectiveAt;
    }
}

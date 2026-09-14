package org.lucoenergia.conluz.infrastructure.production.sharingagreement;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import org.lucoenergia.conluz.domain.production.sharingagreement.ResolvedCoefficientEntry;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Schema(requiredProperties = {"coefficients"})
public class ReplacePartitionCoefficientsBody {

    @NotEmpty
    @Valid
    @Schema(description = "The agreement's full new coefficient set. Replaces the entire existing set.")
    private List<ReplacePartitionCoefficientEntry> coefficients;

    public List<ReplacePartitionCoefficientEntry> getCoefficients() {
        return coefficients;
    }

    public void setCoefficients(List<ReplacePartitionCoefficientEntry> coefficients) {
        this.coefficients = coefficients;
    }

    public List<ResolvedCoefficientEntry> mapToEntries() {
        return coefficients.stream()
                .map(entry -> new ResolvedCoefficientEntry(entry.getSupplyId(), entry.getCoefficient()))
                .collect(Collectors.toList());
    }

    @Schema(requiredProperties = {"supplyId", "coefficient"})
    public static class ReplacePartitionCoefficientEntry {

        @NotNull
        @Schema(description = "The supply's internal id.", example = "3c1f9e2a-6b7d-4a10-9c3e-2f8b6a1d4e5f")
        private UUID supplyId;

        @NotNull
        @PositiveOrZero
        @Schema(description = "Partition coefficient value", example = "0.030763")
        private BigDecimal coefficient;

        public UUID getSupplyId() {
            return supplyId;
        }

        public void setSupplyId(UUID supplyId) {
            this.supplyId = supplyId;
        }

        public BigDecimal getCoefficient() {
            return coefficient;
        }

        public void setCoefficient(BigDecimal coefficient) {
            this.coefficient = coefficient;
        }
    }
}

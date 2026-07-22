package org.lucoenergia.conluz.infrastructure.production.plant.sharingagreement;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import org.lucoenergia.conluz.domain.production.plant.sharingagreement.PendingCoefficientEntry;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Schema(requiredProperties = {"coefficients"})
public class ReplacePartitionCoefficientsBody {

    @NotEmpty
    @Valid
    @Schema(description = "The agreement's full new coefficient set. Replaces the entire existing set.")
    private List<Entry> coefficients;

    public List<Entry> getCoefficients() {
        return coefficients;
    }

    public void setCoefficients(List<Entry> coefficients) {
        this.coefficients = coefficients;
    }

    public List<PendingCoefficientEntry> mapToEntries() {
        return coefficients.stream()
                .map(entry -> new PendingCoefficientEntry(entry.getCups(), entry.getCoefficient()))
                .collect(Collectors.toList());
    }

    @Schema(requiredProperties = {"cups", "coefficient"})
    public static class Entry {

        @NotBlank
        @Schema(description = "Supply code (CUPS)", example = "ES0031300806333001KE0F")
        private String cups;

        @NotNull
        @PositiveOrZero
        @Schema(description = "Partition coefficient value", example = "0.030763")
        private BigDecimal coefficient;

        public String getCups() {
            return cups;
        }

        public void setCups(String cups) {
            this.cups = cups;
        }

        public BigDecimal getCoefficient() {
            return coefficient;
        }

        public void setCoefficient(BigDecimal coefficient) {
            this.coefficient = coefficient;
        }
    }
}

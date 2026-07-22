package org.lucoenergia.conluz.infrastructure.production.plant.sharingagreement;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyPartitionCoefficient;
import org.lucoenergia.conluz.infrastructure.admin.supply.partitioncoefficient.PartitionCoefficientResponse;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

public class ReplacePartitionCoefficientsResponse {

    @Schema(description = "The agreement's new, full pending coefficient set.")
    private final List<PartitionCoefficientResponse> coefficients;

    @Schema(description = "Warning message when the sum of the replaced coefficient set deviates " +
            "from 1 by more than 0.0001. Null when the sum is correct.",
            nullable = true,
            example = "Coefficient set sum is 0.958347, expected 1")
    private final String coefficientSumWarning;

    public ReplacePartitionCoefficientsResponse(List<SupplyPartitionCoefficient> saved) {
        this.coefficients = saved.stream().map(PartitionCoefficientResponse::new).collect(Collectors.toList());
        BigDecimal sum = saved.stream()
                .map(SupplyPartitionCoefficient::getCoefficient)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        this.coefficientSumWarning = PartitionCoefficientSetSumWarning.build(sum);
    }

    public List<PartitionCoefficientResponse> getCoefficients() {
        return coefficients;
    }

    public String getCoefficientSumWarning() {
        return coefficientSumWarning;
    }
}

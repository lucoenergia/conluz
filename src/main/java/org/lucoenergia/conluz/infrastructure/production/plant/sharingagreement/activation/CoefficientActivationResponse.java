package org.lucoenergia.conluz.infrastructure.production.plant.sharingagreement.activation;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyPartitionCoefficient;
import org.lucoenergia.conluz.infrastructure.admin.supply.partitioncoefficient.PartitionCoefficientResponse;

import java.util.List;
import java.util.stream.Collectors;

public class CoefficientActivationResponse {

    @Schema(description = "Every coefficient actually touched by this call -- the requested targets " +
            "and any predecessor cascaded as a result. Empty when the whole batch was a no-op.")
    private final List<PartitionCoefficientResponse> coefficients;

    public CoefficientActivationResponse(List<SupplyPartitionCoefficient> touched) {
        this.coefficients = touched.stream().map(PartitionCoefficientResponse::new).collect(Collectors.toList());
    }

    public List<PartitionCoefficientResponse> getCoefficients() {
        return coefficients;
    }
}

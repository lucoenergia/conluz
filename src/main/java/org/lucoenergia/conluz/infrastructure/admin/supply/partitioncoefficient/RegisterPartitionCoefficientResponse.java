package org.lucoenergia.conluz.infrastructure.admin.supply.partitioncoefficient;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyPartitionCoefficient;

import java.math.BigDecimal;

public class RegisterPartitionCoefficientResponse extends PartitionCoefficientResponse {

    @Schema(description = "Warning message when the sum of all active coefficients in the community " +
            "deviates from 1 by more than 0.0001 at the effectiveAt instant. Null when the sum is correct.",
            nullable = true,
            example = "Community coefficient sum is 0.958347, expected 1")
    private final String communityCoefficientSumWarning;

    public RegisterPartitionCoefficientResponse(SupplyPartitionCoefficient domain, BigDecimal communitySum) {
        super(domain);
        this.communityCoefficientSumWarning = CommunityCoefficientSumWarning.build(communitySum);
    }

    public String getCommunityCoefficientSumWarning() {
        return communityCoefficientSumWarning;
    }
}

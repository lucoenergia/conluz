package org.lucoenergia.conluz.infrastructure.admin.supply.partitioncoefficient;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.shared.response.CreationInBulkResponse;

import java.math.BigDecimal;

public class RegisterPartitionCoefficientsWithFileResponse
        extends CreationInBulkResponse<String, PartitionCoefficientResponse> {

    @Schema(description = "Warning message when the sum of all active coefficients in the community " +
            "deviates from 1 by more than 0.0001 at the effectiveAt instant. Null when the sum is correct.",
            nullable = true,
            example = "Community coefficient sum is 0.958347, expected 1")
    private String communityCoefficientSumWarning;

    public void applyCommunitySum(BigDecimal communitySum) {
        this.communityCoefficientSumWarning = CommunityCoefficientSumWarning.build(communitySum);
    }

    public String getCommunityCoefficientSumWarning() {
        return communityCoefficientSumWarning;
    }
}

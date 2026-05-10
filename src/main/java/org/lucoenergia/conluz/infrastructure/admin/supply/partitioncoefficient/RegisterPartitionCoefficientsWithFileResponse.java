package org.lucoenergia.conluz.infrastructure.admin.supply.partitioncoefficient;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.shared.error.BulkError;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class RegisterPartitionCoefficientsWithFileResponse {

    private final List<PartitionCoefficientResponse> created = new ArrayList<>();
    private final List<BulkError<String>> errors = new ArrayList<>();

    @Schema(description = "Warning message when the sum of all active coefficients in the community " +
            "deviates from 100 by more than 0.0001 at the effectiveAt instant. Null when the sum is correct.",
            nullable = true,
            example = "Community coefficient sum is 97.076300, expected 100")
    private String communityCoefficientSumWarning;

    public void addCreated(PartitionCoefficientResponse item) {
        created.add(item);
    }

    public void addError(String cups, String errorMessage) {
        errors.add(new BulkError<>(cups, errorMessage));
    }

    public void applyCommunitySum(BigDecimal communitySum) {
        this.communityCoefficientSumWarning = buildWarning(communitySum);
    }

    public List<PartitionCoefficientResponse> getCreated() {
        return List.copyOf(created);
    }

    public List<BulkError<String>> getErrors() {
        return List.copyOf(errors);
    }

    public String getCommunityCoefficientSumWarning() {
        return communityCoefficientSumWarning;
    }

    private static String buildWarning(BigDecimal communitySum) {
        if (communitySum == null) {
            return null;
        }
        BigDecimal expected = BigDecimal.valueOf(100);
        if (communitySum.subtract(expected).abs().compareTo(BigDecimal.valueOf(0.0001)) > 0) {
            return "Community coefficient sum is " + communitySum.stripTrailingZeros().toPlainString()
                    + ", expected 100";
        }
        return null;
    }
}

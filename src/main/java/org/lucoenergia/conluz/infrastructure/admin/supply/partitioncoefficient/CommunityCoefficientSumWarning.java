package org.lucoenergia.conluz.infrastructure.admin.supply.partitioncoefficient;

import java.math.BigDecimal;

/**
 * Builds the {@code communityCoefficientSumWarning} message shared by
 * {@link RegisterPartitionCoefficientResponse} and {@link RegisterPartitionCoefficientsWithFileResponse}.
 * Partition coefficients are persisted on a 0-1 scale, so the community-wide sum of all active
 * coefficients is expected to be 1, not 100.
 */
final class CommunityCoefficientSumWarning {

    private static final BigDecimal EXPECTED_COMMUNITY_SUM = BigDecimal.ONE;

    // Coefficients come from distributor TXT files with exactly 6 decimals of precision
    // (see RegisterPartitionCoefficientFileRow.BigDecimalConverter — no rounding is applied,
    // 6 decimals is simply the source file's convention). Summing ~30 supplies can accumulate at
    // most ~30 * 5e-7 ≈ 1.5e-5 of rounding noise. 1e-4 sits comfortably above that noise floor
    // while still catching any real, meaningful deviation from a community sum of 1.
    private static final BigDecimal TOLERANCE = BigDecimal.valueOf(0.0001);

    private CommunityCoefficientSumWarning() {
    }

    static String build(BigDecimal communitySum) {
        if (communitySum == null) {
            return null;
        }
        if (communitySum.subtract(EXPECTED_COMMUNITY_SUM).abs().compareTo(TOLERANCE) > 0) {
            return "Community coefficient sum is " + communitySum.stripTrailingZeros().toPlainString()
                    + ", expected 1";
        }
        return null;
    }
}

package org.lucoenergia.conluz.infrastructure.production.plant.sharingagreement;

import java.math.BigDecimal;

/**
 * Builds the {@code coefficientSumWarning} message for {@link ReplacePartitionCoefficientsResponse}.
 * This is a warning, never a hard error: Σβ ≠ 1 is legitimate mid-transition (e.g. a plant being
 * onboarded row by row). The distributor-file upload path enforces the exact sum as a hard error
 * instead (see {@link org.lucoenergia.conluz.domain.production.plant.distributorfile.DistributorFileErrorCode#COEFFICIENT_SUM_INVALID}),
 * since a file is expected to represent a complete, final set. Deliberately not the same class as
 * {@code CommunityCoefficientSumWarning} (infrastructure.admin.supply.partitioncoefficient): that
 * one sums the community-wide *active* coefficients at an instant; this one sums *this agreement's
 * pending* coefficient set -- same tolerance/shape, different scope.
 */
final class PartitionCoefficientSetSumWarning {

    private static final BigDecimal EXPECTED_SUM = BigDecimal.ONE;
    private static final BigDecimal TOLERANCE = BigDecimal.valueOf(0.0001);

    private PartitionCoefficientSetSumWarning() {
    }

    static String build(BigDecimal sum) {
        if (sum == null) {
            return null;
        }
        if (sum.subtract(EXPECTED_SUM).abs().compareTo(TOLERANCE) > 0) {
            return "Coefficient set sum is " + sum.stripTrailingZeros().toPlainString() + ", expected 1";
        }
        return null;
    }
}

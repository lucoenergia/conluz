package org.lucoenergia.conluz.infrastructure.admin.supply.partitioncoefficient;

import org.lucoenergia.conluz.domain.production.sharingagreement.DuplicatePartitionCoefficientSupplyException;
import org.lucoenergia.conluz.infrastructure.shared.error.PostgresConstraintNameChecker;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.UUID;

/**
 * The database side of "a supply holds at most one coefficient per sharing agreement" (SHA-018).
 * Materialisation refuses a supply listed twice before writing, but a write that bypasses that check
 * is rejected by this constraint, and must be reported as the same refusal rather than as an
 * unmapped integrity error (a 500).
 */
public final class SupplyPartitionCoefficientAgreementSupplyUniqueConstraint {

    public static final String NAME = "supply_partition_coefficient_sharing_agreement_id_supply_id_uq";

    private SupplyPartitionCoefficientAgreementSupplyUniqueConstraint() {
    }

    /**
     * Translates a violation of this specific constraint, identified by its name, into
     * {@link DuplicatePartitionCoefficientSupplyException} for the given agreement and supply. Any
     * other integrity violation is returned unchanged, so the caller rethrows it as it was.
     */
    public static RuntimeException translate(DataIntegrityViolationException exception, UUID sharingAgreementId,
                                             UUID supplyId) {
        if (PostgresConstraintNameChecker.matches(exception, NAME)) {
            return new DuplicatePartitionCoefficientSupplyException(sharingAgreementId, supplyId, exception);
        }
        return exception;
    }
}

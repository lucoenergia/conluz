package org.lucoenergia.conluz.domain.production.plant.sharingagreement.activation;

import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyPartitionCoefficient;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Records when the distributor applied (or corrected, or retracted) each coefficient's activation,
 * and when a coefficient's coverage explicitly ends (the exit case). Two operations, matching the two
 * columns they own -- one domain operation per column, one cascade implementation each, even though
 * the API exposes four verbs (activate/deactivate share {@link #setValidFrom}; close/reopen share
 * {@link #setValidTo}).
 */
public interface CoefficientActivationService {

    /**
     * Sets, corrects, or clears {@code validFrom} for each of {@code coefficientIds} (all must belong
     * to {@code sharingAgreementId}, itself belonging to {@code plantId}). {@code appliedOn == null}
     * reverts to pending, splicing the predecessor's {@code validTo} onto the coefficient's own former
     * {@code validTo}. A non-null {@code appliedOn} activates (if the coefficient was pending) or
     * corrects (if already active) -- the same code path, cascading the change onto the resolved
     * predecessor's {@code validTo} in either case.
     * <p>
     * Validates the whole batch before writing anything: if any item fails, the whole batch is
     * rejected via {@link CoefficientActivationException} and nothing is persisted. A request that
     * would produce no change (the coefficient already holds the requested value) is a no-op for that
     * item -- not an error, and no cascade or status recompute fires for it.
     *
     * @return every coefficient actually touched (targets and any cascaded predecessors), deduplicated
     * @throws org.lucoenergia.conluz.domain.production.plant.sharingagreement.SharingAgreementPlantMismatchException
     *         if the agreement does not belong to {@code plantId}
     * @throws org.lucoenergia.conluz.domain.production.plant.sharingagreement.SharingAgreementNotPublishedException
     *         if the agreement is DRAFT
     * @throws CoefficientActivationException if any item fails validation
     */
    List<SupplyPartitionCoefficient> setValidFrom(UUID plantId, UUID sharingAgreementId, LocalDate appliedOn,
                                                    List<UUID> coefficientIds);

    /**
     * Sets, corrects, or clears {@code validTo} for each of {@code coefficientIds} (all must belong to
     * {@code sharingAgreementId}, itself belonging to {@code plantId}). {@code closedOn == null}
     * reopens a previously-closed coefficient. A non-null {@code closedOn} closes (if currently open)
     * or corrects the close date (if already closed) -- the same code path; both correcting and
     * reopening are rejected when a successor coefficient already starts exactly at the current
     * {@code validTo} (that boundary was written by the activation cascade, not authored).
     * <p>
     * Same batch-validate-then-write and no-op semantics as {@link #setValidFrom}.
     *
     * @return every coefficient actually touched, deduplicated
     * @throws org.lucoenergia.conluz.domain.production.plant.sharingagreement.SharingAgreementPlantMismatchException
     *         if the agreement does not belong to {@code plantId}
     * @throws org.lucoenergia.conluz.domain.production.plant.sharingagreement.SharingAgreementNotPublishedException
     *         if the agreement is DRAFT
     * @throws CoefficientActivationException if any item fails validation
     */
    List<SupplyPartitionCoefficient> setValidTo(UUID plantId, UUID sharingAgreementId, LocalDate closedOn,
                                                  List<UUID> coefficientIds);
}

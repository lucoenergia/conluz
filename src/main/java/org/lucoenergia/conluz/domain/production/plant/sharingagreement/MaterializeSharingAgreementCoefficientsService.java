package org.lucoenergia.conluz.domain.production.plant.sharingagreement;

import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyPartitionCoefficient;
import org.lucoenergia.conluz.domain.production.plant.sharingagreementfile.SharingAgreementPlantMismatchException;

import java.util.List;
import java.util.UUID;

/**
 * Materialises a sharing agreement's partition coefficients as pending rows (validFrom = null) in
 * supply_partition_coefficient. Used by both the distributor-file upload path and the manual
 * authoring PUT path, so the assertDraft-then-atomic-replace sequence exists exactly once.
 */
public interface MaterializeSharingAgreementCoefficientsService {

    /**
     * Atomic full replace: verifies {@code sharingAgreementId} belongs to {@code plantId}, asserts
     * it is DRAFT, resolves every entry's CUPS to a Supply of the plant's community, then deletes
     * the agreement's entire existing coefficient set and inserts {@code entries} in its place as
     * pending rows (validFrom = null; activation is out of scope for this phase). All-or-nothing:
     * any failure (unknown CUPS, duplicate CUPS, non-DRAFT status) leaves the prior coefficient set
     * untouched.
     *
     * @throws SharingAgreementNotFoundException if sharingAgreementId does not exist
     * @throws SharingAgreementPlantMismatchException
     *         if sharingAgreementId does not belong to plantId
     * @throws SharingAgreementNotDraftException if the agreement is not DRAFT
     * @throws org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException
     *         if any entry's CUPS does not resolve to a supply of the plant's community
     * @throws DuplicatePartitionCoefficientEntryException if the same CUPS appears more than once
     *         in {@code entries}
     */
    List<SupplyPartitionCoefficient> replaceAll(UUID plantId, UUID sharingAgreementId,
                                                 List<PendingCoefficientEntry> entries);
}

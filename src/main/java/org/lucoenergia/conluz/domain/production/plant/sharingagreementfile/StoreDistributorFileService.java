package org.lucoenergia.conluz.domain.production.plant.sharingagreementfile;

import java.util.UUID;

/**
 * Validates a distributor coefficient-partition file against a plant, stores it as evidence on an
 * existing DRAFT sharing agreement, and materialises its parsed entries as pending partition
 * coefficients (validFrom = null; activation is a future phase's job). Does not create the
 * sharing agreement -- that is out of scope for this service.
 */
public interface StoreDistributorFileService {

    /**
     * @param plantId           the plant this file distributes production from
     * @param sharingAgreementId an existing DRAFT sharing agreement belonging to {@code plantId}
     * @param filename           original uploaded filename
     * @param content            raw file bytes
     * @param uploadedBy         the user uploading the file
     * @throws org.lucoenergia.conluz.domain.production.plant.sharingagreement.SharingAgreementNotDraftException
     *         if the agreement is not DRAFT
     * @throws org.lucoenergia.conluz.domain.production.plant.distributorfile.DistributorFileValidationException
     *         if the file fails any validation rule
     */
    DistributorFileStoreResult store(UUID plantId, UUID sharingAgreementId, String filename, byte[] content,
                                      UUID uploadedBy);
}

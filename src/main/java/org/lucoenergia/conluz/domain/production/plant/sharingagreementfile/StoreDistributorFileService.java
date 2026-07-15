package org.lucoenergia.conluz.domain.production.plant.sharingagreementfile;

import java.util.UUID;

/**
 * Validates a distributor coefficient-partition file against a plant and stores it as evidence
 * on an existing sharing agreement. Does not create the sharing agreement, and does not write to
 * the coefficient timeline -- both are out of scope for this slice.
 */
public interface StoreDistributorFileService {

    /**
     * @param plantId           the plant this file distributes production from
     * @param sharingAgreementId an existing sharing agreement belonging to {@code plantId}
     * @param filename           original uploaded filename
     * @param content            raw file bytes
     * @param uploadedBy         the user uploading the file
     * @throws org.lucoenergia.conluz.domain.production.plant.distributorfile.DistributorFileValidationException
     *         if the file fails any validation rule
     */
    DistributorFileStoreResult store(UUID plantId, UUID sharingAgreementId, String filename, byte[] content,
                                      UUID uploadedBy);
}

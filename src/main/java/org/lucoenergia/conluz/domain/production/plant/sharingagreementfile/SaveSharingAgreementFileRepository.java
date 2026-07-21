package org.lucoenergia.conluz.domain.production.plant.sharingagreementfile;

import java.util.UUID;

public interface SaveSharingAgreementFileRepository {

    /**
     * Persists the file, verifying first that {@code file.getSharingAgreementId()} belongs to
     * {@code plantId}.
     *
     * @throws org.lucoenergia.conluz.domain.production.plant.sharingagreement.SharingAgreementNotFoundException if
     *                                              the sharing agreement does not exist
     * @throws SharingAgreementMismatchException    if the sharing agreement does not belong to {@code plantId}
     */
    SharingAgreementFile save(SharingAgreementFile file, UUID plantId);
}

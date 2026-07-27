package org.lucoenergia.conluz.domain.production.sharingagreement.sharingagreementfile;

import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementNotFoundException;

import java.util.UUID;

public interface SaveSharingAgreementFileRepository {

    /**
     * Persists the file, verifying first that {@code file.getSharingAgreementId()} belongs to
     * {@code plantId}.
     *
     * @throws SharingAgreementNotFoundException if
     *                                              the sharing agreement does not exist
     * @throws SharingAgreementPlantMismatchException    if the sharing agreement does not belong to {@code plantId}
     */
    SharingAgreementFile save(SharingAgreementFile file, UUID plantId);
}

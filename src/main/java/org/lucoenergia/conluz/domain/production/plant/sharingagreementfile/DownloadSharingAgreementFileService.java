package org.lucoenergia.conluz.domain.production.plant.sharingagreementfile;

import java.util.UUID;

public interface DownloadSharingAgreementFileService {

    /**
     * @throws SharingAgreementFileNotFoundException if no such file exists
     */
    SharingAgreementFile downloadById(UUID fileId);

    /**
     * The most recently uploaded file for a sharing agreement.
     *
     * @throws SharingAgreementFileNotFoundException if the agreement has no files
     */
    SharingAgreementFile downloadLatestBySharingAgreementId(UUID sharingAgreementId);
}

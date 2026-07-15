package org.lucoenergia.conluz.domain.production.plant.get;

import org.lucoenergia.conluz.domain.production.plant.sharingagreementfile.SharingAgreementFile;

import java.util.Optional;
import java.util.UUID;

public interface GetSharingAgreementFileRepository {

    Optional<SharingAgreementFile> findById(UUID id);

    /**
     * The most recently uploaded file for a sharing agreement. Zero files is a valid state for an
     * agreement, hence {@link Optional}.
     */
    Optional<SharingAgreementFile> findLatestBySharingAgreementId(UUID sharingAgreementId);
}

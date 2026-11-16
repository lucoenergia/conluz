package org.lucoenergia.conluz.domain.production.sharingagreement.update;

import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreement;

import java.util.UUID;

public interface UpdateSharingAgreementService {

    /**
     * Replaces the descriptive fields (name, notes, nominal power, distributor file sent date) of a
     * sharing agreement, regardless of its status. Never touches {@code status}, {@code plantId},
     * {@code createdAt} or {@code createdBy}.
     *
     * @throws SharingAgreementDistributorFileSentOnInFutureException if the distributor file sent date
     *                                                                is after today in the application time zone
     */
    SharingAgreement update(UUID plantId, UUID sharingAgreementId, UpdateSharingAgreement update);
}

package org.lucoenergia.conluz.domain.production.sharingagreement.observation;

import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementStatus;

import java.util.UUID;

/**
 * Raised when the observation of an agreement is asked for one that is not analysed: a DRAFT agreement is not
 * in force, so nothing of it can be observed (OBS-006).
 */
public class SharingAgreementNotObservableException extends RuntimeException {

    private final UUID id;
    private final SharingAgreementStatus currentStatus;

    public SharingAgreementNotObservableException(UUID id, SharingAgreementStatus currentStatus) {
        this.id = id;
        this.currentStatus = currentStatus;
    }

    public UUID getId() {
        return id;
    }

    public SharingAgreementStatus getCurrentStatus() {
        return currentStatus;
    }
}

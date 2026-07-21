package org.lucoenergia.conluz.domain.production.plant.sharingagreement;

import java.util.UUID;

public class SharingAgreementNotDraftException extends RuntimeException {

    private final UUID id;
    private final SharingAgreementStatus currentStatus;

    public SharingAgreementNotDraftException(UUID id, SharingAgreementStatus currentStatus) {
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

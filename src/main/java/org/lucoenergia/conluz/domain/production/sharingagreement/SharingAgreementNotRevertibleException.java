package org.lucoenergia.conluz.domain.production.sharingagreement;

import java.util.UUID;

public class SharingAgreementNotRevertibleException extends RuntimeException {

    private final UUID id;
    private final SharingAgreementStatus currentStatus;

    public SharingAgreementNotRevertibleException(UUID id, SharingAgreementStatus currentStatus) {
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

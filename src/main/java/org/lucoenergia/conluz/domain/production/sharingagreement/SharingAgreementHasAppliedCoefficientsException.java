package org.lucoenergia.conluz.domain.production.sharingagreement;

import java.util.UUID;

public class SharingAgreementHasAppliedCoefficientsException extends RuntimeException {

    private final UUID id;

    public SharingAgreementHasAppliedCoefficientsException(UUID id) {
        this.id = id;
    }

    public UUID getId() {
        return id;
    }
}

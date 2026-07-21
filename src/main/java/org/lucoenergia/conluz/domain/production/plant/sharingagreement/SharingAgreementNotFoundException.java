package org.lucoenergia.conluz.domain.production.plant.sharingagreement;

import java.util.UUID;

public class SharingAgreementNotFoundException extends RuntimeException {

    private final UUID id;

    public SharingAgreementNotFoundException(UUID id) {
        this.id = id;
    }

    public UUID getId() {
        return id;
    }
}

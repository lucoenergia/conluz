package org.lucoenergia.conluz.domain.production.sharingagreement.sharingagreementfile;

import java.util.UUID;

public class SharingAgreementFileNotFoundException extends RuntimeException {

    private final UUID id;

    public SharingAgreementFileNotFoundException(UUID id) {
        this.id = id;
    }

    public UUID getId() {
        return id;
    }
}

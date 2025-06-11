package org.lucoenergia.conluz.domain.admin.supply;

import java.util.UUID;

public class SharingAgreementId {

    private final UUID id;

    private SharingAgreementId(UUID id) {
        this.id = id;
    }

    public UUID getId() {
        return id;
    }

    public static SharingAgreementId of(UUID id) {
        return new SharingAgreementId(id);
    }
}

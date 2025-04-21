package org.lucoenergia.conluz.domain.admin.supply;

public class SharingAgreementNotFoundException extends RuntimeException {

    private SharingAgreementId id;

    public SharingAgreementNotFoundException(SharingAgreementId id) {
        this.id = id;
    }

    public SharingAgreementId getId() {
        return id;
    }
}

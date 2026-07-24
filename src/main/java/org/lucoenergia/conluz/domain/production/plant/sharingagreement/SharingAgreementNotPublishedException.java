package org.lucoenergia.conluz.domain.production.plant.sharingagreement;

import java.util.UUID;

/**
 * Raised by {@link SharingAgreement#assertNotDraft()} when a coefficient-activation operation
 * (activate/deactivate/close/reopen) targets a DRAFT agreement. PUBLISHED and SUPERSEDED agreements
 * both pass -- only DRAFT is rejected, since a DRAFT has not been finalised and a subsequent
 * coefficient replacement would delete the activated row.
 */
public class SharingAgreementNotPublishedException extends RuntimeException {

    private final UUID id;
    private final SharingAgreementStatus currentStatus;

    public SharingAgreementNotPublishedException(UUID id, SharingAgreementStatus currentStatus) {
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

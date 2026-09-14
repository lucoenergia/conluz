package org.lucoenergia.conluz.domain.production.sharingagreement.update;

import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreement;

import java.util.UUID;

public interface UpdateSharingAgreementService {

    /**
     * Replaces the descriptive fields (name, notes, installed power) of a sharing agreement,
     * regardless of its status. Never touches {@code status}, {@code plantId}, {@code createdAt}
     * or {@code createdBy}.
     */
    SharingAgreement update(UUID plantId, UUID sharingAgreementId, UpdateSharingAgreement update);
}

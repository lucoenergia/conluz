package org.lucoenergia.conluz.domain.production.sharingagreement.update;

import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreement;

import java.util.UUID;

public interface UpdateSharingAgreementRepository {

    SharingAgreement update(UUID plantId, UUID sharingAgreementId, UpdateSharingAgreement update);
}

package org.lucoenergia.conluz.domain.production.plant.update;

import org.lucoenergia.conluz.domain.production.plant.sharingagreement.SharingAgreement;

import java.util.UUID;

public interface UpdateSharingAgreementRepository {

    SharingAgreement update(UUID plantId, UUID sharingAgreementId, UpdateSharingAgreement update);
}

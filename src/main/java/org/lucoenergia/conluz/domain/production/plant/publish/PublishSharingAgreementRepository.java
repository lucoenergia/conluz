package org.lucoenergia.conluz.domain.production.plant.publish;

import org.lucoenergia.conluz.domain.production.plant.sharingagreement.SharingAgreement;

import java.util.UUID;

public interface PublishSharingAgreementRepository {

    SharingAgreement publish(UUID plantId, UUID sharingAgreementId);
}

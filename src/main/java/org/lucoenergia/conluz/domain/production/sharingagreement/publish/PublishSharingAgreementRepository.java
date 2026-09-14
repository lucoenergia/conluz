package org.lucoenergia.conluz.domain.production.sharingagreement.publish;

import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreement;

import java.util.UUID;

public interface PublishSharingAgreementRepository {

    SharingAgreement publish(UUID plantId, UUID sharingAgreementId);
}

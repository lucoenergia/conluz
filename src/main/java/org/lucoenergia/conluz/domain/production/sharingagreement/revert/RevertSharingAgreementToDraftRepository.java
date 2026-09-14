package org.lucoenergia.conluz.domain.production.sharingagreement.revert;

import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreement;

import java.util.UUID;

public interface RevertSharingAgreementToDraftRepository {

    SharingAgreement revertToDraft(UUID plantId, UUID sharingAgreementId);
}

package org.lucoenergia.conluz.domain.production.sharingagreement.delete;

import java.util.UUID;

public interface DeleteSharingAgreementRepository {

    void delete(UUID plantId, UUID sharingAgreementId);
}

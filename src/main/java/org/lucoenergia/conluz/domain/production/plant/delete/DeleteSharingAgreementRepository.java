package org.lucoenergia.conluz.domain.production.plant.delete;

import java.util.UUID;

public interface DeleteSharingAgreementRepository {

    void delete(UUID plantId, UUID sharingAgreementId);
}

package org.lucoenergia.conluz.infrastructure.production.plant.delete;

import org.lucoenergia.conluz.domain.production.plant.delete.DeleteSharingAgreementRepository;
import org.lucoenergia.conluz.domain.production.plant.sharingagreement.SharingAgreementNotFoundException;
import org.lucoenergia.conluz.infrastructure.production.plant.sharingagreement.SharingAgreementEntity;
import org.lucoenergia.conluz.infrastructure.production.plant.sharingagreement.SharingAgreementRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Transactional
@Repository
public class DeleteSharingAgreementRepositoryDatabase implements DeleteSharingAgreementRepository {

    private final SharingAgreementRepository sharingAgreementRepository;

    public DeleteSharingAgreementRepositoryDatabase(SharingAgreementRepository sharingAgreementRepository) {
        this.sharingAgreementRepository = sharingAgreementRepository;
    }

    @Override
    public void delete(UUID plantId, UUID sharingAgreementId) {
        SharingAgreementEntity entity = sharingAgreementRepository.findById(sharingAgreementId)
                .orElseThrow(() -> new SharingAgreementNotFoundException(sharingAgreementId));
        if (!plantId.equals(entity.getPlant().getId())) {
            throw new SharingAgreementNotFoundException(sharingAgreementId);
        }
        sharingAgreementRepository.delete(entity);
    }
}

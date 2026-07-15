package org.lucoenergia.conluz.infrastructure.production.plant.get;

import org.lucoenergia.conluz.domain.production.plant.get.GetSharingAgreementRepository;
import org.lucoenergia.conluz.infrastructure.production.plant.SharingAgreementEntity;
import org.lucoenergia.conluz.infrastructure.production.plant.SharingAgreementRepository;
import org.lucoenergia.conluz.infrastructure.production.plant.SharingAgreementStatus;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Transactional
@Repository
public class GetSharingAgreementRepositoryDatabase implements GetSharingAgreementRepository {

    private final SharingAgreementRepository sharingAgreementRepository;

    public GetSharingAgreementRepositoryDatabase(SharingAgreementRepository sharingAgreementRepository) {
        this.sharingAgreementRepository = sharingAgreementRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> findCurrentPublishedAgreementIdByPlantId(UUID plantId) {
        return sharingAgreementRepository
                .findFirstByPlantIdAndStatusOrderByCreatedAtDesc(plantId, SharingAgreementStatus.PUBLISHED)
                .map(SharingAgreementEntity::getId);
    }
}

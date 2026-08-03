package org.lucoenergia.conluz.infrastructure.production.sharingagreement.get;

import org.lucoenergia.conluz.domain.production.sharingagreement.get.GetSharingAgreementRepository;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreement;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementStatus;
import org.lucoenergia.conluz.infrastructure.production.sharingagreement.SharingAgreementEntity;
import org.lucoenergia.conluz.infrastructure.production.sharingagreement.SharingAgreementEntityMapper;
import org.lucoenergia.conluz.infrastructure.production.sharingagreement.SharingAgreementRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Transactional
@Repository
public class GetSharingAgreementRepositoryDatabase implements GetSharingAgreementRepository {

    private final SharingAgreementRepository sharingAgreementRepository;
    private final SharingAgreementEntityMapper mapper;

    public GetSharingAgreementRepositoryDatabase(SharingAgreementRepository sharingAgreementRepository,
                                                  SharingAgreementEntityMapper mapper) {
        this.sharingAgreementRepository = sharingAgreementRepository;
        this.mapper = mapper;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> findCurrentPublishedAgreementIdByPlantId(UUID plantId) {
        return sharingAgreementRepository
                .findFirstByPlantIdAndStatusOrderByCreatedAtDesc(plantId, SharingAgreementStatus.PUBLISHED)
                .map(SharingAgreementEntity::getId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SharingAgreement> findById(UUID id) {
        return sharingAgreementRepository.findById(id).map(mapper::map);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SharingAgreement> findByPlantId(UUID plantId, SharingAgreementStatus status) {
        List<SharingAgreementEntity> entities = status == null
                ? sharingAgreementRepository.findByPlantIdOrderByCreatedAtDesc(plantId)
                : sharingAgreementRepository.findByPlantIdAndStatusOrderByCreatedAtDesc(plantId, status);
        return mapper.mapList(entities);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsLaterNonDraftAgreement(UUID plantId, UUID excludeSharingAgreementId, Instant afterCreatedAt) {
        return sharingAgreementRepository.existsLaterNonDraftAgreement(
                plantId, SharingAgreementStatus.DRAFT, afterCreatedAt, excludeSharingAgreementId);
    }
}

package org.lucoenergia.conluz.infrastructure.production.sharingagreement.get;

import org.lucoenergia.conluz.domain.production.sharingagreement.get.GetSharingAgreementRepository;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreement;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementStatus;
import org.lucoenergia.conluz.domain.production.sharingagreement.sharingagreementfile.GetSharingAgreementFileSummaryRepository;
import org.lucoenergia.conluz.domain.production.sharingagreement.sharingagreementfile.SharingAgreementFileSummary;
import org.lucoenergia.conluz.infrastructure.production.sharingagreement.SharingAgreementEntity;
import org.lucoenergia.conluz.infrastructure.production.sharingagreement.SharingAgreementEntityMapper;
import org.lucoenergia.conluz.infrastructure.production.sharingagreement.SharingAgreementRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Transactional(readOnly = true)
@Repository
public class GetSharingAgreementRepositoryDatabase implements GetSharingAgreementRepository {

    private final SharingAgreementRepository sharingAgreementRepository;
    private final GetSharingAgreementFileSummaryRepository fileSummaryRepository;
    private final SharingAgreementEntityMapper mapper;

    public GetSharingAgreementRepositoryDatabase(SharingAgreementRepository sharingAgreementRepository,
                                                  GetSharingAgreementFileSummaryRepository fileSummaryRepository,
                                                  SharingAgreementEntityMapper mapper) {
        this.sharingAgreementRepository = sharingAgreementRepository;
        this.fileSummaryRepository = fileSummaryRepository;
        this.mapper = mapper;
    }

    @Override
    public Optional<UUID> findCurrentPublishedAgreementIdByPlantId(UUID plantId) {
        return sharingAgreementRepository
                .findFirstByPlantIdAndStatusOrderByCreatedAtDesc(plantId, SharingAgreementStatus.PUBLISHED)
                .map(SharingAgreementEntity::getId);
    }

    @Override
    public Optional<SharingAgreement> findById(UUID id) {
        return sharingAgreementRepository.findById(id)
                .map(mapper::map)
                .map(agreement -> agreement.withFile(
                        fileSummaryRepository.findLatestBySharingAgreementId(agreement.getId()).orElse(null)));
    }

    @Override
    public List<SharingAgreement> findByPlantId(UUID plantId, SharingAgreementStatus status) {
        List<SharingAgreementEntity> entities = status == null
                ? sharingAgreementRepository.findByPlantIdOrderByCreatedAtDesc(plantId)
                : sharingAgreementRepository.findByPlantIdAndStatusOrderByCreatedAtDesc(plantId, status);
        List<SharingAgreement> agreements = mapper.mapList(entities);
        if (agreements.isEmpty()) {
            return agreements;
        }

        Map<UUID, SharingAgreementFileSummary> fileSummariesByAgreementId = fileSummaryRepository
                .findLatestBySharingAgreementIds(agreements.stream().map(SharingAgreement::getId).toList())
                .stream()
                .collect(Collectors.toMap(SharingAgreementFileSummary::getSharingAgreementId, summary -> summary));

        return agreements.stream()
                .map(agreement -> agreement.withFile(fileSummariesByAgreementId.get(agreement.getId())))
                .toList();
    }

    @Override
    public boolean existsLaterNonDraftAgreement(UUID plantId, UUID excludeSharingAgreementId, Instant afterCreatedAt) {
        return sharingAgreementRepository.existsLaterNonDraftAgreement(
                plantId, SharingAgreementStatus.DRAFT, afterCreatedAt, excludeSharingAgreementId);
    }
}

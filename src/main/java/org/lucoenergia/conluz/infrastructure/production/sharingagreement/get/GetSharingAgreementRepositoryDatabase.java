package org.lucoenergia.conluz.infrastructure.production.sharingagreement.get;

import org.lucoenergia.conluz.domain.production.sharingagreement.get.GetSharingAgreementRepository;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreement;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementStatus;
import org.lucoenergia.conluz.domain.production.sharingagreement.sharingagreementfile.SharingAgreementFileSummary;
import org.lucoenergia.conluz.infrastructure.production.sharingagreement.SharingAgreementEntity;
import org.lucoenergia.conluz.infrastructure.production.sharingagreement.SharingAgreementEntityMapper;
import org.lucoenergia.conluz.infrastructure.production.sharingagreement.SharingAgreementRepository;
import org.lucoenergia.conluz.infrastructure.production.sharingagreement.sharingagreementfile.SharingAgreementFileEntityMapper;
import org.lucoenergia.conluz.infrastructure.production.sharingagreement.sharingagreementfile.SharingAgreementFileRepository;
import org.lucoenergia.conluz.infrastructure.production.sharingagreement.sharingagreementfile.SharingAgreementFileSummaryProjection;
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
    private final SharingAgreementFileRepository sharingAgreementFileRepository;
    private final SharingAgreementEntityMapper mapper;
    private final SharingAgreementFileEntityMapper fileMapper;

    public GetSharingAgreementRepositoryDatabase(SharingAgreementRepository sharingAgreementRepository,
                                                  SharingAgreementFileRepository sharingAgreementFileRepository,
                                                  SharingAgreementEntityMapper mapper,
                                                  SharingAgreementFileEntityMapper fileMapper) {
        this.sharingAgreementRepository = sharingAgreementRepository;
        this.sharingAgreementFileRepository = sharingAgreementFileRepository;
        this.mapper = mapper;
        this.fileMapper = fileMapper;
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
                .map(agreement -> agreement.withFile(findFileSummary(agreement.getId())));
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

        Map<UUID, SharingAgreementFileSummary> fileSummariesByAgreementId = sharingAgreementFileRepository
                .findLatestSummariesBySharingAgreementIds(agreements.stream().map(SharingAgreement::getId).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(
                        SharingAgreementFileSummaryProjection::getSharingAgreementId,
                        fileMapper::mapSummary,
                        (a, b) -> compareIdsAsPostgresDoes(a.getId(), b.getId()) >= 0 ? a : b));

        return agreements.stream()
                .map(agreement -> agreement.withFile(fileSummariesByAgreementId.get(agreement.getId())))
                .toList();
    }

    private SharingAgreementFileSummary findFileSummary(UUID sharingAgreementId) {
        return sharingAgreementFileRepository
                .findFirstProjectedBySharingAgreementIdOrderByUploadedAtDescIdDesc(sharingAgreementId)
                .map(fileMapper::mapSummary)
                .orElse(null);
    }

    /**
     * {@link UUID#compareTo} compares {@code mostSigBits}/{@code leastSigBits} as signed longs, so it
     * disagrees with PostgreSQL's byte-wise (unsigned) {@code uuid} ordering for any pair of ids whose
     * first hex digit differs in its high bit (roughly half of all pairs). This tiebreak must agree
     * with the DB-side {@code ORDER BY id DESC} used by the single-lookup query
     * (findFirstProjectedBySharingAgreementIdOrderByUploadedAtDescIdDesc), or the list and single-item
     * paths could resolve the same tie to two different "latest" files.
     */
    private static int compareIdsAsPostgresDoes(UUID a, UUID b) {
        int cmp = Long.compareUnsigned(a.getMostSignificantBits(), b.getMostSignificantBits());
        return cmp != 0 ? cmp : Long.compareUnsigned(a.getLeastSignificantBits(), b.getLeastSignificantBits());
    }

    @Override
    public boolean existsLaterNonDraftAgreement(UUID plantId, UUID excludeSharingAgreementId, Instant afterCreatedAt) {
        return sharingAgreementRepository.existsLaterNonDraftAgreement(
                plantId, SharingAgreementStatus.DRAFT, afterCreatedAt, excludeSharingAgreementId);
    }
}

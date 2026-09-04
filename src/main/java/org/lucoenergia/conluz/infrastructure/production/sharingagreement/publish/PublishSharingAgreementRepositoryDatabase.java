package org.lucoenergia.conluz.infrastructure.production.sharingagreement.publish;

import org.lucoenergia.conluz.domain.production.sharingagreement.publish.PublishSharingAgreementRepository;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreement;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementNotFoundException;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementStatus;
import org.lucoenergia.conluz.infrastructure.production.sharingagreement.SharingAgreementEntity;
import org.lucoenergia.conluz.infrastructure.production.sharingagreement.SharingAgreementEntityMapper;
import org.lucoenergia.conluz.infrastructure.production.sharingagreement.SharingAgreementRepository;
import org.lucoenergia.conluz.infrastructure.production.sharingagreement.sharingagreementfile.SharingAgreementFileEntityMapper;
import org.lucoenergia.conluz.infrastructure.production.sharingagreement.sharingagreementfile.SharingAgreementFileRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Transactional
@Repository
public class PublishSharingAgreementRepositoryDatabase implements PublishSharingAgreementRepository {

    private final SharingAgreementRepository sharingAgreementRepository;
    private final SharingAgreementFileRepository sharingAgreementFileRepository;
    private final SharingAgreementEntityMapper mapper;
    private final SharingAgreementFileEntityMapper fileMapper;

    public PublishSharingAgreementRepositoryDatabase(SharingAgreementRepository sharingAgreementRepository,
                                                      SharingAgreementFileRepository sharingAgreementFileRepository,
                                                      SharingAgreementEntityMapper mapper,
                                                      SharingAgreementFileEntityMapper fileMapper) {
        this.sharingAgreementRepository = sharingAgreementRepository;
        this.sharingAgreementFileRepository = sharingAgreementFileRepository;
        this.mapper = mapper;
        this.fileMapper = fileMapper;
    }

    @Override
    public SharingAgreement publish(UUID plantId, UUID sharingAgreementId) {
        SharingAgreementEntity entity = sharingAgreementRepository.findById(sharingAgreementId)
                .orElseThrow(() -> new SharingAgreementNotFoundException(sharingAgreementId));
        if (!plantId.equals(entity.getPlant().getId())) {
            throw new SharingAgreementNotFoundException(sharingAgreementId);
        }

        entity.setStatus(SharingAgreementStatus.PUBLISHED);

        SharingAgreement agreement = mapper.map(sharingAgreementRepository.save(entity));
        return agreement.withFile(sharingAgreementFileRepository
                .findFirstProjectedBySharingAgreementIdOrderByUploadedAtDescIdDesc(agreement.getId())
                .map(fileMapper::mapSummary)
                .orElse(null));
    }
}

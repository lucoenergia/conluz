package org.lucoenergia.conluz.infrastructure.production.sharingagreement.publish;

import org.lucoenergia.conluz.domain.production.sharingagreement.publish.PublishSharingAgreementRepository;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreement;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementNotFoundException;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementStatus;
import org.lucoenergia.conluz.infrastructure.production.sharingagreement.SharingAgreementEntity;
import org.lucoenergia.conluz.infrastructure.production.sharingagreement.SharingAgreementEntityMapper;
import org.lucoenergia.conluz.infrastructure.production.sharingagreement.SharingAgreementRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Transactional
@Repository
public class PublishSharingAgreementRepositoryDatabase implements PublishSharingAgreementRepository {

    private final SharingAgreementRepository sharingAgreementRepository;
    private final SharingAgreementEntityMapper mapper;

    public PublishSharingAgreementRepositoryDatabase(SharingAgreementRepository sharingAgreementRepository,
                                                      SharingAgreementEntityMapper mapper) {
        this.sharingAgreementRepository = sharingAgreementRepository;
        this.mapper = mapper;
    }

    @Override
    public SharingAgreement publish(UUID plantId, UUID sharingAgreementId) {
        SharingAgreementEntity entity = sharingAgreementRepository.findById(sharingAgreementId)
                .orElseThrow(() -> new SharingAgreementNotFoundException(sharingAgreementId));
        if (!plantId.equals(entity.getPlant().getId())) {
            throw new SharingAgreementNotFoundException(sharingAgreementId);
        }

        entity.setStatus(SharingAgreementStatus.PUBLISHED);

        return mapper.map(sharingAgreementRepository.save(entity));
    }
}

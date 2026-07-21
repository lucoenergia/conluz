package org.lucoenergia.conluz.infrastructure.production.plant.sharingagreementfile;

import org.lucoenergia.conluz.domain.production.plant.sharingagreement.SharingAgreementNotFoundException;
import org.lucoenergia.conluz.domain.production.plant.sharingagreementfile.SaveSharingAgreementFileRepository;
import org.lucoenergia.conluz.domain.production.plant.sharingagreementfile.SharingAgreementFile;
import org.lucoenergia.conluz.domain.production.plant.sharingagreementfile.SharingAgreementMismatchException;
import org.lucoenergia.conluz.infrastructure.production.plant.sharingagreement.SharingAgreementEntity;
import org.lucoenergia.conluz.infrastructure.production.plant.sharingagreement.SharingAgreementRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Transactional
@Repository
public class SaveSharingAgreementFileRepositoryDatabase implements SaveSharingAgreementFileRepository {

    private final SharingAgreementFileRepository sharingAgreementFileRepository;
    private final SharingAgreementRepository sharingAgreementRepository;
    private final SharingAgreementFileEntityMapper mapper;

    public SaveSharingAgreementFileRepositoryDatabase(SharingAgreementFileRepository sharingAgreementFileRepository,
                                                       SharingAgreementRepository sharingAgreementRepository,
                                                       SharingAgreementFileEntityMapper mapper) {
        this.sharingAgreementFileRepository = sharingAgreementFileRepository;
        this.sharingAgreementRepository = sharingAgreementRepository;
        this.mapper = mapper;
    }

    @Override
    public SharingAgreementFile save(SharingAgreementFile file, UUID plantId) {
        SharingAgreementEntity sharingAgreementEntity = sharingAgreementRepository.findById(file.getSharingAgreementId())
                .orElseThrow(() -> new SharingAgreementNotFoundException(file.getSharingAgreementId()));
        if (!sharingAgreementEntity.getPlant().getId().equals(plantId)) {
            throw new SharingAgreementMismatchException(file.getSharingAgreementId(), plantId);
        }

        SharingAgreementFileEntity entity = new SharingAgreementFileEntity();
        entity.setId(file.getId());
        entity.setSharingAgreement(sharingAgreementEntity);
        entity.setFilename(file.getFilename());
        entity.setContent(file.getContent());
        entity.setContentHash(file.getContentHash());
        entity.setUploadedAt(file.getUploadedAt());
        entity.setUploadedBy(file.getUploadedBy());

        return mapper.map(sharingAgreementFileRepository.save(entity));
    }
}

package org.lucoenergia.conluz.infrastructure.production.plant.update;

import org.lucoenergia.conluz.domain.production.plant.sharingagreement.SharingAgreementNotFoundException;
import org.lucoenergia.conluz.domain.production.plant.sharingagreement.SharingAgreement;
import org.lucoenergia.conluz.domain.production.plant.update.UpdateSharingAgreement;
import org.lucoenergia.conluz.domain.production.plant.update.UpdateSharingAgreementRepository;
import org.lucoenergia.conluz.infrastructure.production.plant.sharingagreement.SharingAgreementEntity;
import org.lucoenergia.conluz.infrastructure.production.plant.sharingagreement.SharingAgreementEntityMapper;
import org.lucoenergia.conluz.infrastructure.production.plant.sharingagreement.SharingAgreementRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Transactional
@Repository
public class UpdateSharingAgreementRepositoryDatabase implements UpdateSharingAgreementRepository {

    private final SharingAgreementRepository sharingAgreementRepository;
    private final SharingAgreementEntityMapper mapper;

    public UpdateSharingAgreementRepositoryDatabase(SharingAgreementRepository sharingAgreementRepository,
                                                     SharingAgreementEntityMapper mapper) {
        this.sharingAgreementRepository = sharingAgreementRepository;
        this.mapper = mapper;
    }

    @Override
    public SharingAgreement update(UUID plantId, UUID sharingAgreementId, UpdateSharingAgreement update) {
        SharingAgreementEntity entity = sharingAgreementRepository.findById(sharingAgreementId)
                .orElseThrow(() -> new SharingAgreementNotFoundException(sharingAgreementId));
        if (!plantId.equals(entity.getPlant().getId())) {
            throw new SharingAgreementNotFoundException(sharingAgreementId);
        }

        entity.setName(update.getName());
        entity.setNotes(update.getNotes());
        entity.setInstalledPowerKw(update.getInstalledPowerKw());

        return mapper.map(sharingAgreementRepository.save(entity));
    }
}

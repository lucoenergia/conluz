package org.lucoenergia.conluz.infrastructure.production.sharingagreement.create;

import org.lucoenergia.conluz.domain.production.plant.PlantNotFoundException;
import org.lucoenergia.conluz.domain.production.sharingagreement.create.CreateSharingAgreementRepository;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreement;
import org.lucoenergia.conluz.domain.shared.PlantId;
import org.lucoenergia.conluz.infrastructure.production.plant.PlantEntity;
import org.lucoenergia.conluz.infrastructure.production.plant.PlantRepository;
import org.lucoenergia.conluz.infrastructure.production.sharingagreement.SharingAgreementEntity;
import org.lucoenergia.conluz.infrastructure.production.sharingagreement.SharingAgreementEntityMapper;
import org.lucoenergia.conluz.infrastructure.production.sharingagreement.SharingAgreementRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@Repository
public class CreateSharingAgreementRepositoryDatabase implements CreateSharingAgreementRepository {

    private final SharingAgreementRepository sharingAgreementRepository;
    private final PlantRepository plantRepository;
    private final SharingAgreementEntityMapper mapper;

    public CreateSharingAgreementRepositoryDatabase(SharingAgreementRepository sharingAgreementRepository,
                                                     PlantRepository plantRepository,
                                                     SharingAgreementEntityMapper mapper) {
        this.sharingAgreementRepository = sharingAgreementRepository;
        this.plantRepository = plantRepository;
        this.mapper = mapper;
    }

    @Override
    public SharingAgreement create(SharingAgreement agreement) {
        PlantEntity plantEntity = plantRepository.findById(agreement.getPlantId())
                .orElseThrow(() -> new PlantNotFoundException(PlantId.of(agreement.getPlantId())));

        SharingAgreementEntity entity = new SharingAgreementEntity();
        entity.setId(agreement.getId());
        entity.setPlant(plantEntity);
        entity.setName(agreement.getName());
        entity.setNotes(agreement.getNotes());
        entity.setStatus(agreement.getStatus());
        entity.setInstalledPowerKw(agreement.getInstalledPowerKw());
        entity.setCreatedAt(agreement.getCreatedAt());
        entity.setCreatedBy(agreement.getCreatedBy());

        return mapper.map(sharingAgreementRepository.save(entity));
    }
}

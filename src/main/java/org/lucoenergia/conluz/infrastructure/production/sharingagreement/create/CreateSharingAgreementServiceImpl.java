package org.lucoenergia.conluz.infrastructure.production.sharingagreement.create;

import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.plant.PlantNotFoundException;
import org.lucoenergia.conluz.domain.production.sharingagreement.create.CreateSharingAgreementRepository;
import org.lucoenergia.conluz.domain.production.sharingagreement.create.CreateSharingAgreementService;
import org.lucoenergia.conluz.domain.production.plant.get.GetPlantRepository;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreement;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementStatus;
import org.lucoenergia.conluz.domain.shared.PlantId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Transactional
@Service
public class CreateSharingAgreementServiceImpl implements CreateSharingAgreementService {

    private final GetPlantRepository getPlantRepository;
    private final CreateSharingAgreementRepository repository;

    public CreateSharingAgreementServiceImpl(GetPlantRepository getPlantRepository,
                                              CreateSharingAgreementRepository repository) {
        this.getPlantRepository = getPlantRepository;
        this.repository = repository;
    }

    @Override
    public SharingAgreement create(UUID plantId, String name, String notes, UUID createdBy) {
        Plant plant = getPlantRepository.findById(PlantId.of(plantId))
                .orElseThrow(() -> new PlantNotFoundException(PlantId.of(plantId)));

        SharingAgreement agreement = new SharingAgreement.Builder()
                .withId(UUID.randomUUID())
                .withPlantId(plantId)
                .withName(name)
                .withNotes(notes)
                .withStatus(SharingAgreementStatus.DRAFT)
                .withInstalledPowerKw(BigDecimal.valueOf(plant.getTotalPower()))
                .withCreatedAt(Instant.now())
                .withCreatedBy(createdBy)
                .build();

        return repository.create(agreement);
    }
}

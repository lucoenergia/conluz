package org.lucoenergia.conluz.infrastructure.production.sharingagreement.create;

import org.lucoenergia.conluz.domain.production.plant.PlantNotFoundException;
import org.lucoenergia.conluz.domain.production.sharingagreement.create.CreateSharingAgreement;
import org.lucoenergia.conluz.domain.production.sharingagreement.create.CreateSharingAgreementRepository;
import org.lucoenergia.conluz.domain.production.sharingagreement.create.CreateSharingAgreementService;
import org.lucoenergia.conluz.domain.production.plant.get.GetPlantRepository;
import org.lucoenergia.conluz.domain.production.plant.lock.LockPlantRepository;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreement;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementStatus;
import org.lucoenergia.conluz.domain.shared.PlantId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Transactional
@Service
public class CreateSharingAgreementServiceImpl implements CreateSharingAgreementService {

    private final GetPlantRepository getPlantRepository;
    private final LockPlantRepository lockPlantRepository;
    private final CreateSharingAgreementRepository repository;
    private final Clock clock;

    public CreateSharingAgreementServiceImpl(GetPlantRepository getPlantRepository,
                                              LockPlantRepository lockPlantRepository,
                                              CreateSharingAgreementRepository repository,
                                              Clock clock) {
        this.getPlantRepository = getPlantRepository;
        this.lockPlantRepository = lockPlantRepository;
        this.repository = repository;
        this.clock = clock;
    }

    /**
     * The plant is locked against deletion before the agreement is written (PLT-001): a deletion in progress is
     * waited for, and then the plant is not found, instead of the agreement failing on its foreign key.
     */
    @Override
    public SharingAgreement create(UUID plantId, CreateSharingAgreement request) {
        if (!lockPlantRepository.lockAsReferenced(PlantId.of(plantId))) {
            throw new PlantNotFoundException(PlantId.of(plantId));
        }
        getPlantRepository.findById(PlantId.of(plantId))
                .orElseThrow(() -> new PlantNotFoundException(PlantId.of(plantId)));

        SharingAgreement agreement = new SharingAgreement.Builder()
                .withId(UUID.randomUUID())
                .withPlantId(plantId)
                .withName(request.getName())
                .withNotes(request.getNotes())
                .withStatus(SharingAgreementStatus.DRAFT)
                .withInstalledPowerKw(request.getInstalledPowerKw())
                .withCreatedAt(clock.instant())
                .withCreatedBy(request.getCreatedBy())
                .build();

        return repository.create(agreement);
    }
}

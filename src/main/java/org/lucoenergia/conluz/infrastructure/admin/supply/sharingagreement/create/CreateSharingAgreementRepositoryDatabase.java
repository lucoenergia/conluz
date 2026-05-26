package org.lucoenergia.conluz.infrastructure.admin.supply.sharingagreement.create;

import org.lucoenergia.conluz.domain.admin.supply.sharingagreement.SharingAgreement;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSharingAgreement;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSharingAgreementRepository;
import org.lucoenergia.conluz.infrastructure.admin.supply.sharingagreement.SharingAgreementEntity;
import org.lucoenergia.conluz.infrastructure.admin.supply.sharingagreement.SharingAgreementEntityMapper;
import org.lucoenergia.conluz.infrastructure.admin.supply.sharingagreement.SharingAgreementRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Repository
@Transactional
public class CreateSharingAgreementRepositoryDatabase implements CreateSharingAgreementRepository {

    private final SharingAgreementRepository repository;
    private final SharingAgreementEntityMapper mapper;

    public CreateSharingAgreementRepositoryDatabase(SharingAgreementRepository repository,
                                                    SharingAgreementEntityMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public SharingAgreement create(CreateSharingAgreement command) {
        SharingAgreementEntity entity = new SharingAgreementEntity();
        entity.setId(UUID.randomUUID());
        entity.setStartDate(command.getStartDate());
        entity.setEndDate(command.getEndDate());
        entity.setNotes(command.getNotes());

        SharingAgreementEntity savedEntity = repository.save(entity);
        return mapper.map(savedEntity);
    }
}

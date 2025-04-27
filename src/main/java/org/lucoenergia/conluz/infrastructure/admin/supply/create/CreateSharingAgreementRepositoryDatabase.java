package org.lucoenergia.conluz.infrastructure.admin.supply.create;

import org.lucoenergia.conluz.domain.admin.supply.SharingAgreement;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSharingAgreementRepository;
import org.lucoenergia.conluz.infrastructure.admin.supply.SharingAgreementEntity;
import org.lucoenergia.conluz.infrastructure.admin.supply.SharingAgreementEntityMapper;
import org.lucoenergia.conluz.infrastructure.admin.supply.SharingAgreementRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Implementation of the repository for creating sharing agreements
 */
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
    public SharingAgreement create(LocalDate startDate, LocalDate endDate) {
        SharingAgreementEntity entity = new SharingAgreementEntity();
        entity.setId(UUID.randomUUID());
        entity.setStartDate(startDate);
        entity.setEndDate(endDate);
        
        SharingAgreementEntity savedEntity = repository.save(entity);
        return mapper.map(savedEntity);
    }
}
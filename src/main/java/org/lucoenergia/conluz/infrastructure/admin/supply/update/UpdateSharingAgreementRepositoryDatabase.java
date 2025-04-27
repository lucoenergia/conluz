package org.lucoenergia.conluz.infrastructure.admin.supply.update;

import org.lucoenergia.conluz.domain.admin.supply.SharingAgreement;
import org.lucoenergia.conluz.domain.admin.supply.SharingAgreementNotFoundException;
import org.lucoenergia.conluz.domain.admin.supply.update.UpdateSharingAgreementRepository;
import org.lucoenergia.conluz.infrastructure.admin.supply.SharingAgreementEntity;
import org.lucoenergia.conluz.infrastructure.admin.supply.SharingAgreementEntityMapper;
import org.lucoenergia.conluz.infrastructure.admin.supply.SharingAgreementRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Implementation of the repository for updating sharing agreements
 */
@Repository
@Transactional
public class UpdateSharingAgreementRepositoryDatabase implements UpdateSharingAgreementRepository {

    private final SharingAgreementRepository repository;
    private final SharingAgreementEntityMapper mapper;

    public UpdateSharingAgreementRepositoryDatabase(SharingAgreementRepository repository,
                                                   SharingAgreementEntityMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public SharingAgreement update(UUID id, LocalDate startDate, LocalDate endDate) {
        SharingAgreementEntity entity = repository.findById(id)
                .orElseThrow(() -> new SharingAgreementNotFoundException(org.lucoenergia.conluz.domain.admin.supply.SharingAgreementId.of(id)));
        
        entity.setStartDate(startDate);
        entity.setEndDate(endDate);
        
        SharingAgreementEntity savedEntity = repository.save(entity);
        return mapper.map(savedEntity);
    }
}
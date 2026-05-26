package org.lucoenergia.conluz.infrastructure.admin.supply.sharingagreement.get;

import org.lucoenergia.conluz.domain.admin.supply.sharingagreement.SharingAgreement;
import org.lucoenergia.conluz.domain.admin.supply.sharingagreement.SharingAgreementId;
import org.lucoenergia.conluz.domain.admin.supply.sharingagreement.GetSharingAgreementRepository;
import org.lucoenergia.conluz.infrastructure.admin.supply.sharingagreement.SharingAgreementEntity;
import org.lucoenergia.conluz.infrastructure.admin.supply.sharingagreement.SharingAgreementEntityMapper;
import org.lucoenergia.conluz.infrastructure.admin.supply.sharingagreement.SharingAgreementRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

@Transactional
@Repository
public class GetSharingAgreementRepositoryDatabase implements GetSharingAgreementRepository {

    private final SharingAgreementRepository sharingAgreementRepository;
    private final SharingAgreementEntityMapper mapper;

    public GetSharingAgreementRepositoryDatabase(SharingAgreementRepository sharingAgreementRepository,
                                                 SharingAgreementEntityMapper mapper) {
        this.mapper = mapper;
        this.sharingAgreementRepository = sharingAgreementRepository;
    }

    @Override
    public Optional<SharingAgreement> findById(SharingAgreementId id) {
        Optional<SharingAgreementEntity> entity = sharingAgreementRepository.findById(id.getId());
        return entity.map(mapper::map);
    }

    @Override
    public Optional<SharingAgreement> findFirstByEndDateIsNull() {
        Optional<SharingAgreementEntity> entity = sharingAgreementRepository.findFirstByEndDateIsNull();
        return entity.map(mapper::map);
    }

    @Override
    public Optional<SharingAgreement> findFirstByEndDate(LocalDate date) {
        Optional<SharingAgreementEntity> entity = sharingAgreementRepository.findFirstByEndDate(date);
        return entity.map(mapper::map);
    }
}

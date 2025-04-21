package org.lucoenergia.conluz.infrastructure.admin.supply.get;

import org.lucoenergia.conluz.domain.admin.supply.SharingAgreement;
import org.lucoenergia.conluz.domain.admin.supply.SharingAgreementId;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSharingAgreementRepository;
import org.lucoenergia.conluz.infrastructure.admin.supply.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

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
}

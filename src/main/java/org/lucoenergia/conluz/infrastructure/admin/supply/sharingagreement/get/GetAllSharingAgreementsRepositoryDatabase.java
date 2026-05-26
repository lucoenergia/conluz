package org.lucoenergia.conluz.infrastructure.admin.supply.sharingagreement.get;

import org.lucoenergia.conluz.domain.admin.supply.sharingagreement.SharingAgreement;
import org.lucoenergia.conluz.domain.admin.supply.sharingagreement.GetAllSharingAgreementsRepository;
import org.lucoenergia.conluz.infrastructure.admin.supply.sharingagreement.SharingAgreementEntityMapper;
import org.lucoenergia.conluz.infrastructure.admin.supply.sharingagreement.SharingAgreementRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
@Transactional(readOnly = true)
public class GetAllSharingAgreementsRepositoryDatabase implements GetAllSharingAgreementsRepository {

    private final SharingAgreementRepository sharingAgreementRepository;
    private final SharingAgreementEntityMapper mapper;

    public GetAllSharingAgreementsRepositoryDatabase(SharingAgreementRepository sharingAgreementRepository,
                                                     SharingAgreementEntityMapper mapper) {
        this.sharingAgreementRepository = sharingAgreementRepository;
        this.mapper = mapper;
    }

    @Override
    public List<SharingAgreement> findAll() {
        return sharingAgreementRepository.findAllByOrderByStartDateDesc().stream()
                .map(mapper::map)
                .toList();
    }
}

package org.lucoenergia.conluz.infrastructure.admin.supply.get;

import org.lucoenergia.conluz.domain.admin.supply.SharingAgreement;
import org.lucoenergia.conluz.domain.admin.supply.get.GetAllSharingAgreementsRepository;
import org.lucoenergia.conluz.infrastructure.admin.supply.SharingAgreementEntityMapper;
import org.lucoenergia.conluz.infrastructure.admin.supply.SharingAgreementRepository;
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

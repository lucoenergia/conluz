package org.lucoenergia.conluz.infrastructure.admin.supply.sharingagreement.get;

import org.lucoenergia.conluz.domain.admin.supply.sharingagreement.SharingAgreement;
import org.lucoenergia.conluz.domain.admin.supply.sharingagreement.GetAllSharingAgreementsRepository;
import org.lucoenergia.conluz.domain.admin.supply.sharingagreement.GetAllSharingAgreementsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class GetAllSharingAgreementsServiceImpl implements GetAllSharingAgreementsService {

    private final GetAllSharingAgreementsRepository repository;

    public GetAllSharingAgreementsServiceImpl(GetAllSharingAgreementsRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<SharingAgreement> findAll() {
        return repository.findAll();
    }
}

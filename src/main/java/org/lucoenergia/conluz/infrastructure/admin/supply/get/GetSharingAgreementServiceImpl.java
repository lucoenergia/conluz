package org.lucoenergia.conluz.infrastructure.admin.supply.get;

import org.lucoenergia.conluz.domain.admin.supply.SharingAgreement;
import org.lucoenergia.conluz.domain.admin.supply.SharingAgreementId;
import org.lucoenergia.conluz.domain.admin.supply.SharingAgreementNotFoundException;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSharingAgreementRepository;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSharingAgreementService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of the service for retrieving sharing agreements
 */
@Service
@Transactional(readOnly = true)
public class GetSharingAgreementServiceImpl implements GetSharingAgreementService {

    private final GetSharingAgreementRepository repository;

    public GetSharingAgreementServiceImpl(GetSharingAgreementRepository repository) {
        this.repository = repository;
    }

    @Override
    public SharingAgreement getById(SharingAgreementId id) {
        return repository.findById(id)
                .orElseThrow(() -> new SharingAgreementNotFoundException(id));
    }
}
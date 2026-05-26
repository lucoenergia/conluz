package org.lucoenergia.conluz.infrastructure.admin.supply.sharingagreement.get;

import org.lucoenergia.conluz.domain.admin.supply.sharingagreement.SharingAgreement;
import org.lucoenergia.conluz.domain.admin.supply.sharingagreement.SharingAgreementId;
import org.lucoenergia.conluz.domain.admin.supply.sharingagreement.SharingAgreementNotFoundException;
import org.lucoenergia.conluz.domain.admin.supply.sharingagreement.GetSharingAgreementRepository;
import org.lucoenergia.conluz.domain.admin.supply.sharingagreement.GetSharingAgreementService;
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
package org.lucoenergia.conluz.infrastructure.admin.supply.delete;

import org.lucoenergia.conluz.domain.admin.supply.SharingAgreementId;
import org.lucoenergia.conluz.domain.admin.supply.delete.DeleteSharingAgreementRepository;
import org.lucoenergia.conluz.domain.admin.supply.delete.DeleteSharingAgreementService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of the service for deleting sharing agreements
 */
@Service
@Transactional
public class DeleteSharingAgreementServiceImpl implements DeleteSharingAgreementService {

    private final DeleteSharingAgreementRepository repository;

    public DeleteSharingAgreementServiceImpl(DeleteSharingAgreementRepository repository) {
        this.repository = repository;
    }

    @Override
    public void delete(SharingAgreementId id) {
        repository.delete(id);
    }
}
package org.lucoenergia.conluz.infrastructure.admin.supply.delete;

import org.lucoenergia.conluz.domain.admin.supply.SharingAgreementId;
import org.lucoenergia.conluz.domain.admin.supply.SharingAgreementNotFoundException;
import org.lucoenergia.conluz.domain.admin.supply.delete.DeleteSharingAgreementRepository;
import org.lucoenergia.conluz.infrastructure.admin.supply.SharingAgreementEntity;
import org.lucoenergia.conluz.infrastructure.admin.supply.SharingAgreementRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Implementation of the repository for deleting sharing agreements
 */
@Repository
@Transactional
public class DeleteSharingAgreementRepositoryDatabase implements DeleteSharingAgreementRepository {

    private final SharingAgreementRepository repository;

    public DeleteSharingAgreementRepositoryDatabase(SharingAgreementRepository repository) {
        this.repository = repository;
    }

    @Override
    public void delete(SharingAgreementId id) {
        Optional<SharingAgreementEntity> entity = repository.findById(id.getId());
        if (entity.isEmpty()) {
            throw new SharingAgreementNotFoundException(id);
        }
        repository.deleteById(id.getId());
    }
}
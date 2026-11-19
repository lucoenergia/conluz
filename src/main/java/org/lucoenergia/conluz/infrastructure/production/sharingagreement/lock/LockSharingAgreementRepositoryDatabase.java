package org.lucoenergia.conluz.infrastructure.production.sharingagreement.lock;

import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementStatus;
import org.lucoenergia.conluz.domain.production.sharingagreement.lock.LockSharingAgreementRepository;
import org.lucoenergia.conluz.infrastructure.production.sharingagreement.SharingAgreementRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class LockSharingAgreementRepositoryDatabase implements LockSharingAgreementRepository {

    private final SharingAgreementRepository repository;

    public LockSharingAgreementRepositoryDatabase(SharingAgreementRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<SharingAgreementStatus> lockForCoefficients(UUID sharingAgreementId) {
        return repository.lockForCoefficientsById(sharingAgreementId).map(SharingAgreementStatus::valueOf);
    }
}

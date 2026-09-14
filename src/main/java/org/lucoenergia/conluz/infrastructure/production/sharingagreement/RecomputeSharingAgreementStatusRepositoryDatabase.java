package org.lucoenergia.conluz.infrastructure.production.sharingagreement;

import org.lucoenergia.conluz.domain.production.sharingagreement.RecomputeSharingAgreementStatusRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Transactional
@Repository
public class RecomputeSharingAgreementStatusRepositoryDatabase implements RecomputeSharingAgreementStatusRepository {

    private final SharingAgreementRepository jpaRepository;

    public RecomputeSharingAgreementStatusRepositoryDatabase(SharingAgreementRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public void recomputeStatus(UUID sharingAgreementId) {
        jpaRepository.recomputeStatus(sharingAgreementId);
    }
}

package org.lucoenergia.conluz.infrastructure.production.sharingagreement.revert;

import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.GetSupplyPartitionCoefficientRepository;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreement;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementHasAppliedCoefficientsException;
import org.lucoenergia.conluz.domain.production.sharingagreement.get.GetSharingAgreementService;
import org.lucoenergia.conluz.domain.production.sharingagreement.revert.RevertSharingAgreementToDraftRepository;
import org.lucoenergia.conluz.domain.production.sharingagreement.revert.RevertSharingAgreementToDraftService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Transactional
@Service
public class RevertSharingAgreementToDraftServiceImpl implements RevertSharingAgreementToDraftService {

    private final GetSharingAgreementService getSharingAgreementService;
    private final GetSupplyPartitionCoefficientRepository supplyPartitionCoefficientRepository;
    private final RevertSharingAgreementToDraftRepository repository;

    public RevertSharingAgreementToDraftServiceImpl(GetSharingAgreementService getSharingAgreementService,
                                                      GetSupplyPartitionCoefficientRepository supplyPartitionCoefficientRepository,
                                                      RevertSharingAgreementToDraftRepository repository) {
        this.getSharingAgreementService = getSharingAgreementService;
        this.supplyPartitionCoefficientRepository = supplyPartitionCoefficientRepository;
        this.repository = repository;
    }

    @Override
    public SharingAgreement revertToDraft(UUID plantId, UUID sharingAgreementId) {
        SharingAgreement agreement = getSharingAgreementService.findById(sharingAgreementId);
        agreement.assertPublished();
        if (supplyPartitionCoefficientRepository.existsBySharingAgreementIdAndValidFromIsNotNull(sharingAgreementId)) {
            throw new SharingAgreementHasAppliedCoefficientsException(sharingAgreementId);
        }
        // The checks above cover the common case with a clean, correctly-typed exception. The actual
        // write below re-verifies both preconditions atomically (see RevertSharingAgreementToDraftRepositoryDatabase)
        // to close the race window a concurrent coefficient activation could otherwise slip through
        // between the checks above and the write.
        return repository.revertToDraft(plantId, sharingAgreementId);
    }
}

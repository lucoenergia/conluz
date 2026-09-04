package org.lucoenergia.conluz.infrastructure.production.sharingagreement.revert;

import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.GetSupplyPartitionCoefficientRepository;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreement;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementHasAppliedCoefficientsException;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementNotFoundException;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementNotRevertibleException;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementStatus;
import org.lucoenergia.conluz.domain.production.sharingagreement.revert.RevertSharingAgreementToDraftRepository;
import org.lucoenergia.conluz.domain.production.sharingagreement.sharingagreementfile.GetSharingAgreementFileSummaryRepository;
import org.lucoenergia.conluz.infrastructure.production.sharingagreement.SharingAgreementEntity;
import org.lucoenergia.conluz.infrastructure.production.sharingagreement.SharingAgreementEntityMapper;
import org.lucoenergia.conluz.infrastructure.production.sharingagreement.SharingAgreementRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Transactional
@Repository
public class RevertSharingAgreementToDraftRepositoryDatabase implements RevertSharingAgreementToDraftRepository {

    private final SharingAgreementRepository sharingAgreementRepository;
    private final GetSharingAgreementFileSummaryRepository fileSummaryRepository;
    private final GetSupplyPartitionCoefficientRepository supplyPartitionCoefficientRepository;
    private final SharingAgreementEntityMapper mapper;

    public RevertSharingAgreementToDraftRepositoryDatabase(SharingAgreementRepository sharingAgreementRepository,
                                                             GetSharingAgreementFileSummaryRepository fileSummaryRepository,
                                                             GetSupplyPartitionCoefficientRepository supplyPartitionCoefficientRepository,
                                                             SharingAgreementEntityMapper mapper) {
        this.sharingAgreementRepository = sharingAgreementRepository;
        this.fileSummaryRepository = fileSummaryRepository;
        this.supplyPartitionCoefficientRepository = supplyPartitionCoefficientRepository;
        this.mapper = mapper;
    }

    @Override
    public SharingAgreement revertToDraft(UUID plantId, UUID sharingAgreementId) {
        int updated = sharingAgreementRepository.revertToDraftIfEligible(sharingAgreementId, plantId);
        if (updated == 1) {
            SharingAgreementEntity entity = sharingAgreementRepository.findById(sharingAgreementId)
                    .orElseThrow(() -> new SharingAgreementNotFoundException(sharingAgreementId));
            SharingAgreement agreement = mapper.map(entity);
            return agreement.withFile(fileSummaryRepository.findLatestBySharingAgreementId(agreement.getId()).orElse(null));
        }

        // The atomic UPDATE above found no eligible row. The service already checked the same
        // preconditions before calling this method, so reaching here means either the target
        // never existed/didn't match this plant, or a concurrent change (e.g. a coefficient
        // activation) invalidated the precondition between the service's check and this UPDATE.
        // This re-read is only to pick the right exception to report -- the UPDATE already decided
        // the actual outcome atomically.
        SharingAgreementEntity entity = sharingAgreementRepository.findById(sharingAgreementId)
                .filter(e -> plantId.equals(e.getPlant().getId()))
                .orElseThrow(() -> new SharingAgreementNotFoundException(sharingAgreementId));
        if (entity.getStatus() != SharingAgreementStatus.PUBLISHED) {
            throw new SharingAgreementNotRevertibleException(sharingAgreementId, entity.getStatus());
        }
        if (supplyPartitionCoefficientRepository.existsBySharingAgreementIdAndValidFromIsNotNull(sharingAgreementId)) {
            throw new SharingAgreementHasAppliedCoefficientsException(sharingAgreementId);
        }
        // PUBLISHED and inert per this re-read, yet the UPDATE affected no row: something changed
        // between the check and the write (e.g. a concurrent revert already won the race). The UPDATE
        // is the source of truth for the outcome; report the wrong-status rejection rather than
        // silently retrying.
        throw new SharingAgreementNotRevertibleException(sharingAgreementId, entity.getStatus());
    }
}

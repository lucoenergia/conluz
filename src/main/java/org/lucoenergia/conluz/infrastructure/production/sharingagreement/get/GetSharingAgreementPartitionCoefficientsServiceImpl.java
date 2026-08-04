package org.lucoenergia.conluz.infrastructure.production.sharingagreement.get;

import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.CoefficientApplicationState;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.CoefficientEndState;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.CoefficientSuccessionCascade;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.GetSupplyPartitionCoefficientRepository;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyPartitionCoefficient;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreement;
import org.lucoenergia.conluz.domain.production.sharingagreement.get.GetSharingAgreementPartitionCoefficientsService;
import org.lucoenergia.conluz.domain.production.sharingagreement.get.GetSharingAgreementRepository;
import org.lucoenergia.conluz.domain.production.sharingagreement.get.GetSharingAgreementService;
import org.lucoenergia.conluz.domain.production.sharingagreement.get.SharingAgreementCoefficient;
import org.lucoenergia.conluz.domain.production.sharingagreement.sharingagreementfile.SharingAgreementPlantMismatchException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class GetSharingAgreementPartitionCoefficientsServiceImpl implements GetSharingAgreementPartitionCoefficientsService {

    private final GetSharingAgreementService getSharingAgreementService;
    private final GetSharingAgreementRepository getSharingAgreementRepository;
    private final GetSupplyPartitionCoefficientRepository getCoefficientRepository;
    private final GetSupplyRepository getSupplyRepository;

    public GetSharingAgreementPartitionCoefficientsServiceImpl(GetSharingAgreementService getSharingAgreementService,
                                                                 GetSharingAgreementRepository getSharingAgreementRepository,
                                                                 GetSupplyPartitionCoefficientRepository getCoefficientRepository,
                                                                 GetSupplyRepository getSupplyRepository) {
        this.getSharingAgreementService = getSharingAgreementService;
        this.getSharingAgreementRepository = getSharingAgreementRepository;
        this.getCoefficientRepository = getCoefficientRepository;
        this.getSupplyRepository = getSupplyRepository;
    }

    @Override
    public List<SharingAgreementCoefficient> findBySharingAgreementId(UUID plantId, UUID sharingAgreementId) {
        SharingAgreement agreement = getSharingAgreementService.findById(sharingAgreementId);
        if (!agreement.getPlantId().equals(plantId)) {
            throw new SharingAgreementPlantMismatchException(sharingAgreementId, plantId);
        }

        List<SupplyPartitionCoefficient> coefficients = getCoefficientRepository.findAllBySharingAgreementId(sharingAgreementId);
        if (coefficients.isEmpty()) {
            return List.of();
        }

        Map<UUID, Supply> suppliesById = getSupplyRepository.findAllByIds(
                        coefficients.stream().map(SupplyPartitionCoefficient::getSupplyId).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(Supply::getId, supply -> supply));

        // Constant for the whole agreement (does not depend on supplyId): computed once so an open
        // row with no successor (the common case for a freshly-published agreement) resolves to
        // OPEN directly, without a per-supply lookup.
        boolean laterAgreementExists = getSharingAgreementRepository.existsLaterNonDraftAgreement(
                plantId, sharingAgreementId, agreement.getCreatedAt());

        return coefficients.stream()
                .map(coefficient -> toView(plantId, agreement, coefficient, suppliesById.get(coefficient.getSupplyId()),
                        laterAgreementExists))
                .sorted(Comparator.comparing(SharingAgreementCoefficient::getSupplyCode))
                .toList();
    }

    private SharingAgreementCoefficient toView(UUID plantId, SharingAgreement agreement,
                                               SupplyPartitionCoefficient coefficient, Supply supply,
                                               boolean laterAgreementExists) {
        CoefficientApplicationState applicationState = coefficient.getValidFrom() == null
                ? CoefficientApplicationState.PENDING : CoefficientApplicationState.APPLIED;

        CoefficientEndState endState;
        Instant endDate;
        if (coefficient.getValidTo() != null) {
            Optional<SupplyPartitionCoefficient> nextActivated = getCoefficientRepository.findNextActivatedAfter(
                    plantId, coefficient.getSupplyId(), coefficient.getId(), coefficient.getValidFrom());
            endState = CoefficientSuccessionCascade.isValidToCascadeDerived(coefficient, nextActivated)
                    ? CoefficientEndState.DERIVED : CoefficientEndState.CLOSED;
            endDate = coefficient.getValidTo();
        } else if (!laterAgreementExists) {
            endState = CoefficientEndState.OPEN;
            endDate = null;
        } else {
            Optional<SupplyPartitionCoefficient> next = getCoefficientRepository.findNextCoefficientForSupplyInLaterAgreement(
                    plantId, coefficient.getSupplyId(), agreement.getId(), agreement.getCreatedAt());
            if (next.isEmpty()) {
                endState = CoefficientEndState.OPEN_ORPHAN;
                endDate = null;
            } else if (next.get().getValidFrom() != null) {
                // Structurally unreachable via this application's own write paths: an activated
                // "next" row here would mean two overlapping open-ended activated coefficients for
                // the same (plantId, supplyId) -- the no_overlapping_coefficients exclusion
                // constraint (and the setValidFrom cascade that closes a predecessor when its
                // successor activates) makes that impossible. Kept for defensive completeness of the
                // enum's contract; exercised only at the unit level (mocked repositories), not by an
                // integration fixture.
                endState = CoefficientEndState.DERIVED;
                endDate = next.get().getValidFrom();
            } else {
                endState = CoefficientEndState.PENDING_SUCCESSION;
                endDate = null;
            }
        }

        return new SharingAgreementCoefficient(coefficient, supply, applicationState, endState, endDate);
    }
}

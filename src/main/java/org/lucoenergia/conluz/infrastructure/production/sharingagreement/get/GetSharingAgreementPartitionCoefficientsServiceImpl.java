package org.lucoenergia.conluz.infrastructure.production.sharingagreement.get;

import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.CoefficientApplicationState;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.GetSupplyPartitionCoefficientRepository;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyPartitionCoefficient;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyPartitionCoefficientDetail;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreement;
import org.lucoenergia.conluz.domain.production.sharingagreement.get.CoefficientEndStateDerivation;
import org.lucoenergia.conluz.domain.production.sharingagreement.get.GetSharingAgreementPartitionCoefficientsService;
import org.lucoenergia.conluz.domain.production.sharingagreement.get.GetSharingAgreementRepository;
import org.lucoenergia.conluz.domain.production.sharingagreement.get.GetSharingAgreementService;
import org.lucoenergia.conluz.domain.production.sharingagreement.get.SharingAgreementCoefficient;
import org.lucoenergia.conluz.domain.production.sharingagreement.sharingagreementfile.SharingAgreementPlantMismatchException;
import org.lucoenergia.conluz.domain.shared.time.ZoneResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The coefficients of one sharing agreement, each with how it ends ({@link CoefficientEndStateDerivation}).
 *
 * <h2>Query cost</h2>
 *
 * <p>Constant, whatever the number of supplies: the agreement, every coefficient of its plant, the supplies, their
 * current coefficients, and the plant's agreements. End states are derived in memory from those, never with a
 * query per row.</p>
 */
@Service
@Transactional(readOnly = true)
public class GetSharingAgreementPartitionCoefficientsServiceImpl implements GetSharingAgreementPartitionCoefficientsService {

    private final GetSharingAgreementService getSharingAgreementService;
    private final GetSharingAgreementRepository getSharingAgreementRepository;
    private final GetSupplyPartitionCoefficientRepository getCoefficientRepository;
    private final GetSupplyRepository getSupplyRepository;
    private final ZoneResolver zoneResolver;

    public GetSharingAgreementPartitionCoefficientsServiceImpl(GetSharingAgreementService getSharingAgreementService,
                                                                 GetSharingAgreementRepository getSharingAgreementRepository,
                                                                 GetSupplyPartitionCoefficientRepository getCoefficientRepository,
                                                                 GetSupplyRepository getSupplyRepository,
                                                                 ZoneResolver zoneResolver) {
        this.getSharingAgreementService = getSharingAgreementService;
        this.getSharingAgreementRepository = getSharingAgreementRepository;
        this.getCoefficientRepository = getCoefficientRepository;
        this.getSupplyRepository = getSupplyRepository;
        this.zoneResolver = zoneResolver;
    }

    @Override
    public List<SharingAgreementCoefficient> findBySharingAgreementId(UUID plantId, UUID sharingAgreementId) {
        SharingAgreement agreement = getSharingAgreementService.findById(sharingAgreementId);
        if (!agreement.getPlantId().equals(plantId)) {
            throw new SharingAgreementPlantMismatchException(sharingAgreementId, plantId);
        }

        List<SupplyPartitionCoefficient> plantCoefficients = getCoefficientRepository.findAllByPlantId(plantId);
        List<SupplyPartitionCoefficient> coefficients = plantCoefficients.stream()
                .filter(coefficient -> sharingAgreementId.equals(coefficient.getSharingAgreementId()))
                .toList();
        if (coefficients.isEmpty()) {
            return List.of();
        }

        Set<UUID> supplyIds = coefficients.stream()
                .map(SupplyPartitionCoefficient::getSupplyId)
                .collect(Collectors.toSet());

        Map<UUID, Supply> suppliesById = getSupplyRepository.findAllByIds(supplyIds)
                .stream()
                .collect(Collectors.toMap(Supply::getId, supply -> supply));

        // One query for the whole set, not one per row. Scoped to this agreement's plant and to open
        // activated rows, so the no_overlapping_coefficients exclusion constraint guarantees at most
        // one per supply and the map cannot collide.
        Map<UUID, SupplyPartitionCoefficientDetail> currentBySupplyId = getCoefficientRepository
                .findActiveDetailsByPlantIdAndSupplyIdIn(plantId, supplyIds)
                .stream()
                .collect(Collectors.toMap(detail -> detail.getSupply().id(), detail -> detail));

        // The application zone, as the date writes of this plant's coefficients use: this read has no community
        // in reach without one more query.
        CoefficientEndStateDerivation endStates = new CoefficientEndStateDerivation(agreement,
                getSharingAgreementRepository.findByPlantId(plantId, null), plantCoefficients,
                zoneResolver.resolveApplicationZoneId());

        return coefficients.stream()
                .map(coefficient -> map(coefficient, suppliesById.get(coefficient.getSupplyId()), endStates,
                        currentBySupplyId.get(coefficient.getSupplyId())))
                .sorted(Comparator.comparing(SharingAgreementCoefficient::getSupplyCode))
                .toList();
    }

    private SharingAgreementCoefficient map(SupplyPartitionCoefficient coefficient, Supply supply,
                                            CoefficientEndStateDerivation endStates,
                                            SupplyPartitionCoefficientDetail currentCoefficient) {
        CoefficientApplicationState applicationState = coefficient.getValidFrom() == null
                ? CoefficientApplicationState.PENDING : CoefficientApplicationState.APPLIED;
        CoefficientEndStateDerivation.EndOfCoverage end = endStates.derive(coefficient);
        return new SharingAgreementCoefficient(coefficient, supply, applicationState, end.state(), end.endDate(),
                currentCoefficient);
    }
}

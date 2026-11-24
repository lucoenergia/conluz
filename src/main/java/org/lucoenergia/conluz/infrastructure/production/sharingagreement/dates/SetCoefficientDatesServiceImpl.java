package org.lucoenergia.conluz.infrastructure.production.sharingagreement.dates;

import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.CoefficientOverlapCheckRepository;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.GetSupplyPartitionCoefficientRepository;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SaveSupplyPartitionCoefficientRepository;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyPartitionCoefficient;
import org.lucoenergia.conluz.domain.production.plant.PlantNotFoundException;
import org.lucoenergia.conluz.domain.production.plant.lock.LockPlantRepository;
import org.lucoenergia.conluz.domain.production.sharingagreement.RecomputeSharingAgreementStatusRepository;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreement;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementStatus;
import org.lucoenergia.conluz.domain.production.sharingagreement.dates.CoefficientDatesEffects;
import org.lucoenergia.conluz.domain.production.sharingagreement.dates.CoefficientDatesEffects.AgreementStatusChange;
import org.lucoenergia.conluz.domain.production.sharingagreement.dates.CoefficientDatesEffects.CoefficientPeriodChange;
import org.lucoenergia.conluz.domain.production.sharingagreement.dates.CoefficientDatesEffects.CommunityStartChange;
import org.lucoenergia.conluz.domain.production.sharingagreement.dates.CoefficientDatesEffects.LeavingClosureChange;
import org.lucoenergia.conluz.domain.production.sharingagreement.dates.CoefficientDatesEffects.NeighbourChange;
import org.lucoenergia.conluz.domain.production.sharingagreement.dates.CoefficientDatesOutcome;
import org.lucoenergia.conluz.domain.production.sharingagreement.dates.CoefficientDatesRequest;
import org.lucoenergia.conluz.domain.production.sharingagreement.dates.SetCoefficientDatesService;
import org.lucoenergia.conluz.domain.production.sharingagreement.get.GetSharingAgreementRepository;
import org.lucoenergia.conluz.domain.shared.PlantId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Transactional
@Service
public class SetCoefficientDatesServiceImpl implements SetCoefficientDatesService {

    private final LockPlantRepository lockPlantRepository;
    private final CoefficientDatesPlanner planner;
    private final SaveSupplyPartitionCoefficientRepository saveCoefficientRepository;
    private final CoefficientOverlapCheckRepository overlapCheckRepository;
    private final RecomputeSharingAgreementStatusRepository recomputeStatusRepository;
    private final GetSharingAgreementRepository getSharingAgreementRepository;
    private final GetSupplyPartitionCoefficientRepository getCoefficientRepository;

    public SetCoefficientDatesServiceImpl(LockPlantRepository lockPlantRepository,
                                          CoefficientDatesPlanner planner,
                                          SaveSupplyPartitionCoefficientRepository saveCoefficientRepository,
                                          CoefficientOverlapCheckRepository overlapCheckRepository,
                                          RecomputeSharingAgreementStatusRepository recomputeStatusRepository,
                                          GetSharingAgreementRepository getSharingAgreementRepository,
                                          GetSupplyPartitionCoefficientRepository getCoefficientRepository) {
        this.lockPlantRepository = lockPlantRepository;
        this.planner = planner;
        this.saveCoefficientRepository = saveCoefficientRepository;
        this.overlapCheckRepository = overlapCheckRepository;
        this.recomputeStatusRepository = recomputeStatusRepository;
        this.getSharingAgreementRepository = getSharingAgreementRepository;
        this.getCoefficientRepository = getCoefficientRepository;
    }

    @Override
    public CoefficientDatesOutcome setDates(UUID plantId, UUID sharingAgreementId, CoefficientDatesRequest request) {
        // Before anything is read, so this write plans on the timeline the previous one left (SHA-007).
        if (!lockPlantRepository.lockForCoefficientDates(PlantId.of(plantId))) {
            throw new PlantNotFoundException(PlantId.of(plantId));
        }
        CoefficientDatesPlanner.Prepared prepared = planner.plan(plantId, sharingAgreementId, request);
        CoefficientDatesEffects planned = prepared.planned().effects();

        Map<UUID, SupplyPartitionCoefficient> written = new LinkedHashMap<>();
        for (SupplyPartitionCoefficient write : prepared.planned().writes()) {
            SupplyPartitionCoefficient saved = saveCoefficientRepository.save(write);
            written.put(saved.getId(), saved);
        }
        if (!written.isEmpty()) {
            overlapCheckRepository.flushAndCheckNoOverlap();
        }
        // Once per distinct agreement, never once per coefficient (SHA-006).
        planned.agreements().forEach(change -> recomputeStatusRepository.recomputeStatus(change.sharingAgreementId()));

        return planner.outcome(asWritten(planned, written, prepared.communityId()), prepared.agreements());
    }

    /**
     * The planned effects with every "after" value read back from what was stored: periods from the saved rows, the
     * statuses the recompute wrote and the community start the database now holds.
     */
    private CoefficientDatesEffects asWritten(CoefficientDatesEffects planned, Map<UUID, SupplyPartitionCoefficient> written,
                                              UUID communityId) {
        Map<UUID, SharingAgreementStatus> statuses = getSharingAgreementRepository.findAllByIdsWithoutFile(
                        planned.agreements().stream().map(AgreementStatusChange::sharingAgreementId).toList())
                .stream()
                .collect(Collectors.toMap(SharingAgreement::getId, SharingAgreement::getStatus));
        Instant communityStart = getCoefficientRepository.findEarliestValidFromByCommunityId(communityId).orElse(null);

        List<CoefficientPeriodChange> coefficients = planned.coefficients().stream()
                .map(change -> new CoefficientPeriodChange(change.coefficientId(), change.supplyId(),
                        change.validFromBefore(), change.validToBefore(),
                        written.get(change.coefficientId()).getValidFrom(),
                        written.get(change.coefficientId()).getValidTo(), change.leavesDistribution()))
                .toList();
        List<NeighbourChange> neighbours = planned.neighbours().stream()
                .map(change -> new NeighbourChange(change.coefficientId(), change.supplyId(),
                        change.sharingAgreementId(), change.validToBefore(),
                        written.get(change.coefficientId()).getValidTo()))
                .toList();
        List<LeavingClosureChange> closures = planned.leavingClosures().stream()
                .map(change -> new LeavingClosureChange(change.supplyId(), change.coefficientId(),
                        change.sharingAgreementId(), change.validToBefore(),
                        written.get(change.coefficientId()).getValidTo(), change.source()))
                .toList();
        List<AgreementStatusChange> agreements = planned.agreements().stream()
                .map(change -> new AgreementStatusChange(change.sharingAgreementId(), change.statusBefore(),
                        statuses.get(change.sharingAgreementId())))
                .toList();
        return new CoefficientDatesEffects(coefficients, neighbours, closures, agreements,
                new CommunityStartChange(planned.communityStart().before(), communityStart));
    }
}

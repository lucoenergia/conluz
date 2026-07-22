package org.lucoenergia.conluz.infrastructure.admin.supply.partitioncoefficient;

import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.GetSupplyPartitionCoefficientRepository;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.PartitionCoefficientService;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SaveSupplyPartitionCoefficientRepository;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyPartitionCoefficient;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyPartitionCoefficientNotFoundException;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.plant.get.GetPlantRepository;
import org.lucoenergia.conluz.domain.production.plant.get.GetSharingAgreementRepository;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Transactional
@Service
public class PartitionCoefficientServiceImpl implements PartitionCoefficientService {

    private final GetSupplyPartitionCoefficientRepository repository;
    private final SaveSupplyPartitionCoefficientRepository saveRepository;
    private final GetSupplyRepository getSupplyRepository;
    private final GetPlantRepository getPlantRepository;
    private final GetSharingAgreementRepository getSharingAgreementRepository;

    public PartitionCoefficientServiceImpl(GetSupplyPartitionCoefficientRepository repository,
                                           SaveSupplyPartitionCoefficientRepository saveRepository,
                                           GetSupplyRepository getSupplyRepository,
                                           GetPlantRepository getPlantRepository,
                                           GetSharingAgreementRepository getSharingAgreementRepository) {
        this.repository = repository;
        this.saveRepository = saveRepository;
        this.getSupplyRepository = getSupplyRepository;
        this.getPlantRepository = getPlantRepository;
        this.getSharingAgreementRepository = getSharingAgreementRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal findCoefficientByInstant(UUID supplyId, Instant timestamp) {
        return repository.findBySupplyIdAtTimestamp(supplyId, timestamp)
                .map(SupplyPartitionCoefficient::getCoefficient)
                .orElseThrow(() -> new SupplyPartitionCoefficientNotFoundException(supplyId, timestamp));
    }

    @Override
    @Transactional(readOnly = true)
    public List<SupplyPartitionCoefficient> findAllCoefficientsInRange(UUID supplyId, Instant from, Instant to) {
        List<SupplyPartitionCoefficient> periods = repository.findBySupplyIdInRange(supplyId, from, to);
        return periods.stream()
                .map(period -> clipToRange(period, from, to))
                .collect(Collectors.toList());
    }

    @Override
    public SupplyPartitionCoefficient registerCoefficientChange(UUID supplyId, BigDecimal newCoefficient,
                                                                Instant effectiveAt) {
        Supply supply = getSupplyRepository.findById(SupplyId.of(supplyId))
                .orElseThrow(() -> new SupplyNotFoundException(SupplyId.of(supplyId)));

        // STILL INTERIM (phase 2d), open decision owned by phase 5d: plant and sharing-agreement are
        // inferred here because neither the single-supply endpoint (RegisterPartitionCoefficientController)
        // nor the bulk-import path (RegisterPartitionCoefficientsWithFileController /
        // RegisterPartitionCoefficientInBulkServiceImpl) carries that context in its request shape.
        // This is scaffolding, not a permanent design -- its disposition (redesign to pass
        // plantId/sharingAgreementId explicitly, or removal alongside the two endpoints themselves)
        // is an open decision for phase 5d, which already owns those endpoints.
        //
        // Known consequence for 5d to weigh: this method calls neither assertDraft() nor checks
        // SharingAgreementStatus at all, so the two legacy endpoints above can currently write an
        // active (non-null validFrom) coefficient row against a PUBLISHED agreement -- bypassing the
        // immutability guarantee assertDraft() enforces everywhere else on the sharing-agreement
        // write surface (phase 5b's patch/delete/publish, and phase 5c's file-upload/PUT endpoints).
        // This is a pre-existing gap, not one phase 5c introduces, but it must not be read as settled.
        //
        // This method is unrelated to, and never called by,
        // MaterializeSharingAgreementCoefficientsService (phase 5c), which never sets a non-null
        // validFrom -- it only ever writes pending rows.
        UUID communityId = supply.getCommunity().getId();
        Plant plant = getPlantRepository.findByCommunityId(communityId)
                .orElseThrow(() -> new IllegalStateException(
                        "No plant for community " + communityId + " during interim phase-2d coefficient " +
                        "resolution; this is guaranteed by the phase-2d migration precondition and should be unreachable."));
        UUID plantId = plant.getId();
        UUID sharingAgreementId = getSharingAgreementRepository
                .findCurrentPublishedAgreementIdByPlantId(plantId)
                .orElseThrow(() -> new IllegalStateException(
                        "No PUBLISHED sharing agreement for plant " + plantId + " during interim phase-2d " +
                        "coefficient resolution; every plant with coefficients gets a synthetic PUBLISHED " +
                        "agreement in the phase-2d migration backfill, so this should be unreachable."));

        saveRepository.closeActivePeriod(supplyId, plantId, effectiveAt);

        SupplyPartitionCoefficient newPeriod = new SupplyPartitionCoefficient.Builder()
                .withId(UUID.randomUUID())
                .withSupplyId(supplyId)
                .withPlantId(plantId)
                .withSharingAgreementId(sharingAgreementId)
                .withCoefficient(newCoefficient)
                .withValidFrom(effectiveAt)
                .withValidTo(null)
                .withCreatedAt(Instant.now())
                .build();

        SupplyPartitionCoefficient saved = saveRepository.save(newPeriod);

        // Keep supply.partition_coefficient in sync with the active value
        saveRepository.syncSupplyPartitionCoefficient(supplyId, newCoefficient);

        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SupplyPartitionCoefficient> findAllCoefficientHistory(UUID supplyId) {
        return repository.findAllBySupplyIdOrderByValidFromAsc(supplyId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SupplyPartitionCoefficient> findActiveBySupplyId(UUID supplyId) {
        return repository.findActiveBySupplyId(supplyId);
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal computeCommunitySum(Instant timestamp) {
        return repository.findAllActiveAtTimestamp(timestamp).stream()
                .map(SupplyPartitionCoefficient::getCoefficient)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private SupplyPartitionCoefficient clipToRange(SupplyPartitionCoefficient period, Instant from, Instant to) {
        Instant clippedFrom = period.getValidFrom().isBefore(from) ? from : period.getValidFrom();
        Instant clippedTo = period.getValidTo() == null || period.getValidTo().isAfter(to) ? to : period.getValidTo();
        return new SupplyPartitionCoefficient.Builder()
                .withId(period.getId())
                .withSupplyId(period.getSupplyId())
                .withPlantId(period.getPlantId())
                .withSharingAgreementId(period.getSharingAgreementId())
                .withCoefficient(period.getCoefficient())
                .withValidFrom(clippedFrom)
                .withValidTo(clippedTo)
                .withCreatedAt(period.getCreatedAt())
                .build();
    }
}

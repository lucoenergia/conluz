package org.lucoenergia.conluz.infrastructure.admin.supply.partitioncoefficient;

import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.PartitionCoefficientService;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyPartitionCoefficient;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyPartitionCoefficientNotFoundException;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyPartitionCoefficientRepository;
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

    private final SupplyPartitionCoefficientRepository repository;
    private final GetSupplyRepository getSupplyRepository;
    private final GetPlantRepository getPlantRepository;
    private final GetSharingAgreementRepository getSharingAgreementRepository;

    public PartitionCoefficientServiceImpl(SupplyPartitionCoefficientRepository repository,
                                           GetSupplyRepository getSupplyRepository,
                                           GetPlantRepository getPlantRepository,
                                           GetSharingAgreementRepository getSharingAgreementRepository) {
        this.repository = repository;
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

        // INTERIM (phase 2d): plant and sharing-agreement are inferred here because
        // neither the single-supply endpoint nor the bulk-import path carries that
        // context yet. Phase 3 (distributor-file-driven agreement creation) removes
        // this inference entirely: the caller will pass plantId/sharingAgreementId
        // explicitly and this block should be deleted then.
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

        repository.closeActivePeriod(supplyId, plantId, effectiveAt);

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

        SupplyPartitionCoefficient saved = repository.save(newPeriod);

        // Keep supply.partition_coefficient in sync with the active value
        repository.syncSupplyPartitionCoefficient(supplyId, newCoefficient);

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

package org.lucoenergia.conluz.infrastructure.admin.supply.partitioncoefficient;

import org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.PartitionCoefficientService;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyPartitionCoefficient;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyPartitionCoefficientNotFoundException;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyPartitionCoefficientRepository;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Transactional
@Service
public class PartitionCoefficientServiceImpl implements PartitionCoefficientService {

    private final SupplyPartitionCoefficientRepository repository;
    private final SupplyRepository supplyRepository;

    public PartitionCoefficientServiceImpl(SupplyPartitionCoefficientRepository repository,
                                           SupplyRepository supplyRepository) {
        this.repository = repository;
        this.supplyRepository = supplyRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal resolveCoefficient(UUID supplyId, Instant timestamp) {
        return repository.findBySupplyIdAtTimestamp(supplyId, timestamp)
                .map(SupplyPartitionCoefficient::getCoefficient)
                .orElseThrow(() -> new SupplyPartitionCoefficientNotFoundException(supplyId, timestamp));
    }

    @Override
    @Transactional(readOnly = true)
    public List<SupplyPartitionCoefficient> resolveCoefficientsInRange(UUID supplyId, Instant from, Instant to) {
        List<SupplyPartitionCoefficient> periods = repository.findBySupplyIdInRange(supplyId, from, to);
        return periods.stream()
                .map(period -> clipToRange(period, from, to))
                .collect(Collectors.toList());
    }

    @Override
    public SupplyPartitionCoefficient registerCoefficientChange(UUID supplyId, BigDecimal newCoefficient,
                                                                Instant effectiveAt) {
        supplyRepository.findById(supplyId)
                .orElseThrow(() -> new SupplyNotFoundException(SupplyId.of(supplyId)));

        repository.closeActivePeriod(supplyId, effectiveAt);

        SupplyPartitionCoefficient newPeriod = new SupplyPartitionCoefficient.Builder()
                .withId(UUID.randomUUID())
                .withSupplyId(supplyId)
                .withCoefficient(newCoefficient)
                .withValidFrom(effectiveAt)
                .withValidTo(null)
                .withCreatedAt(Instant.now())
                .build();

        SupplyPartitionCoefficient saved = repository.save(newPeriod);

        // Keep supply.partition_coefficient in sync with the active value
        supplyRepository.findById(supplyId).ifPresent(supply -> {
            supply.setPartitionCoefficient(newCoefficient.floatValue());
            supplyRepository.save(supply);
        });

        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SupplyPartitionCoefficient> getCoefficientHistory(UUID supplyId) {
        return repository.findAllBySupplyIdOrderByValidFromAsc(supplyId);
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
                .withCoefficient(period.getCoefficient())
                .withValidFrom(clippedFrom)
                .withValidTo(clippedTo)
                .withCreatedAt(period.getCreatedAt())
                .build();
    }
}

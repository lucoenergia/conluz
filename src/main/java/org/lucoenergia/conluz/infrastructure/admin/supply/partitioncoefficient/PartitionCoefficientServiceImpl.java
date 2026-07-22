package org.lucoenergia.conluz.infrastructure.admin.supply.partitioncoefficient;

import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.GetSupplyPartitionCoefficientRepository;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.PartitionCoefficientService;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyPartitionCoefficient;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyPartitionCoefficientNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Transactional(readOnly = true)
@Service
public class PartitionCoefficientServiceImpl implements PartitionCoefficientService {

    private final GetSupplyPartitionCoefficientRepository repository;

    public PartitionCoefficientServiceImpl(GetSupplyPartitionCoefficientRepository repository) {
        this.repository = repository;
    }

    @Override
    public BigDecimal findCoefficientByInstant(UUID supplyId, Instant timestamp) {
        return repository.findBySupplyIdAtTimestamp(supplyId, timestamp)
                .map(SupplyPartitionCoefficient::getCoefficient)
                .orElseThrow(() -> new SupplyPartitionCoefficientNotFoundException(supplyId, timestamp));
    }

    @Override
    public List<SupplyPartitionCoefficient> findAllCoefficientsInRange(UUID supplyId, Instant from, Instant to) {
        List<SupplyPartitionCoefficient> periods = repository.findBySupplyIdInRange(supplyId, from, to);
        return periods.stream()
                .map(period -> clipToRange(period, from, to))
                .collect(Collectors.toList());
    }

    @Override
    public List<SupplyPartitionCoefficient> findAllCoefficientHistory(UUID supplyId) {
        return repository.findAllBySupplyIdOrderByValidFromAsc(supplyId);
    }

    @Override
    public Optional<SupplyPartitionCoefficient> findActiveBySupplyId(UUID supplyId) {
        return repository.findActiveBySupplyId(supplyId);
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

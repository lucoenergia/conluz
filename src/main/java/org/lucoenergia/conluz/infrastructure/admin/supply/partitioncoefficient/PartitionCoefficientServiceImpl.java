package org.lucoenergia.conluz.infrastructure.admin.supply.partitioncoefficient;

import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.GetSupplyPartitionCoefficientRepository;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.PartitionCoefficientService;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyPartitionCoefficient;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyPartitionCoefficientDetail;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyPartitionCoefficientNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
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
    public List<SupplyPartitionCoefficientDetail> findAllCoefficientHistory(UUID supplyId) {
        return repository.findAllDetailsBySupplyId(supplyId, null);
    }

    @Override
    public List<SupplyPartitionCoefficientDetail> findActiveBySupplyId(UUID supplyId) {
        return repository.findActiveDetailsBySupplyId(supplyId, null);
    }

    @Override
    public List<SupplyPartitionCoefficientDetail> findDetailsInOrderOf(List<SupplyPartitionCoefficient> coefficients) {
        if (coefficients.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = coefficients.stream().map(SupplyPartitionCoefficient::getId).toList();
        Map<UUID, SupplyPartitionCoefficientDetail> byId = repository.findAllDetailsByIdIn(ids).stream()
                .collect(Collectors.toMap(SupplyPartitionCoefficientDetail::getId, Function.identity()));
        // SQL IN returns rows in whatever order the database finds them, so the caller's order is
        // reimposed here. Callers such as the activation endpoints document their order ("the
        // requested targets and any predecessor cascaded as a result"), so losing it would be a
        // silent contract change.
        return ids.stream().map(byId::get).filter(Objects::nonNull).toList();
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

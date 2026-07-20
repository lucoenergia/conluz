package org.lucoenergia.conluz.infrastructure.admin.supply.partitioncoefficient;

import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.CoefficientResolver;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.CoefficientSegment;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyPartitionCoefficient;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyPartitionCoefficientRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
@Transactional(readOnly = true)
public class CoefficientResolverDatabase implements CoefficientResolver {

    private final SupplyPartitionCoefficientRepository repository;

    public CoefficientResolverDatabase(SupplyPartitionCoefficientRepository repository) {
        this.repository = repository;
    }

    @Override
    public BigDecimal resolveCoefficient(UUID plantId, UUID supplyId, Instant instant) {
        return repository.findByPlantIdAndSupplyIdAtTimestamp(plantId, supplyId, instant)
                .filter(coefficient -> coefficient.getValidFrom() != null)
                .map(SupplyPartitionCoefficient::getCoefficient)
                .orElse(BigDecimal.ZERO);
    }

    @Override
    public Map<UUID, BigDecimal> resolveCoefficientsBySupplyAtInstant(UUID supplyId, Instant instant) {
        Map<UUID, BigDecimal> coefficientsByPlant = new HashMap<>();
        for (SupplyPartitionCoefficient coefficient : repository.findAllBySupplyIdAtTimestamp(supplyId, instant)) {
            if (coefficient.getValidFrom() == null) {
                continue;
            }
            coefficientsByPlant.put(coefficient.getPlantId(), coefficient.getCoefficient());
        }
        return coefficientsByPlant;
    }

    @Override
    public Map<UUID, List<CoefficientSegment>> resolveSegmentsBySupply(UUID supplyId, Instant from, Instant to) {
        Map<UUID, List<CoefficientSegment>> segmentsByPlant = new HashMap<>();
        for (SupplyPartitionCoefficient coefficient : repository.findBySupplyIdInRange(supplyId, from, to)) {
            if (coefficient.getValidFrom() == null) {
                continue;
            }
            Instant clampedFrom = coefficient.getValidFrom().isAfter(from) ? coefficient.getValidFrom() : from;
            Instant clampedTo = (coefficient.getValidTo() == null || coefficient.getValidTo().isAfter(to))
                    ? to : coefficient.getValidTo();
            if (!clampedFrom.isBefore(clampedTo)) {
                continue;
            }
            segmentsByPlant
                    .computeIfAbsent(coefficient.getPlantId(), id -> new ArrayList<>())
                    .add(new CoefficientSegment(clampedFrom, clampedTo, coefficient.getCoefficient()));
        }
        return segmentsByPlant;
    }
}

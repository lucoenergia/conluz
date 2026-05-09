package org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SupplyPartitionCoefficientRepository {

    Optional<SupplyPartitionCoefficient> findActiveBySupplyId(UUID supplyId);

    Optional<SupplyPartitionCoefficient> findBySupplyIdAtTimestamp(UUID supplyId, Instant timestamp);

    List<SupplyPartitionCoefficient> findBySupplyIdInRange(UUID supplyId, Instant from, Instant to);

    List<SupplyPartitionCoefficient> findAllBySupplyIdOrderByValidFromAsc(UUID supplyId);

    List<SupplyPartitionCoefficient> findAllActiveAtTimestamp(Instant timestamp);

    SupplyPartitionCoefficient save(SupplyPartitionCoefficient coefficient);

    void closeActivePeriod(UUID supplyId, Instant validTo);
}

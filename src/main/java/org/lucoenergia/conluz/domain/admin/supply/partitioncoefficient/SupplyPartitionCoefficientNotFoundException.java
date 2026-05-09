package org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient;

import java.time.Instant;
import java.util.UUID;

public class SupplyPartitionCoefficientNotFoundException extends RuntimeException {

    public SupplyPartitionCoefficientNotFoundException(UUID supplyId, Instant timestamp) {
        super("No partition coefficient found for supply " + supplyId + " at " + timestamp);
    }

    public SupplyPartitionCoefficientNotFoundException(UUID supplyId) {
        super("No partition coefficient history found for supply " + supplyId);
    }
}

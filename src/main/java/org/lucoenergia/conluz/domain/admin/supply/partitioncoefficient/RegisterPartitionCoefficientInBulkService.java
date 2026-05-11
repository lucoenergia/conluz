package org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient;

import org.lucoenergia.conluz.infrastructure.admin.supply.partitioncoefficient.RegisterPartitionCoefficientFileRow;
import org.lucoenergia.conluz.infrastructure.admin.supply.partitioncoefficient.RegisterPartitionCoefficientsWithFileResponse;

import java.time.Instant;
import java.util.List;

public interface RegisterPartitionCoefficientInBulkService {

    RegisterPartitionCoefficientsWithFileResponse registerInBulk(
            List<RegisterPartitionCoefficientFileRow> rows, Instant effectiveAt);
}

package org.lucoenergia.conluz.domain.admin.supply.create;


import org.lucoenergia.conluz.infrastructure.admin.supply.sharingagreement.CreateSupplyPartitionDto;

import java.util.Collection;

public interface ValidateSupplyPartitionsService {

    void validateTotalCoefficient(Collection<CreateSupplyPartitionDto> suppliesPartitions);
}

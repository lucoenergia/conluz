package org.lucoenergia.conluz.domain.admin.supply.create;


import org.lucoenergia.conluz.domain.admin.supply.SharingAgreementId;
import org.lucoenergia.conluz.domain.admin.supply.SupplyPartition;
import org.lucoenergia.conluz.domain.shared.SupplyCode;
import org.lucoenergia.conluz.infrastructure.admin.supply.create.CreateSupplyPartitionDto;

import java.util.Collection;

public interface CreateSupplyPartitionService {

    void validateTotalCoefficient(Collection<CreateSupplyPartitionDto> suppliesPartitions);

    SupplyPartition create(SupplyCode code, Double coefficient, SharingAgreementId sharingAgreementId);
}

package org.lucoenergia.conluz.domain.admin.supply.create;

import org.lucoenergia.conluz.domain.admin.supply.SharingAgreementId;
import org.lucoenergia.conluz.domain.admin.supply.SupplyPartition;
import org.lucoenergia.conluz.domain.shared.SupplyCode;
import org.lucoenergia.conluz.domain.admin.supply.SupplyPartitionId;

public interface CreateSupplyPartitionRepository {

    SupplyPartition updateCoefficient(SupplyPartitionId id, Double coefficient);

    SupplyPartition create(SupplyCode supplycode, Double coefficient, SharingAgreementId sharingAgreementId);
}

package org.lucoenergia.conluz.domain.admin.supply.get;

import org.lucoenergia.conluz.domain.admin.supply.SharingAgreementId;

import java.util.List;

public interface GetSharingAgreementPartitionsService {

    List<SupplyPartitionWithComparison> findPartitions(SharingAgreementId id);
}

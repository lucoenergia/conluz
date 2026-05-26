package org.lucoenergia.conluz.domain.admin.supply.sharingagreement;

import org.lucoenergia.conluz.domain.admin.supply.get.SupplyPartitionWithComparison;

import java.util.List;

public interface GetSharingAgreementPartitionsService {

    List<SupplyPartitionWithComparison> findPartitions(SharingAgreementId id);
}

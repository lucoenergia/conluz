package org.lucoenergia.conluz.domain.admin.supply.get;

import org.lucoenergia.conluz.domain.admin.supply.SharingAgreementId;
import org.lucoenergia.conluz.domain.admin.supply.SupplyPartition;
import org.lucoenergia.conluz.domain.shared.SupplyId;

import java.util.Optional;

public interface GetSupplyPartitionRepository {

    Optional<SupplyPartition> findBySupplyAndSharingAgreement(SupplyId supplyId, SharingAgreementId agreementId);
}

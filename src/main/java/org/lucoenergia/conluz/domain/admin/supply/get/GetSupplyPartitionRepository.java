package org.lucoenergia.conluz.domain.admin.supply.get;

import jakarta.validation.constraints.NotNull;
import org.lucoenergia.conluz.domain.admin.supply.SharingAgreementId;
import org.lucoenergia.conluz.domain.admin.supply.SupplyPartition;
import org.lucoenergia.conluz.domain.shared.SupplyId;

import java.util.Optional;

public interface GetSupplyPartitionRepository {

    Optional<SupplyPartition> findBySupplyAndSharingAgreement(@NotNull SupplyId supplyId,
                                                              @NotNull SharingAgreementId agreementId);
}

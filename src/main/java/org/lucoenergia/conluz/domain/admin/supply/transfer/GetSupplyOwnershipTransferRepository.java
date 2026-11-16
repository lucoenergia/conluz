package org.lucoenergia.conluz.domain.admin.supply.transfer;

import org.lucoenergia.conluz.domain.shared.SupplyId;

import java.time.LocalDate;
import java.util.Optional;

public interface GetSupplyOwnershipTransferRepository {

    /**
     * @return the latest effective date among the supply's transfers, or empty if it was never transferred
     */
    Optional<LocalDate> findLatestEffectiveDate(SupplyId supplyId);
}

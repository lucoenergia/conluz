package org.lucoenergia.conluz.domain.admin.supply.transfer;

import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException;
import org.lucoenergia.conluz.domain.admin.supply.SupplyOwnerNotFoundException;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.domain.shared.UserPersonalId;

import java.time.LocalDate;

public interface TransferSupplyOwnershipService {

    /**
     * Makes another member of the supply's community its owner, from the given date, and records the transfer
     * (SUP-009 to SUP-011). The new owner receives the supply's whole history (SUP-012).
     *
     * @param newOwner      the personal ID of the new owner, as typed; it is normalised before the lookup
     * @param effectiveDate the date the new owner became the supply's holder at the distributor
     * @param transferredBy the user making the transfer
     * @return the supply with its new owner
     * @throws SupplyNotFoundException                      if there is no such supply
     * @throws SupplyOwnerNotFoundException                 if no user has that personal ID, or the user has no
     *                                                      membership in the supply's community; the two alike
     * @throws SupplyAlreadyOwnedByUserException            if that user already owns the supply
     * @throws SupplyTransferDateInFutureException          if the date is after today in the community's time zone
     * @throws SupplyTransferDateNotAfterPreviousException  if the date is on or before the supply's previous
     *                                                      transfer's
     */
    Supply transfer(SupplyId supplyId, UserPersonalId newOwner, LocalDate effectiveDate, UserId transferredBy);
}

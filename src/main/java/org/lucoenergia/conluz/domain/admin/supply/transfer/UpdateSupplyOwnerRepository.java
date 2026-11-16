package org.lucoenergia.conluz.domain.admin.supply.transfer;

import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.domain.shared.UserId;

/**
 * The only way to change a supply's owner. It writes the owner and nothing else, so it never writes back a field a
 * caller loaded earlier; no other write of a supply writes its owner (#585).
 */
public interface UpdateSupplyOwnerRepository {

    /**
     * @return {@code true} if the supply exists and its owner is now the given user, {@code false} if there is no
     * such supply
     */
    boolean updateOwner(SupplyId supplyId, UserId ownerId);
}

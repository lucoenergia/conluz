package org.lucoenergia.conluz.domain.admin.supply.delete;

import org.lucoenergia.conluz.domain.shared.SupplyId;

public interface DeleteSupplyRepository {

    /**
     * Deletes the supply with its contract, distributor and Shelly details.
     *
     * @throws org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException if the supply does not exist
     */
    void delete(SupplyId id);
}

package org.lucoenergia.conluz.domain.admin.supply.delete;

import org.lucoenergia.conluz.domain.shared.SupplyId;

public interface DeleteSupplyService {

    /**
     * Deletes the supply with its contract, distributor and Shelly details, or refuses and deletes nothing
     * (SUP-001).
     *
     * @throws org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException if the supply does not exist
     * @throws SupplyHasPlantsException if a plant is attached to the supply
     * @throws SupplyHasPartitionCoefficientsException if a sharing agreement holds a partition coefficient for the
     *         supply
     */
    void delete(SupplyId id);
}

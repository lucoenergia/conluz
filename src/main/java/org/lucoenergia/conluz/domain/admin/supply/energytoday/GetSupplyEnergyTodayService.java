package org.lucoenergia.conluz.domain.admin.supply.energytoday;

import java.util.UUID;

public interface GetSupplyEnergyTodayService {

    /**
     * Today's production assigned to the supply point, the current local day being the one of the supply's
     * community.
     *
     * @throws org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException if the supply does not exist
     */
    SupplyProductionToday getToday(UUID supplyId);
}

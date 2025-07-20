package org.lucoenergia.conluz.domain.admin.supply.tariff;

import org.lucoenergia.conluz.domain.admin.supply.Supply;

public interface SupplyAccessHelper {

    /**
     * Check if the current user is an admin or the owner of the supply
     *
     * @param supply the supply to check
     * @return true if the current user is an admin or the owner of the supply
     */
    boolean isAdminOrSupplyOwner(Supply supply);
}

package org.lucoenergia.conluz.domain.admin.supply.tariff;

import org.lucoenergia.conluz.domain.admin.supply.SupplyTariff;

/**
 * Service for setting supply tariffs
 */
public interface SetSupplyTariffService {

    /**
     * Set tariff values for a supply
     * 
     * @param supplyTariff the supply tariff to set
     * @return the updated supply tariff
     */
    SupplyTariff setTariff(SupplyTariff supplyTariff);
}
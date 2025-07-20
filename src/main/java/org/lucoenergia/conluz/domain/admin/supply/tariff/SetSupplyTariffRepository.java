package org.lucoenergia.conluz.domain.admin.supply.tariff;

import org.lucoenergia.conluz.domain.admin.supply.SupplyTariff;

/**
 * Repository for managing supply tariffs
 */
public interface SetSupplyTariffRepository {

    /**
     * Save a supply tariff
     * 
     * @param supplyTariff the supply tariff to save
     * @return the saved supply tariff
     */
    SupplyTariff save(SupplyTariff supplyTariff);
}
package org.lucoenergia.conluz.domain.admin.supply.tariff;

import org.lucoenergia.conluz.domain.admin.supply.SupplyTariff;
import org.lucoenergia.conluz.domain.shared.SupplyId;

import java.util.Optional;

/**
 * Repository for getting supply tariffs
 */
public interface GetSupplyTariffRepository {

    /**
     * Find a supply tariff by supply ID
     * 
     * @param supplyId the supply ID
     * @return the supply tariff, if found
     */
    Optional<SupplyTariff> findBySupplyId(SupplyId supplyId);
}
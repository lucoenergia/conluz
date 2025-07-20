package org.lucoenergia.conluz.domain.admin.supply.tariff;

import org.lucoenergia.conluz.domain.admin.supply.SupplyTariff;
import org.lucoenergia.conluz.domain.shared.SupplyId;

import java.util.Optional;

/**
 * Service for getting supply tariffs
 */
public interface GetSupplyTariffService {

    /**
     * Get tariff for a supply
     * 
     * @param supplyId the supply ID
     * @return the supply tariff, if found
     */
    Optional<SupplyTariff> getTariffBySupplyId(SupplyId supplyId);
}
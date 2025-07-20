package org.lucoenergia.conluz.infrastructure.admin.supply.tariff;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * JPA repository for supply tariffs
 */
@Repository
public interface SupplyTariffRepository extends JpaRepository<SupplyTariffEntity, UUID> {
    
    /**
     * Find a supply tariff by supply ID
     * 
     * @param supplyId the supply ID
     * @return the supply tariff, if found
     */
    Optional<SupplyTariffEntity> findBySupplyId(UUID supplyId);
}
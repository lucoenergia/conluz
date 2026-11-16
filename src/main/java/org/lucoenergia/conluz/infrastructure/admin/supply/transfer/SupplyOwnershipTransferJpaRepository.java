package org.lucoenergia.conluz.infrastructure.admin.supply.transfer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface SupplyOwnershipTransferJpaRepository extends JpaRepository<SupplyOwnershipTransferEntity, UUID> {

    @Query("SELECT max(t.effectiveDate) FROM supply_ownership_transfers t WHERE t.supplyId = :supplyId")
    Optional<LocalDate> findLatestEffectiveDateBySupplyId(@Param("supplyId") UUID supplyId);
}

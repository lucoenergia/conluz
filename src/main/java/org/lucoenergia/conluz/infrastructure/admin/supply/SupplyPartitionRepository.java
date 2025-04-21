package org.lucoenergia.conluz.infrastructure.admin.supply;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SupplyPartitionRepository extends JpaRepository<SupplyPartitionEntity, UUID> {

    Optional<SupplyPartitionEntity> findBySupplyIdAndSharingAgreementId(UUID supplyId, UUID sharingAgreementId);
}

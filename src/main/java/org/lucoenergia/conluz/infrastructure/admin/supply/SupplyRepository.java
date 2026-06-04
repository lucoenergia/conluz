package org.lucoenergia.conluz.infrastructure.admin.supply;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SupplyRepository extends JpaRepository<SupplyEntity, UUID>, JpaSpecificationExecutor<SupplyEntity> {

    Optional<SupplyEntity> findByCode(String code);

    int countByCode(String code);

    List<SupplyEntity> findByUserId(UUID userId);

    List<SupplyEntity> findByCommunityId(UUID communityId);
}

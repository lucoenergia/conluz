package org.lucoenergia.conluz.infrastructure.admin.supply;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface SupplyRepository extends JpaRepository<SupplyEntity, UUID>, JpaSpecificationExecutor<SupplyEntity> {

    Optional<SupplyEntity> findByCode(String code);

    int countByCode(String code);

    List<SupplyEntity> findByUserId(UUID userId);

    List<SupplyEntity> findByCommunityId(UUID communityId);

    @Query("SELECT s.community.id, COUNT(s) FROM supplies s WHERE s.community.id IN :ids GROUP BY s.community.id")
    List<Object[]> countSuppliesByCommunityIds(@Param("ids") Set<UUID> ids);
}

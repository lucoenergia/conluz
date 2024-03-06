package org.lucoenergia.conluz.infrastructure.admin.supply;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SupplyRepository extends JpaRepository<SupplyEntity, UUID> {

    Optional<SupplyEntity> findByCode(String code);

    int countByCode(String code);
}

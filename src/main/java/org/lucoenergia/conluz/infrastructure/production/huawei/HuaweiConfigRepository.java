package org.lucoenergia.conluz.infrastructure.production.huawei;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface HuaweiConfigRepository extends JpaRepository<HuaweiConfigEntity, UUID> {

    Optional<HuaweiConfigEntity> findFirstByOrderByIdAsc();
}

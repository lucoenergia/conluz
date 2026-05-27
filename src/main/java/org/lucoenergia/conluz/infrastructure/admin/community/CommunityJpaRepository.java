package org.lucoenergia.conluz.infrastructure.admin.community;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CommunityJpaRepository extends JpaRepository<CommunityEntity, UUID> {

    Optional<CommunityEntity> findByCode(String code);

    boolean existsByCode(String code);

    boolean existsByLegalId(String legalId);
}

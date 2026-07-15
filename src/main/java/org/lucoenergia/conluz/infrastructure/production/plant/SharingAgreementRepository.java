package org.lucoenergia.conluz.infrastructure.production.plant;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SharingAgreementRepository extends JpaRepository<SharingAgreementEntity, UUID> {

    Optional<SharingAgreementEntity> findFirstByPlantIdAndStatusOrderByCreatedAtDesc(
            UUID plantId, SharingAgreementStatus status);
}

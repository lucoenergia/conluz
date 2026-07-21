package org.lucoenergia.conluz.infrastructure.production.plant.sharingagreement;

import org.lucoenergia.conluz.domain.production.plant.sharingagreement.SharingAgreementStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SharingAgreementRepository extends JpaRepository<SharingAgreementEntity, UUID> {

    Optional<SharingAgreementEntity> findFirstByPlantIdAndStatusOrderByCreatedAtDesc(
            UUID plantId, SharingAgreementStatus status);

    List<SharingAgreementEntity> findByPlantIdOrderByCreatedAtDesc(UUID plantId);

    List<SharingAgreementEntity> findByPlantIdAndStatusOrderByCreatedAtDesc(UUID plantId, SharingAgreementStatus status);
}

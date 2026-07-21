package org.lucoenergia.conluz.infrastructure.production.plant.sharingagreementfile;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SharingAgreementFileRepository extends JpaRepository<SharingAgreementFileEntity, UUID> {

    Optional<SharingAgreementFileEntity> findFirstBySharingAgreementIdOrderByUploadedAtDesc(UUID sharingAgreementId);
}

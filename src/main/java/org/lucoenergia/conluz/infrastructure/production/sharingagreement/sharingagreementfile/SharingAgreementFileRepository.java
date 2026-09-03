package org.lucoenergia.conluz.infrastructure.production.sharingagreement.sharingagreementfile;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface SharingAgreementFileRepository extends JpaRepository<SharingAgreementFileEntity, UUID> {

    Optional<SharingAgreementFileEntity> findFirstBySharingAgreementIdOrderByUploadedAtDesc(UUID sharingAgreementId);

    @Modifying(clearAutomatically = true)
    @Query("delete from sharing_agreement_file f where f.sharingAgreement.id = :sharingAgreementId")
    void deleteBySharingAgreementId(@Param("sharingAgreementId") UUID sharingAgreementId);
}

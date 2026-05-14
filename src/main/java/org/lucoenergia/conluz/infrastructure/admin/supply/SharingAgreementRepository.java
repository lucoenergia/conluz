package org.lucoenergia.conluz.infrastructure.admin.supply;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SharingAgreementRepository extends JpaRepository<SharingAgreementEntity, UUID> {

    List<SharingAgreementEntity> findAllByOrderByStartDateDesc();

    Optional<SharingAgreementEntity> findFirstByEndDateIsNull();

    Optional<SharingAgreementEntity> findFirstByEndDate(LocalDate date);
}

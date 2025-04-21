package org.lucoenergia.conluz.infrastructure.admin.supply;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SharingAgreementRepository extends JpaRepository<SharingAgreementEntity, UUID> {
}

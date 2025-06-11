package org.lucoenergia.conluz.infrastructure.admin.supply.get;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.SharingAgreement;
import org.lucoenergia.conluz.domain.admin.supply.SharingAgreementId;
import org.lucoenergia.conluz.infrastructure.admin.supply.SharingAgreementEntity;
import org.lucoenergia.conluz.infrastructure.admin.supply.SharingAgreementEntityMother;
import org.lucoenergia.conluz.infrastructure.admin.supply.SharingAgreementRepository;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Transactional
class GetSharingAgreementRepositoryTest extends BaseIntegrationTest {

    @Autowired
    private GetSharingAgreementRepositoryDatabase repositoryDatabase;
    @Autowired
    private SharingAgreementRepository sharingAgreementRepository;

    /**
     * Tests that the findById method returns a valid SharingAgreement when the repository contains an entry for the given ID.
     */
    @Test
    void testFindById_ReturnsSharingAgreement() {
        SharingAgreementEntity sharingAgreement = SharingAgreementEntityMother.random();
        sharingAgreementRepository.save(sharingAgreement);

        Optional<SharingAgreement> result = repositoryDatabase.findById(SharingAgreementId.of(sharingAgreement.getId()));

        assertTrue(result.isPresent());
        assertEquals(sharingAgreement.getId(), result.get().getId());
        assertEquals(sharingAgreement.getStartDate(), result.get().getStartDate());
        assertEquals(sharingAgreement.getEndDate(), result.get().getEndDate());
    }

    /**
     * Tests that the findById method returns an empty Optional when the repository does not contain an entry for the given ID.
     */
    @Test
    void testFindById_ReturnsEmptyOptional() {
        UUID uuid = UUID.randomUUID();
        SharingAgreementId agreementId = SharingAgreementId.of(uuid);

        Optional<SharingAgreement> result = repositoryDatabase.findById(agreementId);

        assertFalse(result.isPresent());
    }
}
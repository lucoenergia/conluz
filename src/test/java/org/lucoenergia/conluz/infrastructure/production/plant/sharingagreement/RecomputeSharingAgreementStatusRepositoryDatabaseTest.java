package org.lucoenergia.conluz.infrastructure.production.plant.sharingagreement;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SaveSupplyPartitionCoefficientRepository;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyPartitionCoefficient;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.production.plant.PlantMother;
import org.lucoenergia.conluz.domain.production.plant.sharingagreement.SharingAgreementStatus;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityJpaRepository;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyEntity;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyEntityMother;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyRepository;
import org.lucoenergia.conluz.infrastructure.admin.user.UserEntity;
import org.lucoenergia.conluz.infrastructure.admin.user.UserRepository;
import org.lucoenergia.conluz.infrastructure.production.plant.PlantEntity;
import org.lucoenergia.conluz.infrastructure.production.plant.PlantRepository;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.lucoenergia.conluz.infrastructure.admin.supply.create.CreateSupplyRepositoryDatabase.DEFAULT_COMMUNITY_ID;

@Transactional
class RecomputeSharingAgreementStatusRepositoryDatabaseTest extends BaseIntegrationTest {

    @Autowired
    private RecomputeSharingAgreementStatusRepositoryDatabase repository;
    @Autowired
    private SharingAgreementRepository sharingAgreementRepository;
    @Autowired
    private SaveSupplyPartitionCoefficientRepository saveCoefficientRepository;
    @Autowired
    private SupplyRepository supplyRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CommunityJpaRepository communityJpaRepository;
    @Autowired
    private PlantRepository plantRepository;

    private SupplyEntity persistSupply() {
        UserEntity user = userRepository.save(UserMother.randomUserEntity());
        return supplyRepository.save(SupplyEntityMother.random(user, communityJpaRepository.getReferenceById(DEFAULT_COMMUNITY_ID)));
    }

    private SharingAgreementEntity persistAgreement(PlantEntity plant, SharingAgreementStatus status) {
        SharingAgreementEntity agreement = new SharingAgreementEntity();
        agreement.setId(UUID.randomUUID());
        agreement.setPlant(plant);
        agreement.setName("Test agreement " + UUID.randomUUID());
        agreement.setStatus(status);
        agreement.setCreatedAt(Instant.now());
        agreement.setCreatedBy(null);
        return sharingAgreementRepository.save(agreement);
    }

    private void persistCoefficient(SupplyEntity supply, PlantEntity plant, SharingAgreementEntity agreement,
                                     Instant validFrom, Instant validTo) {
        saveCoefficientRepository.save(new SupplyPartitionCoefficient.Builder()
                .withId(UUID.randomUUID())
                .withSupplyId(supply.getId())
                .withPlantId(plant.getId())
                .withSharingAgreementId(agreement.getId())
                .withCoefficient(BigDecimal.ONE)
                .withValidFrom(validFrom)
                .withValidTo(validTo)
                .withCreatedAt(Instant.now())
                .build());
    }

    @Test
    void recomputeStatusSetsSupersededWhenEveryCoefficientIsClosed() {
        SupplyEntity supply = persistSupply();
        PlantEntity plant = plantRepository.save(PlantMother.randomPlantEntity().withSupply(supply).build());
        SharingAgreementEntity agreement = persistAgreement(plant, SharingAgreementStatus.PUBLISHED);
        persistCoefficient(supply, plant, agreement, Instant.parse("2024-01-01T00:00:00Z"), Instant.parse("2024-06-01T00:00:00Z"));

        repository.recomputeStatus(agreement.getId());

        assertEquals(SharingAgreementStatus.SUPERSEDED, sharingAgreementRepository.findById(agreement.getId()).orElseThrow().getStatus());
    }

    @Test
    void recomputeStatusSetsPublishedWhenAtLeastOneCoefficientIsOpen() {
        SupplyEntity supply = persistSupply();
        PlantEntity plant = plantRepository.save(PlantMother.randomPlantEntity().withSupply(supply).build());
        SharingAgreementEntity agreement = persistAgreement(plant, SharingAgreementStatus.SUPERSEDED);
        persistCoefficient(supply, plant, agreement, Instant.parse("2024-01-01T00:00:00Z"), null);

        repository.recomputeStatus(agreement.getId());

        assertEquals(SharingAgreementStatus.PUBLISHED, sharingAgreementRepository.findById(agreement.getId()).orElseThrow().getStatus());
    }

    @Test
    void recomputeStatusLeavesPublishedWhenAllCoefficientsArePending() {
        SupplyEntity supply = persistSupply();
        PlantEntity plant = plantRepository.save(PlantMother.randomPlantEntity().withSupply(supply).build());
        SharingAgreementEntity agreement = persistAgreement(plant, SharingAgreementStatus.PUBLISHED);
        persistCoefficient(supply, plant, agreement, null, null);

        repository.recomputeStatus(agreement.getId());

        assertEquals(SharingAgreementStatus.PUBLISHED, sharingAgreementRepository.findById(agreement.getId()).orElseThrow().getStatus());
    }

    @Test
    void recomputeStatusNeverTouchesDraftAgreement() {
        SupplyEntity supply = persistSupply();
        PlantEntity plant = plantRepository.save(PlantMother.randomPlantEntity().withSupply(supply).build());
        SharingAgreementEntity agreement = persistAgreement(plant, SharingAgreementStatus.DRAFT);
        persistCoefficient(supply, plant, agreement, Instant.parse("2024-01-01T00:00:00Z"), Instant.parse("2024-06-01T00:00:00Z"));

        repository.recomputeStatus(agreement.getId());

        assertEquals(SharingAgreementStatus.DRAFT, sharingAgreementRepository.findById(agreement.getId()).orElseThrow().getStatus());
    }

    // Note: every test above already exercises the flushAutomatically requirement documented on
    // SharingAgreementRepository#recomputeStatus -- each persists a coefficient via the JPA-backed
    // save() and immediately calls recompute in the SAME (never separately committed) @Transactional
    // test transaction. Without flushAutomatically, this native query would read stale rows and every
    // test in this class would fail, not just one -- there is no single distinguished "regression test"
    // for it to add beyond what is already covered above.
}

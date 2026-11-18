package org.lucoenergia.conluz.infrastructure.admin.supply.partitioncoefficient;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.create.CreateCommunityRepository;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.GetSupplyPartitionCoefficientRepository;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SaveSupplyPartitionCoefficientRepository;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyPartitionCoefficient;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.plant.PlantMother;
import org.lucoenergia.conluz.domain.production.plant.create.CreatePlantRepository;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreement;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementStatus;
import org.lucoenergia.conluz.domain.production.sharingagreement.create.CreateSharingAgreementRepository;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The plant-wide reads the observed sharing of #579 is computed from.
 */
@Transactional
class GetSupplyPartitionCoefficientRepositoryTest extends BaseIntegrationTest {

    @Autowired
    private GetSupplyPartitionCoefficientRepository repository;
    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateCommunityRepository createCommunityRepository;
    @Autowired
    private CreateSupplyRepository createSupplyRepository;
    @Autowired
    private CreatePlantRepository createPlantRepository;
    @Autowired
    private CreateSharingAgreementRepository createSharingAgreementRepository;
    @Autowired
    private SaveSupplyPartitionCoefficientRepository saveSupplyPartitionCoefficientRepository;

    private Community community;
    private Plant plant;
    private Plant otherPlant;

    @BeforeEach
    void setUp() {
        community = createCommunityRepository.create(CommunityMother.random().build());
        plant = plant();
        otherPlant = plant();
    }

    @Test
    void findAllByPlantIdReturnsEveryRowOfThePlantInEveryAgreement() {
        Supply first = supply();
        Supply second = supply();
        SharingAgreement superseded = agreement(plant, SharingAgreementStatus.SUPERSEDED);
        SharingAgreement draft = agreement(plant, SharingAgreementStatus.DRAFT);
        SupplyPartitionCoefficient applied = coefficient(first, plant, superseded, Instant.parse("2024-01-01T00:00:00Z"));
        SupplyPartitionCoefficient pending = coefficient(second, plant, draft, null);
        coefficient(first, otherPlant, agreement(otherPlant, SharingAgreementStatus.PUBLISHED), null);

        List<SupplyPartitionCoefficient> rows = repository.findAllByPlantId(plant.getId());

        assertThat(rows).extracting(SupplyPartitionCoefficient::getId)
                .containsExactlyInAnyOrder(applied.getId(), pending.getId());
        assertThat(rows).allSatisfy(row -> assertThat(row.getPlantId()).isEqualTo(plant.getId()));
    }

    @Test
    @DisplayName("OBS-006 the supplies holding a coefficient in another plant are found, and only them")
    void findSupplyIdsWithCoefficientsInOtherPlants() {
        Supply onlyHere = supply();
        Supply inBoth = supply();
        Supply onlyThere = supply();
        Supply nowhere = supply();
        SharingAgreement here = agreement(plant, SharingAgreementStatus.PUBLISHED);
        SharingAgreement there = agreement(otherPlant, SharingAgreementStatus.DRAFT);
        coefficient(onlyHere, plant, here, null);
        coefficient(inBoth, plant, here, null);
        coefficient(inBoth, otherPlant, there, null);
        coefficient(onlyThere, otherPlant, there, null);

        assertThat(repository.findSupplyIdsWithCoefficientsInOtherPlants(plant.getId(),
                List.of(onlyHere.getId(), inBoth.getId(), onlyThere.getId(), nowhere.getId())))
                .containsExactlyInAnyOrder(inBoth.getId(), onlyThere.getId());
        assertThat(repository.findSupplyIdsWithCoefficientsInOtherPlants(plant.getId(), List.of())).isEmpty();
    }

    private Supply supply() {
        User owner = UserMother.randomUser();
        createUserRepository.create(owner);
        return createSupplyRepository.create(SupplyMother.random(owner).build(), UserId.of(owner.getId()),
                community.getId());
    }

    private Plant plant() {
        Supply supply = supply();
        return createPlantRepository.create(PlantMother.random(supply).build(), SupplyId.of(supply.getId()));
    }

    private SharingAgreement agreement(Plant owner, SharingAgreementStatus status) {
        return createSharingAgreementRepository.create(new SharingAgreement.Builder()
                .withId(UUID.randomUUID())
                .withPlantId(owner.getId())
                .withName("Agreement " + UUID.randomUUID())
                .withStatus(status)
                .withNominalPowerKW(BigDecimal.TEN)
                .withCreatedAt(Instant.now())
                .build());
    }

    private SupplyPartitionCoefficient coefficient(Supply supply, Plant owner, SharingAgreement agreement,
                                                   Instant validFrom) {
        return saveSupplyPartitionCoefficientRepository.save(new SupplyPartitionCoefficient.Builder()
                .withId(UUID.randomUUID())
                .withSupplyId(supply.getId())
                .withPlantId(owner.getId())
                .withSharingAgreementId(agreement.getId())
                .withCoefficient(new BigDecimal("0.5"))
                .withValidFrom(validFrom)
                .withValidTo(null)
                .withCreatedAt(Instant.now())
                .build());
    }
}

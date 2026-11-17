package org.lucoenergia.conluz.infrastructure.production.sharingagreement.activation;

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
import org.lucoenergia.conluz.domain.production.sharingagreement.activation.CoefficientActivationService;
import org.lucoenergia.conluz.domain.production.sharingagreement.create.CreateSharingAgreementRepository;
import org.lucoenergia.conluz.domain.production.sharingagreement.publish.PublishSharingAgreementRepository;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.domain.shared.time.ZoneResolver;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.lucoenergia.conluz.infrastructure.shared.db.LockWaitTestSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Two date writes on the same plant, ordered with the lock wait of {@link LockWaitTestSupport}: the first holds its
 * transaction open until the second is blocked. Not transactional itself, so that each write commits as a request's
 * own transaction does.
 */
class CoefficientDateWriteConcurrencyTest extends BaseIntegrationTest {

    private static final LocalDate FIRST_START = LocalDate.of(2023, 1, 1);
    private static final LocalDate EARLIER_ACTIVATION = LocalDate.of(2024, 1, 1);
    private static final LocalDate LATER_ACTIVATION = LocalDate.of(2024, 6, 1);

    @Autowired
    private CoefficientActivationService service;
    @Autowired
    private GetSupplyPartitionCoefficientRepository getCoefficientRepository;
    @Autowired
    private SaveSupplyPartitionCoefficientRepository saveCoefficientRepository;
    @Autowired
    private CreateSharingAgreementRepository createSharingAgreementRepository;
    @Autowired
    private PublishSharingAgreementRepository publishSharingAgreementRepository;
    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateCommunityRepository createCommunityRepository;
    @Autowired
    private CreateSupplyRepository createSupplyRepository;
    @Autowired
    private CreatePlantRepository createPlantRepository;
    @Autowired
    private ZoneResolver zoneResolver;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * Without the lock, the second activation would read the first row as still open, and close it again at its own
     * date once the first activation committed: the first activation's cascade would be lost, and its coefficient
     * would overlap the second one, which the exclusion constraint refuses at commit.
     */
    @Test
    @DisplayName("SHA-007 two activations of the same supply at the same time both succeed, and neither loses the other's cascade")
    void twoActivationsAtTheSameTime_bothSucceed_andKeepBothCascades() throws Exception {
        Community community = createCommunityRepository.create(CommunityMother.random().build());
        Plant plant = createPlant(community);
        Supply supply = createSupply(community);
        SharingAgreement firstAgreement = publishedAgreement(plant);
        SupplyPartitionCoefficient first = pending(supply, plant, firstAgreement);
        service.setValidFrom(plant.getId(), firstAgreement.getId(), FIRST_START, List.of(first.getId()));
        SharingAgreement earlierAgreement = publishedAgreement(plant);
        SupplyPartitionCoefficient earlier = pending(supply, plant, earlierAgreement);
        SharingAgreement laterAgreement = publishedAgreement(plant);
        SupplyPartitionCoefficient later = pending(supply, plant, laterAgreement);

        Throwable secondOutcome = LockWaitTestSupport.runWhileHeld(transactionManager, jdbcTemplate,
                () -> service.setValidFrom(plant.getId(), earlierAgreement.getId(), EARLIER_ACTIVATION,
                        List.of(earlier.getId())),
                () -> service.setValidFrom(plant.getId(), laterAgreement.getId(), LATER_ACTIVATION,
                        List.of(later.getId())));

        assertThat(secondOutcome).isNull();
        assertThat(timeline(plant, supply)).containsExactly(
                new Period(first.getId(), at(FIRST_START), at(EARLIER_ACTIVATION)),
                new Period(earlier.getId(), at(EARLIER_ACTIVATION), at(LATER_ACTIVATION)),
                new Period(later.getId(), at(LATER_ACTIVATION), null));
    }

    private record Period(UUID id, Instant validFrom, Instant validTo) {
    }

    private List<Period> timeline(Plant plant, Supply supply) {
        return getCoefficientRepository.findAllByPlantIdAndSupplyIdIn(plant.getId(), List.of(supply.getId())).stream()
                .filter(coefficient -> coefficient.getValidFrom() != null)
                .sorted(Comparator.comparing(SupplyPartitionCoefficient::getValidFrom))
                .map(coefficient -> new Period(coefficient.getId(), coefficient.getValidFrom(), coefficient.getValidTo()))
                .toList();
    }

    private Instant at(LocalDate date) {
        return date.atStartOfDay(zoneResolver.resolveApplicationZoneId()).toInstant();
    }

    private Plant createPlant(Community community) {
        Supply ownSupply = createSupply(community);
        return createPlantRepository.create(PlantMother.random(ownSupply).build(), SupplyId.of(ownSupply.getId()));
    }

    private Supply createSupply(Community community) {
        User owner = UserMother.randomUser();
        createUserRepository.create(owner);
        return createSupplyRepository.create(SupplyMother.random(owner).build(), UserId.of(owner.getId()),
                community.getId());
    }

    private SharingAgreement publishedAgreement(Plant plant) {
        SharingAgreement draft = createSharingAgreementRepository.create(new SharingAgreement.Builder()
                .withId(UUID.randomUUID())
                .withPlantId(plant.getId())
                .withName("Agreement " + UUID.randomUUID())
                .withStatus(SharingAgreementStatus.DRAFT)
                .withNominalPowerKW(BigDecimal.TEN)
                .withCreatedAt(Instant.now())
                .build());
        return publishSharingAgreementRepository.publish(plant.getId(), draft.getId());
    }

    /**
     * A pending row, exactly as materialisation writes one: no validFrom, no validTo.
     */
    private SupplyPartitionCoefficient pending(Supply supply, Plant plant, SharingAgreement agreement) {
        return saveCoefficientRepository.save(new SupplyPartitionCoefficient.Builder()
                .withId(UUID.randomUUID())
                .withSupplyId(supply.getId())
                .withPlantId(plant.getId())
                .withSharingAgreementId(agreement.getId())
                .withCoefficient(BigDecimal.ONE)
                .withValidFrom(null)
                .withValidTo(null)
                .withCreatedAt(Instant.now())
                .build());
    }
}

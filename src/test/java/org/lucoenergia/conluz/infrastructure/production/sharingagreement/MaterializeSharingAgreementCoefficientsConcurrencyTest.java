package org.lucoenergia.conluz.infrastructure.production.sharingagreement;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.create.CreateCommunityRepository;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.GetSupplyPartitionCoefficientRepository;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyPartitionCoefficient;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.plant.PlantMother;
import org.lucoenergia.conluz.domain.production.plant.create.CreatePlantRepository;
import org.lucoenergia.conluz.domain.production.sharingagreement.MaterializeSharingAgreementCoefficientsService;
import org.lucoenergia.conluz.domain.production.sharingagreement.ResolvedCoefficientEntry;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreement;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementNotDraftException;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementStatus;
import org.lucoenergia.conluz.domain.production.sharingagreement.create.CreateSharingAgreementRepository;
import org.lucoenergia.conluz.domain.production.sharingagreement.publish.PublishSharingAgreementRepository;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.lucoenergia.conluz.infrastructure.shared.db.LockWaitTestSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Two replacements of coefficients, ordered with the lock wait of {@link LockWaitTestSupport}: the first holds its
 * transaction open until the second is blocked on a lock, or has finished because nothing blocked it. Not
 * transactional itself, so that each replacement commits as a request's own transaction does.
 */
class MaterializeSharingAgreementCoefficientsConcurrencyTest extends BaseIntegrationTest {

    @Autowired
    private MaterializeSharingAgreementCoefficientsService service;
    @Autowired
    private GetSupplyPartitionCoefficientRepository getCoefficientRepository;
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
    private EntityManager entityManager;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * Without the lock, the second replacement's delete would not see the first one's uncommitted rows, and its
     * inserts would then collide with them on the one-coefficient-per-supply constraint (SHA-018): the second request
     * would be refused instead of replacing the first one's set.
     */
    @Test
    @DisplayName("SHA-019 two replacements of the same draft agreement run one after another, and the second one's set replaces the first")
    void twoReplacementsOfTheSameAgreement_runOneAfterAnother_andTheSecondSetWins() throws Exception {
        Community community = createCommunityRepository.create(CommunityMother.random().build());
        Plant plant = createPlant(community);
        Supply first = createSupply(community);
        Supply second = createSupply(community);
        SharingAgreement agreement = draftAgreement(plant);
        AtomicBoolean firstCommitted = new AtomicBoolean();
        AtomicBoolean secondFinishedAfterFirstCommitted = new AtomicBoolean();

        Throwable secondOutcome = LockWaitTestSupport.runWhileHeld(transactionManager, jdbcTemplate,
                () -> {
                    flagOnCommit(firstCommitted);
                    service.replaceAllBySupplyId(plant.getId(), agreement.getId(), List.of(
                            new ResolvedCoefficientEntry(first.getId(), new BigDecimal("0.400000")),
                            new ResolvedCoefficientEntry(second.getId(), new BigDecimal("0.600000"))));
                },
                () -> {
                    service.replaceAllBySupplyId(plant.getId(), agreement.getId(), List.of(
                            new ResolvedCoefficientEntry(first.getId(), new BigDecimal("0.300000")),
                            new ResolvedCoefficientEntry(second.getId(), new BigDecimal("0.700000"))));
                    secondFinishedAfterFirstCommitted.set(firstCommitted.get());
                });

        assertThat(secondOutcome).isNull();
        assertThat(secondFinishedAfterFirstCommitted).isTrue();
        assertThat(coefficientsBySupply(agreement)).isEqualTo(Map.of(
                first.getId(), new BigDecimal("0.300000"),
                second.getId(), new BigDecimal("0.700000")));
    }

    @Test
    @DisplayName("SHA-019 a replacement of another agreement on the same plant does not wait")
    void replacementsOfTwoAgreementsOfOnePlant_doNotWaitForEachOther() throws Exception {
        Community community = createCommunityRepository.create(CommunityMother.random().build());
        Plant plant = createPlant(community);
        Supply supply = createSupply(community);
        SharingAgreement one = draftAgreement(plant);
        SharingAgreement other = draftAgreement(plant);
        AtomicBoolean firstCommitted = new AtomicBoolean();
        AtomicBoolean secondFinishedAfterFirstCommitted = new AtomicBoolean(true);

        Throwable secondOutcome = LockWaitTestSupport.runWhileHeld(transactionManager, jdbcTemplate,
                () -> {
                    flagOnCommit(firstCommitted);
                    service.replaceAllBySupplyId(plant.getId(), one.getId(),
                            List.of(new ResolvedCoefficientEntry(supply.getId(), new BigDecimal("0.400000"))));
                },
                () -> {
                    service.replaceAllBySupplyId(plant.getId(), other.getId(),
                            List.of(new ResolvedCoefficientEntry(supply.getId(), new BigDecimal("0.600000"))));
                    secondFinishedAfterFirstCommitted.set(firstCommitted.get());
                });

        assertThat(secondOutcome).isNull();
        assertThat(secondFinishedAfterFirstCommitted).isFalse();
        assertThat(coefficientsBySupply(one)).isEqualTo(Map.of(supply.getId(), new BigDecimal("0.400000")));
        assertThat(coefficientsBySupply(other)).isEqualTo(Map.of(supply.getId(), new BigDecimal("0.600000")));
    }

    /**
     * The replacement read the agreement as DRAFT before it waited; the status it acts on is the one read under the
     * lock, once the publish has committed.
     */
    @Test
    @DisplayName("SHA-019 a replacement that waited for the agreement to be published is refused, and the published set is kept")
    void aReplacementThatWaitedForAPublish_isRefused_andThePublishedSetIsKept() throws Exception {
        Community community = createCommunityRepository.create(CommunityMother.random().build());
        Plant plant = createPlant(community);
        Supply supply = createSupply(community);
        SharingAgreement agreement = draftAgreement(plant);
        service.replaceAllBySupplyId(plant.getId(), agreement.getId(),
                List.of(new ResolvedCoefficientEntry(supply.getId(), new BigDecimal("0.400000"))));

        Throwable secondOutcome = LockWaitTestSupport.runWhileHeld(transactionManager, jdbcTemplate,
                () -> {
                    publishSharingAgreementRepository.publish(plant.getId(), agreement.getId());
                    // Writes the status now, so this transaction holds the agreement row while the replacement waits
                    entityManager.flush();
                },
                () -> service.replaceAllBySupplyId(plant.getId(), agreement.getId(),
                        List.of(new ResolvedCoefficientEntry(supply.getId(), new BigDecimal("0.600000")))));

        assertThat(secondOutcome).isInstanceOf(SharingAgreementNotDraftException.class);
        assertThat(coefficientsBySupply(agreement)).isEqualTo(Map.of(supply.getId(), new BigDecimal("0.400000")));
    }

    private static void flagOnCommit(AtomicBoolean committed) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                committed.set(true);
            }
        });
    }

    private Map<UUID, BigDecimal> coefficientsBySupply(SharingAgreement agreement) {
        return getCoefficientRepository.findAllBySharingAgreementId(agreement.getId()).stream()
                .collect(Collectors.toMap(SupplyPartitionCoefficient::getSupplyId,
                        SupplyPartitionCoefficient::getCoefficient));
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

    private SharingAgreement draftAgreement(Plant plant) {
        return createSharingAgreementRepository.create(new SharingAgreement.Builder()
                .withId(UUID.randomUUID())
                .withPlantId(plant.getId())
                .withName("Agreement " + UUID.randomUUID())
                .withStatus(SharingAgreementStatus.DRAFT)
                .withNominalPowerKW(BigDecimal.TEN)
                .withCreatedAt(Instant.now())
                .build());
    }
}

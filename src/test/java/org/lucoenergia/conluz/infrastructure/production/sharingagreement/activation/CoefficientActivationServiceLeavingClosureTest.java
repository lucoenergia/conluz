package org.lucoenergia.conluz.infrastructure.production.sharingagreement.activation;

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
import org.lucoenergia.conluz.domain.production.sharingagreement.activation.CoefficientActivationError;
import org.lucoenergia.conluz.domain.production.sharingagreement.activation.CoefficientActivationErrorCode;
import org.lucoenergia.conluz.domain.production.sharingagreement.activation.CoefficientActivationException;
import org.lucoenergia.conluz.domain.production.sharingagreement.activation.CoefficientActivationService;
import org.lucoenergia.conluz.domain.production.sharingagreement.create.CreateSharingAgreementRepository;
import org.lucoenergia.conluz.domain.production.sharingagreement.get.GetSharingAgreementService;
import org.lucoenergia.conluz.domain.production.sharingagreement.publish.PublishSharingAgreementRepository;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.domain.shared.time.ZoneResolver;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The closure, on an agreement's first activation, of the coefficients of the supplies absent from it (#593), against
 * PostgreSQL. Every timeline is built the way production builds it: pending rows as materialisation writes them, then
 * the activation, close and reopen services. The assertions read only persisted state.
 */
@Transactional
class CoefficientActivationServiceLeavingClosureTest extends BaseIntegrationTest {

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
    private GetSharingAgreementService getSharingAgreementService;
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

    private Community community;
    private Plant plant;
    private Supply x;
    private Supply y;
    private Supply z;

    @BeforeEach
    void setUp() {
        community = createCommunityRepository.create(CommunityMother.random().build());
        plant = createPlant();
        x = createSupply();
        y = createSupply();
        z = createSupply();
    }

    @Test
    @DisplayName("SHA-009 the first activation of B closes at D the open coefficients of the supplies absent from B, and places the others per SHA-003")
    void firstActivationClosesTheSuppliesAbsentFromTheAgreement() {
        Agreement a = activatedAgreement("2025-01-01", x, y, z);
        Agreement b = pendingAgreement(x);

        activate(b, "2025-06-01", x);

        assertThat(timeline(x)).containsExactly(
                period(a.of(x), "2025-01-01", "2025-06-01"),
                period(b.of(x), "2025-06-01", null));
        assertThat(timeline(y)).containsExactly(period(a.of(y), "2025-01-01", "2025-06-01"));
        assertThat(timeline(z)).containsExactly(period(a.of(z), "2025-01-01", "2025-06-01"));
    }

    @Test
    @DisplayName("SHA-009 an agreement whose rows have all ended through leaving closures is recomputed to SUPERSEDED")
    void theAgreementOfTheClosedRowsIsRecomputed() {
        Agreement a = activatedAgreement("2025-01-01", x, y, z);
        Agreement b = pendingAgreement(x);
        assertThat(statusOf(a)).isEqualTo(SharingAgreementStatus.PUBLISHED);

        activate(b, "2025-06-01", x);

        assertThat(statusOf(a)).isEqualTo(SharingAgreementStatus.SUPERSEDED);
        assertThat(statusOf(b)).isEqualTo(SharingAgreementStatus.PUBLISHED);
    }

    @Test
    @DisplayName("SHA-009 leaving supplies across two other agreements are each closed, and each agreement is recomputed")
    void leavingSuppliesAcrossTwoAgreementsAreEachClosed() {
        Supply w = createSupply();
        // A2 is activated first, starting after A1: A1's first activation finds no row of A2 in force on its date.
        Agreement a2 = activatedAgreement("2025-03-01", z, w);
        Agreement a1 = activatedAgreement("2025-01-01", x, y);
        assertThat(timeline(z)).containsExactly(period(a2.of(z), "2025-03-01", null));
        assertThat(statusOf(a1)).isEqualTo(SharingAgreementStatus.PUBLISHED);
        assertThat(statusOf(a2)).isEqualTo(SharingAgreementStatus.PUBLISHED);
        Agreement b = pendingAgreement(x);

        activate(b, "2025-06-01", x);

        assertThat(timeline(y)).containsExactly(period(a1.of(y), "2025-01-01", "2025-06-01"));
        assertThat(timeline(z)).containsExactly(period(a2.of(z), "2025-03-01", "2025-06-01"));
        assertThat(timeline(w)).containsExactly(period(a2.of(w), "2025-03-01", "2025-06-01"));
        assertThat(statusOf(a1)).isEqualTo(SharingAgreementStatus.SUPERSEDED);
        assertThat(statusOf(a2)).isEqualTo(SharingAgreementStatus.SUPERSEDED);
    }

    @Test
    @DisplayName("SHA-010 a leaving coefficient closed on or before D is untouched, and one closed after D is shortened to D")
    void aLeavingClosureOnlyShortens() {
        Supply w = createSupply();
        Agreement a = activatedAgreement("2025-01-01", x, y, z, w);
        close(a, y, "2025-04-01");
        close(a, z, "2025-09-01");
        close(a, w, "2025-06-01");
        Agreement b = pendingAgreement(x);

        activate(b, "2025-06-01", x);

        assertThat(timeline(y)).containsExactly(period(a.of(y), "2025-01-01", "2025-04-01"));
        assertThat(timeline(z)).containsExactly(period(a.of(z), "2025-01-01", "2025-06-01"));
        assertThat(timeline(w)).containsExactly(period(a.of(w), "2025-01-01", "2025-06-01"));
    }

    @Test
    @DisplayName("SHA-010 reconstruction: rows starting after D are not touched, and nothing is closed")
    void rowsStartingAfterDAreNotTouched() {
        Agreement s = activatedAgreement("2024-05-23", x, y, z);
        Agreement o = pendingAgreement(x);

        activate(o, "2023-04-12", x);

        assertThat(timeline(x)).containsExactly(
                period(o.of(x), "2023-04-12", "2024-05-23"),
                period(s.of(x), "2024-05-23", null));
        assertThat(timeline(y)).containsExactly(period(s.of(y), "2024-05-23", null));
        assertThat(timeline(z)).containsExactly(period(s.of(z), "2024-05-23", null));
        assertThat(statusOf(s)).isEqualTo(SharingAgreementStatus.PUBLISHED);
    }

    @Test
    @DisplayName("SHA-009 a supply holding a pending coefficient of B outside the first batch is not leaving; its row stays open")
    void aSupplyPendingInTheAgreementButNotInTheBatchIsNotLeaving() {
        Agreement a = activatedAgreement("2025-01-01", x, y, z);
        Agreement b = pendingAgreement(x, y);

        activate(b, "2025-06-01", x);

        assertThat(timeline(y)).containsExactly(period(a.of(y), "2025-01-01", null));
        assertThat(timeline(z)).containsExactly(period(a.of(z), "2025-01-01", "2025-06-01"));
    }

    @Test
    @DisplayName("SHA-009 a second batch of B closes nothing")
    void aSecondBatchClosesNothing() {
        Agreement a = activatedAgreement("2025-01-01", x, y, z);
        Agreement b = pendingAgreement(x, y);
        activate(b, "2025-06-01", x);
        reopen(a, z);
        assertThat(timeline(z)).containsExactly(period(a.of(z), "2025-01-01", null));

        activate(b, "2025-07-01", y);

        assertThat(timeline(z)).containsExactly(period(a.of(z), "2025-01-01", null));
    }

    @Test
    @DisplayName("SHA-009 a correction of B's activation closes nothing")
    void aCorrectionClosesNothing() {
        Agreement a = activatedAgreement("2025-01-01", x, y, z);
        Agreement b = pendingAgreement(x);
        activate(b, "2025-06-01", x);
        reopen(a, y);
        reopen(a, z);

        activate(b, "2025-05-01", x);

        assertThat(timeline(x)).containsExactly(
                period(a.of(x), "2025-01-01", "2025-05-01"),
                period(b.of(x), "2025-05-01", null));
        assertThat(timeline(y)).containsExactly(period(a.of(y), "2025-01-01", null));
        assertThat(timeline(z)).containsExactly(period(a.of(z), "2025-01-01", null));
    }

    @Test
    @DisplayName("SHA-009 an invalid item rejects the first activation, and no leaving closure is written")
    void anInvalidItemRejectsTheActivationAndClosesNothing() {
        Agreement a = activatedAgreement("2025-01-01", x, y, z);
        Agreement b = pendingAgreement(x);
        UUID unknown = UUID.randomUUID();

        assertThatThrownBy(() -> service.setValidFrom(plant.getId(), b.id(), LocalDate.parse("2025-06-01"),
                List.of(b.of(x), unknown)))
                .isInstanceOfSatisfying(CoefficientActivationException.class, exception ->
                        assertThat(exception.getErrors()).extracting(CoefficientActivationError::getCode)
                                .containsExactly(CoefficientActivationErrorCode.COEFFICIENT_NOT_IN_AGREEMENT));

        assertThat(timeline(x)).containsExactly(period(a.of(x), "2025-01-01", null));
        assertThat(timeline(y)).containsExactly(period(a.of(y), "2025-01-01", null));
        assertThat(timeline(z)).containsExactly(period(a.of(z), "2025-01-01", null));
        assertThat(statusOf(a)).isEqualTo(SharingAgreementStatus.PUBLISHED);
    }

    @Test
    @DisplayName("SHA-011 deactivating B leaves the leaving closures in place, and reopening one works as before")
    void deactivationDoesNotReopenLeavingClosures() {
        Agreement a = activatedAgreement("2025-01-01", x, y, z);
        Agreement b = pendingAgreement(x);
        activate(b, "2025-06-01", x);

        service.setValidFrom(plant.getId(), b.id(), null, List.of(b.of(x)));

        assertThat(timeline(x)).containsExactly(period(a.of(x), "2025-01-01", null));
        assertThat(timeline(y)).containsExactly(period(a.of(y), "2025-01-01", "2025-06-01"));
        assertThat(timeline(z)).containsExactly(period(a.of(z), "2025-01-01", "2025-06-01"));

        reopen(a, y);

        assertThat(timeline(y)).containsExactly(period(a.of(y), "2025-01-01", null));
        assertThat(timeline(z)).containsExactly(period(a.of(z), "2025-01-01", "2025-06-01"));
        assertThat(statusOf(a)).isEqualTo(SharingAgreementStatus.PUBLISHED);
    }

    @Test
    @DisplayName("SHA-009 after a full deactivation, activating B again is a first activation and closes again")
    void reactivationAfterAFullDeactivationClosesAgain() {
        Agreement a = activatedAgreement("2025-01-01", x, y, z);
        Agreement b = pendingAgreement(x);
        activate(b, "2025-06-01", x);
        service.setValidFrom(plant.getId(), b.id(), null, List.of(b.of(x)));
        reopen(a, y);
        reopen(a, z);

        activate(b, "2025-07-01", x);

        assertThat(timeline(y)).containsExactly(period(a.of(y), "2025-01-01", "2025-07-01"));
        assertThat(timeline(z)).containsExactly(period(a.of(z), "2025-01-01", "2025-07-01"));
    }

    // ---- fixtures ----

    /**
     * A published agreement and the id of its coefficient for each supply.
     */
    private record Agreement(UUID id, Map<UUID, UUID> coefficientIdBySupplyId) {
        UUID of(Supply supply) {
            return coefficientIdBySupplyId.get(supply.getId());
        }
    }

    private record Period(UUID id, Instant validFrom, Instant validTo) {
    }

    private Period period(UUID coefficientId, String validFrom, String validTo) {
        return new Period(coefficientId, at(validFrom), validTo == null ? null : at(validTo));
    }

    /**
     * The activated rows of the supply's timeline in the plant, in chronological order.
     */
    private List<Period> timeline(Supply supply) {
        return getCoefficientRepository.findAllByPlantIdAndSupplyIdIn(plant.getId(), List.of(supply.getId())).stream()
                .filter(coefficient -> coefficient.getValidFrom() != null)
                .sorted(Comparator.comparing(SupplyPartitionCoefficient::getValidFrom))
                .map(coefficient -> new Period(coefficient.getId(), coefficient.getValidFrom(), coefficient.getValidTo()))
                .toList();
    }

    private Instant at(String date) {
        return LocalDate.parse(date).atStartOfDay(zoneResolver.resolveApplicationZoneId()).toInstant();
    }

    private SharingAgreementStatus statusOf(Agreement agreement) {
        return getSharingAgreementService.findById(agreement.id()).getStatus();
    }

    /**
     * A new published agreement with a coefficient for each supply, all activated on {@code appliedOn}.
     */
    private Agreement activatedAgreement(String appliedOn, Supply... supplies) {
        Agreement agreement = pendingAgreement(supplies);
        activate(agreement, appliedOn, supplies);
        return agreement;
    }

    /**
     * A new published agreement with a pending coefficient for each supply, exactly as materialisation writes them.
     */
    private Agreement pendingAgreement(Supply... supplies) {
        SharingAgreement draft = createSharingAgreementRepository.create(new SharingAgreement.Builder()
                .withId(UUID.randomUUID())
                .withPlantId(plant.getId())
                .withName("Agreement " + UUID.randomUUID())
                .withStatus(SharingAgreementStatus.DRAFT)
                .withNominalPowerKW(BigDecimal.TEN)
                .withCreatedAt(Instant.now())
                .build());
        SharingAgreement published = publishSharingAgreementRepository.publish(plant.getId(), draft.getId());
        Map<UUID, UUID> coefficientIdBySupplyId = new LinkedHashMap<>();
        for (Supply supply : supplies) {
            SupplyPartitionCoefficient saved = saveCoefficientRepository.save(new SupplyPartitionCoefficient.Builder()
                    .withId(UUID.randomUUID())
                    .withSupplyId(supply.getId())
                    .withPlantId(plant.getId())
                    .withSharingAgreementId(published.getId())
                    .withCoefficient(BigDecimal.ONE)
                    .withValidFrom(null)
                    .withValidTo(null)
                    .withCreatedAt(Instant.now())
                    .build());
            coefficientIdBySupplyId.put(supply.getId(), saved.getId());
        }
        return new Agreement(published.getId(), coefficientIdBySupplyId);
    }

    private void activate(Agreement agreement, String appliedOn, Supply... supplies) {
        service.setValidFrom(plant.getId(), agreement.id(), LocalDate.parse(appliedOn),
                Arrays.stream(supplies).map(agreement::of).toList());
    }

    private void close(Agreement agreement, Supply supply, String closedOn) {
        service.setValidTo(plant.getId(), agreement.id(), LocalDate.parse(closedOn), List.of(agreement.of(supply)));
    }

    private void reopen(Agreement agreement, Supply supply) {
        service.setValidTo(plant.getId(), agreement.id(), null, List.of(agreement.of(supply)));
    }

    private Plant createPlant() {
        Supply ownSupply = createSupply();
        return createPlantRepository.create(PlantMother.random(ownSupply).build(), SupplyId.of(ownSupply.getId()));
    }

    private Supply createSupply() {
        User owner = UserMother.randomUser();
        createUserRepository.create(owner);
        return createSupplyRepository.create(SupplyMother.random(owner).build(), UserId.of(owner.getId()),
                community.getId());
    }
}

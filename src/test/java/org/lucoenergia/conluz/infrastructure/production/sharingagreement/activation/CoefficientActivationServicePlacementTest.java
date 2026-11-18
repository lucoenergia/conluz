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
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Chronological placement of activated and corrected coefficients (#582), against PostgreSQL, so every write also
 * goes through the no_overlapping_coefficients exclusion constraint. Every timeline is built the way production
 * builds it: pending rows as materialisation writes them, then the activation and close services.
 */
@Transactional
class CoefficientActivationServicePlacementTest extends BaseIntegrationTest {

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
    @Autowired
    private Clock clock;

    private Community community;
    private Plant plant;

    @BeforeEach
    void setUp() {
        community = createCommunityRepository.create(CommunityMother.random().build());
        plant = createPlant();
    }

    // ---- SHA-003: placement ----

    @Test
    @DisplayName("SHA-003 a coefficient activated before an open one ends where the open one starts, and the open one is unchanged")
    void olderUnderAnOpenNewerOne() {
        Supply supply = createSupply();
        Coefficient open = activatedAgreement(supply, "2024-05-23");
        Coefficient older = pendingAgreement(supply);

        activate(older, "2023-04-12");

        assertThat(timeline(supply)).containsExactly(
                period(older, "2023-04-12", "2024-05-23"),
                period(open, "2024-05-23", null));
    }

    @Test
    @DisplayName("SHA-003 a coefficient activated between two ends the earlier one and ends where the later one starts")
    void betweenTwo() {
        Supply supply = createSupply();
        Coefficient earlier = activatedAgreement(supply, "2023-04-12");
        Coefficient later = activatedAgreement(supply, "2024-05-23");
        Coefficient between = pendingAgreement(supply);

        activate(between, "2024-02-01");

        assertThat(timeline(supply)).containsExactly(
                period(earlier, "2023-04-12", "2024-02-01"),
                period(between, "2024-02-01", "2024-05-23"),
                period(later, "2024-05-23", null));
    }

    @Test
    @DisplayName("SHA-003 a coefficient activated after an open one ends it and stays open")
    void afterAnOpenOne() {
        Supply supply = createSupply();
        Coefficient open = activatedAgreement(supply, "2023-04-12");
        Coefficient after = pendingAgreement(supply);

        activate(after, "2024-03-01");

        assertThat(timeline(supply)).containsExactly(
                period(open, "2023-04-12", "2024-03-01"),
                period(after, "2024-03-01", null));
    }

    @Test
    @DisplayName("SHA-003 a close an author set before the new start is kept, and the gap stays")
    void authoredCloseKept() {
        Supply supply = createSupply();
        Coefficient closed = activatedAgreement(supply, "2023-04-12");
        close(closed, "2024-01-01");
        Coefficient after = pendingAgreement(supply);

        activate(after, "2024-03-01");

        assertThat(timeline(supply)).containsExactly(
                period(closed, "2023-04-12", "2024-01-01"),
                period(after, "2024-03-01", null));
    }

    @Test
    @DisplayName("SHA-003 a close an author set after the new start is moved back to it")
    void authoredCloseShortened() {
        Supply supply = createSupply();
        Coefficient closed = activatedAgreement(supply, "2023-04-12");
        close(closed, "2024-06-01");
        Coefficient after = pendingAgreement(supply);

        activate(after, "2024-03-01");

        assertThat(timeline(supply)).containsExactly(
                period(closed, "2023-04-12", "2024-03-01"),
                period(after, "2024-03-01", null));
    }

    @Test
    @DisplayName("SHA-003 reconstructing history in reverse order under an open agreement, then deactivating it, leaves the chronological chain")
    void reconstructionInReverseOrder() {
        Supply first = createSupply();
        Supply second = createSupply();
        SharingAgreement synthetic = publishedAgreement();
        Coefficient syntheticFirst = pending(first, synthetic);
        Coefficient syntheticSecond = pending(second, synthetic);
        activate(synthetic, "2024-05-23", syntheticFirst, syntheticSecond);
        SharingAgreement imported2023April = publishedAgreement();
        Coefficient aprilFirst = pending(first, imported2023April);
        Coefficient aprilSecond = pending(second, imported2023April);
        SharingAgreement imported2023September = publishedAgreement();
        Coefficient septemberFirst = pending(first, imported2023September);
        SharingAgreement imported2024January = publishedAgreement();
        Coefficient januaryFirst = pending(first, imported2024January);
        Coefficient januarySecond = pending(second, imported2024January);

        activate(imported2024January, "2024-01-15", januaryFirst, januarySecond);
        activate(imported2023September, "2023-09-01", septemberFirst);
        activate(imported2023April, "2023-04-12", aprilFirst, aprilSecond);
        service.setValidFrom(plant.getId(), synthetic.getId(), null,
                List.of(syntheticFirst.id(), syntheticSecond.id()));

        assertThat(timeline(first)).containsExactly(
                period(aprilFirst, "2023-04-12", "2023-09-01"),
                period(septemberFirst, "2023-09-01", "2024-01-15"),
                period(januaryFirst, "2024-01-15", null));
        // The second supply is not in the September agreement: April's coefficient runs until January's.
        assertThat(timeline(second)).containsExactly(
                period(aprilSecond, "2023-04-12", "2024-01-15"),
                period(januarySecond, "2024-01-15", null));
    }

    // ---- SHA-004: correction ----

    @Test
    @DisplayName("SHA-004 a correction moving a coefficient later past its successor in another agreement joins its old neighbours and ends the successor at its new start")
    void correctionMovingLaterPastItsSuccessor() {
        Supply supply = createSupply();
        Coefficient first = activatedAgreement(supply, "2023-01-01");
        Coefficient moved = activatedAgreement(supply, "2023-06-01");
        Coefficient successor = activatedAgreement(supply, "2024-01-01");

        activate(moved, "2024-03-01");

        assertThat(timeline(supply)).containsExactly(
                period(first, "2023-01-01", "2024-01-01"),
                period(successor, "2024-01-01", "2024-03-01"),
                period(moved, "2024-03-01", null));
    }

    @Test
    @DisplayName("SHA-004 a correction moving a coefficient earlier past its predecessor joins its old neighbours and ends at the predecessor's start")
    void correctionMovingEarlierPastItsPredecessor() {
        Supply supply = createSupply();
        Coefficient predecessor = activatedAgreement(supply, "2023-01-01");
        Coefficient moved = activatedAgreement(supply, "2023-06-01");
        Coefficient last = activatedAgreement(supply, "2024-01-01");

        activate(moved, "2022-06-01");

        assertThat(timeline(supply)).containsExactly(
                period(moved, "2022-06-01", "2023-01-01"),
                period(predecessor, "2023-01-01", "2024-01-01"),
                period(last, "2024-01-01", null));
    }

    @Test
    @DisplayName("SHA-004 a correction never extends an authored close: neither the predecessor's before a gap nor the coefficient's own")
    void correctionNeverExtendsAnAuthoredClose() {
        Supply supply = createSupply();
        Coefficient closedBeforeGap = activatedAgreement(supply, "2023-01-01");
        close(closedBeforeGap, "2023-03-01");
        Coefficient moved = activatedAgreement(supply, "2023-06-01");
        close(moved, "2023-12-01");
        Coefficient later = activatedAgreement(supply, "2024-06-01");

        activate(moved, "2023-09-01");

        assertThat(timeline(supply)).containsExactly(
                period(closedBeforeGap, "2023-01-01", "2023-03-01"),
                period(moved, "2023-09-01", "2023-12-01"),
                period(later, "2024-06-01", null));
    }

    @Test
    @DisplayName("SHA-004 a correction to a date on or after the coefficient's own authored close is refused, and nothing is written")
    void correctionAtOrAfterOwnAuthoredCloseIsRefused() {
        Supply supply = createSupply();
        Coefficient closed = activatedAgreement(supply, "2023-01-01");
        close(closed, "2023-06-01");
        List<Period> before = timeline(supply);

        assertThatThrownBy(() -> activate(closed, "2023-06-01"))
                .isInstanceOfSatisfying(CoefficientActivationException.class, exception -> assertThat(codes(exception))
                        .containsExactly(CoefficientActivationErrorCode.ACTIVATION_DATE_NOT_BEFORE_SUCCESSOR));
        assertThat(timeline(supply)).isEqualTo(before);
    }

    @Test
    @DisplayName("SHA-004 a predecessor's authored close equal to the moved coefficient's start counts as cascade-derived, so a correction extends it")
    void authoredCloseEqualToTheNextStartIsTreatedAsCascadeDerived() {
        Supply supply = createSupply();
        Coefficient closed = activatedAgreement(supply, "2023-01-01");
        close(closed, "2023-06-01");
        Coefficient next = activatedAgreement(supply, "2023-06-01");

        activate(next, "2023-09-01");

        // Known limitation (#582): nothing records who wrote a validTo, so the author's close on
        // 2023-06-01 cannot be told apart from the cascade and is moved forward with the correction.
        assertThat(timeline(supply)).containsExactly(
                period(closed, "2023-01-01", "2023-09-01"),
                period(next, "2023-09-01", null));
    }

    // ---- SHA-005: refusals ----

    @Test
    @DisplayName("SHA-005 an activation on another coefficient's start is refused as an overlap with it, and nothing is written")
    void equalStartIsRefused() {
        Supply supply = createSupply();
        activatedAgreement(supply, "2023-04-12");
        Coefficient taken = activatedAgreement(supply, "2024-05-23");
        Coefficient pending = pendingAgreement(supply);
        List<Period> before = timeline(supply);

        assertThatThrownBy(() -> activate(pending, "2024-05-23"))
                .isInstanceOfSatisfying(CoefficientActivationException.class, exception -> {
                    assertThat(codes(exception)).containsExactly(CoefficientActivationErrorCode.PERIOD_OVERLAP);
                    assertThat(exception.getErrors().get(0).getParams())
                            .containsEntry("conflictingCoefficientId", taken.id().toString());
                });
        assertThat(timeline(supply)).isEqualTo(before);
    }

    @Test
    @DisplayName("SHA-005 an activation in the future is refused, and nothing is written")
    void futureDateIsRefused() {
        Supply supply = createSupply();
        activatedAgreement(supply, "2023-04-12");
        Coefficient pending = pendingAgreement(supply);
        List<Period> before = timeline(supply);
        LocalDate tomorrow = LocalDate.now(clock.withZone(zoneResolver.resolveApplicationZoneId())).plusDays(1);

        assertThatThrownBy(() -> service.setValidFrom(plant.getId(), pending.agreementId(), tomorrow,
                List.of(pending.id())))
                .isInstanceOfSatisfying(CoefficientActivationException.class, exception -> assertThat(codes(exception))
                        .containsExactly(CoefficientActivationErrorCode.DATE_IN_FUTURE));
        assertThat(timeline(supply)).isEqualTo(before);
    }

    @Test
    @DisplayName("SHA-005 a batch over several supplies is refused whole when one of them starts on another coefficient's start")
    void oneInvalidItemRejectsTheWholeBatch() {
        Supply placeable = createSupply();
        Supply alsoPlaceable = createSupply();
        Supply taken = createSupply();
        Coefficient placeableOpen = activatedAgreement(placeable, "2024-05-23");
        Coefficient alsoPlaceableOpen = activatedAgreement(alsoPlaceable, "2022-01-01");
        activatedAgreement(taken, "2023-04-12");
        SharingAgreement batch = publishedAgreement();
        Coefficient first = pending(placeable, batch);
        Coefficient second = pending(alsoPlaceable, batch);
        Coefficient third = pending(taken, batch);

        assertThatThrownBy(() -> activate(batch, "2023-04-12", first, second, third))
                .isInstanceOfSatisfying(CoefficientActivationException.class, exception -> {
                    assertThat(codes(exception)).containsExactly(CoefficientActivationErrorCode.PERIOD_OVERLAP);
                    assertThat(exception.getErrors().get(0).getParams())
                            .containsEntry("coefficientId", third.id().toString());
                });
        assertThat(timeline(placeable)).containsExactly(period(placeableOpen, "2024-05-23", null));
        // Closed on 2023-04-12 by the first activation of taken's agreement, which alsoPlaceable is absent from
        // (SHA-009), not by the refused batch: its coefficient second was not written.
        assertThat(timeline(alsoPlaceable)).containsExactly(period(alsoPlaceableOpen, "2022-01-01", "2023-04-12"));
    }

    @Test
    @DisplayName("SHA-003 a batch over several supplies places each coefficient within its own supply's timeline")
    void batchPlacesEachSupplyInItsOwnTimeline() {
        Supply underOpen = createSupply();
        Supply afterOpen = createSupply();
        Coefficient laterOpen = activatedAgreement(underOpen, "2024-05-23");
        Coefficient earlierOpen = activatedAgreement(afterOpen, "2022-01-01");
        SharingAgreement batch = publishedAgreement();
        Coefficient first = pending(underOpen, batch);
        Coefficient second = pending(afterOpen, batch);

        activate(batch, "2023-04-12", first, second);

        assertThat(timeline(underOpen)).containsExactly(
                period(first, "2023-04-12", "2024-05-23"),
                period(laterOpen, "2024-05-23", null));
        assertThat(timeline(afterOpen)).containsExactly(
                period(earlierOpen, "2022-01-01", "2023-04-12"),
                period(second, "2023-04-12", null));
    }

    // ---- SHA-006: statuses ----

    @Test
    @DisplayName("SHA-006 an agreement whose coefficients all gain an end when placed under newer ones becomes SUPERSEDED")
    void agreementPlacedUnderNewerOnesBecomesSuperseded() {
        Supply first = createSupply();
        Supply second = createSupply();
        SharingAgreement newer = publishedAgreement();
        activate(newer, "2024-05-23", pending(first, newer), pending(second, newer));
        SharingAgreement older = publishedAgreement();

        activate(older, "2023-04-12", pending(first, older), pending(second, older));

        assertThat(statusOf(older)).isEqualTo(SharingAgreementStatus.SUPERSEDED);
        assertThat(statusOf(newer)).isEqualTo(SharingAgreementStatus.PUBLISHED);
    }

    @Test
    @DisplayName("SHA-006 the status of every agreement a correction touches is recomputed, the old and the new predecessor's included")
    void correctionRecomputesEveryTouchedAgreement() {
        Supply supply = createSupply();
        SharingAgreement predecessorAgreement = publishedAgreement();
        Coefficient predecessor = pending(supply, predecessorAgreement);
        activate(predecessorAgreement, "2023-01-01", predecessor);
        SharingAgreement movedAgreement = publishedAgreement();
        Coefficient moved = pending(supply, movedAgreement);
        activate(movedAgreement, "2023-06-01", moved);
        assertThat(statusOf(predecessorAgreement)).isEqualTo(SharingAgreementStatus.SUPERSEDED);
        assertThat(statusOf(movedAgreement)).isEqualTo(SharingAgreementStatus.PUBLISHED);

        activate(moved, "2022-06-01");

        assertThat(timeline(supply)).containsExactly(
                period(moved, "2022-06-01", "2023-01-01"),
                period(predecessor, "2023-01-01", null));
        assertThat(statusOf(predecessorAgreement)).isEqualTo(SharingAgreementStatus.PUBLISHED);
        assertThat(statusOf(movedAgreement)).isEqualTo(SharingAgreementStatus.SUPERSEDED);
    }

    // ---- fixtures ----

    private record Coefficient(UUID id, UUID agreementId) {
    }

    private record Period(UUID id, Instant validFrom, Instant validTo) {
    }

    private Period period(Coefficient coefficient, String validFrom, String validTo) {
        return new Period(coefficient.id(), at(validFrom), validTo == null ? null : at(validTo));
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

    private List<CoefficientActivationErrorCode> codes(CoefficientActivationException exception) {
        return exception.getErrors().stream().map(CoefficientActivationError::getCode).toList();
    }

    private SharingAgreementStatus statusOf(SharingAgreement agreement) {
        return getSharingAgreementService.findById(agreement.getId()).getStatus();
    }

    /**
     * A new published agreement holding the supply's coefficient, activated on {@code appliedOn}.
     */
    private Coefficient activatedAgreement(Supply supply, String appliedOn) {
        Coefficient coefficient = pendingAgreement(supply);
        activate(coefficient, appliedOn);
        return coefficient;
    }

    /**
     * A new published agreement holding the supply's coefficient, not activated yet.
     */
    private Coefficient pendingAgreement(Supply supply) {
        return pending(supply, publishedAgreement());
    }

    private void activate(Coefficient coefficient, String appliedOn) {
        service.setValidFrom(plant.getId(), coefficient.agreementId(), LocalDate.parse(appliedOn),
                List.of(coefficient.id()));
    }

    private void activate(SharingAgreement agreement, String appliedOn, Coefficient... coefficients) {
        service.setValidFrom(plant.getId(), agreement.getId(), LocalDate.parse(appliedOn),
                Arrays.stream(coefficients).map(Coefficient::id).toList());
    }

    private void close(Coefficient coefficient, String closedOn) {
        service.setValidTo(plant.getId(), coefficient.agreementId(), LocalDate.parse(closedOn),
                List.of(coefficient.id()));
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

    private SharingAgreement publishedAgreement() {
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
    private Coefficient pending(Supply supply, SharingAgreement agreement) {
        SupplyPartitionCoefficient saved = saveCoefficientRepository.save(new SupplyPartitionCoefficient.Builder()
                .withId(UUID.randomUUID())
                .withSupplyId(supply.getId())
                .withPlantId(plant.getId())
                .withSharingAgreementId(agreement.getId())
                .withCoefficient(BigDecimal.ONE)
                .withValidFrom(null)
                .withValidTo(null)
                .withCreatedAt(Instant.now())
                .build());
        return new Coefficient(saved.getId(), agreement.getId());
    }
}

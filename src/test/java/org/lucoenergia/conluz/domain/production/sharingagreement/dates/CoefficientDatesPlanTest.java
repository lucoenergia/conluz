package org.lucoenergia.conluz.domain.production.sharingagreement.dates;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyPartitionCoefficient;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreement;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementStatus;
import org.lucoenergia.conluz.domain.production.sharingagreement.activation.CoefficientActivationErrorCode;
import org.lucoenergia.conluz.domain.production.sharingagreement.dates.CoefficientDatesEffects.AgreementStatusChange;
import org.lucoenergia.conluz.domain.production.sharingagreement.dates.CoefficientDatesEffects.CoefficientPeriodChange;
import org.lucoenergia.conluz.domain.production.sharingagreement.dates.CoefficientDatesEffects.CommunityStartChange;
import org.lucoenergia.conluz.domain.production.sharingagreement.dates.CoefficientDatesEffects.LeavingClosureChange;
import org.lucoenergia.conluz.domain.production.sharingagreement.dates.CoefficientDatesEffects.NeighbourChange;
import org.lucoenergia.conluz.domain.production.sharingagreement.dates.CoefficientDatesPlan.RowError;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class CoefficientDatesPlanTest {

    private static final UUID PLANT_ID = UUID.randomUUID();
    private static final UUID AGREEMENT_A = UUID.randomUUID();
    private static final UUID AGREEMENT_B = UUID.randomUUID();
    private static final UUID AGREEMENT_C = UUID.randomUUID();
    private static final UUID SUPPLY_X = UUID.randomUUID();
    private static final UUID SUPPLY_Y = UUID.randomUUID();
    private static final UUID SUPPLY_Z = UUID.randomUUID();
    private static final UUID SUPPLY_W = UUID.randomUUID();
    private static final LocalDate TODAY = LocalDate.parse("2026-01-01");
    private static final List<SharingAgreement> AGREEMENTS = List.of(
            agreement(AGREEMENT_A, SharingAgreementStatus.PUBLISHED, 0),
            agreement(AGREEMENT_B, SharingAgreementStatus.PUBLISHED, 1),
            agreement(AGREEMENT_C, SharingAgreementStatus.PUBLISHED, 2));

    /** An agreement with no sent date, created {@code hour} hours after the epoch. */
    private static SharingAgreement agreement(UUID id, SharingAgreementStatus status, int hour) {
        return new SharingAgreement.Builder()
                .withId(id)
                .withPlantId(PLANT_ID)
                .withStatus(status)
                .withCreatedAt(Instant.EPOCH.plusSeconds(3600L * hour))
                .build();
    }

    private static Instant day(String date) {
        return date == null ? null : Instant.parse(date + "T00:00:00Z");
    }

    private static SupplyPartitionCoefficient row(UUID agreementId, UUID supplyId, String validFrom, String validTo) {
        return row(UUID.randomUUID(), agreementId, supplyId, validFrom, validTo);
    }

    private static SupplyPartitionCoefficient row(UUID id, UUID agreementId, UUID supplyId, String validFrom,
                                                  String validTo) {
        return row(id, agreementId, supplyId, "1", validFrom, validTo);
    }

    private static SupplyPartitionCoefficient row(UUID agreementId, UUID supplyId, String coefficient,
                                                  String validFrom, String validTo) {
        return row(UUID.randomUUID(), agreementId, supplyId, coefficient, validFrom, validTo);
    }

    private static SupplyPartitionCoefficient row(UUID id, UUID agreementId, UUID supplyId, String coefficient,
                                                  String validFrom, String validTo) {
        return new SupplyPartitionCoefficient.Builder()
                .withId(id)
                .withSupplyId(supplyId)
                .withPlantId(PLANT_ID)
                .withSharingAgreementId(agreementId)
                .withCoefficient(new BigDecimal(coefficient))
                .withValidFrom(day(validFrom))
                .withValidTo(day(validTo))
                .withCreatedAt(Instant.EPOCH)
                .build();
    }

    private static CoefficientDate date(SupplyPartitionCoefficient row, String validFrom) {
        return new CoefficientDate(row.getId(), LocalDate.parse(validFrom));
    }

    private static LeavingClosureDate closure(UUID supplyId, String closedOn) {
        return new LeavingClosureDate(supplyId, LocalDate.parse(closedOn));
    }

    private static CoefficientDatesPlan.Result plan(List<SupplyPartitionCoefficient> timeline,
                                                    List<CoefficientDate> coefficients,
                                                    List<LeavingClosureDate> closures) {
        return plan(timeline, coefficients, closures, null);
    }

    private static CoefficientDatesPlan.Result plan(List<SupplyPartitionCoefficient> timeline,
                                                    List<CoefficientDate> coefficients,
                                                    List<LeavingClosureDate> closures, Instant earliestOutsidePlant) {
        return plan(AGREEMENT_B, AGREEMENTS, timeline, coefficients, closures, earliestOutsidePlant);
    }

    private static CoefficientDatesPlan.Result plan(UUID agreementId, List<SharingAgreement> agreements,
                                                    List<SupplyPartitionCoefficient> timeline,
                                                    List<CoefficientDate> coefficients,
                                                    List<LeavingClosureDate> closures, Instant earliestOutsidePlant) {
        return CoefficientDatesPlan.plan(new CoefficientDatesPlan.Input(agreementId, timeline, agreements, TODAY,
                ZoneOffset.UTC, new CoefficientDatesRequest(coefficients, closures), earliestOutsidePlant));
    }

    private static CoefficientDatesEffects effects(CoefficientDatesPlan.Result result) {
        assertThat(result).isInstanceOf(CoefficientDatesPlan.Planned.class);
        return ((CoefficientDatesPlan.Planned) result).effects();
    }

    private static List<RowError> errors(CoefficientDatesPlan.Result result) {
        assertThat(result).isInstanceOf(CoefficientDatesPlan.Refused.class);
        return ((CoefficientDatesPlan.Refused) result).errors();
    }

    /** A open for X, Y and Z since 2025-01-01; B holds X and Y, pending. */
    private static final class Scenario {
        final SupplyPartitionCoefficient xInA = row(AGREEMENT_A, SUPPLY_X, "2025-01-01", null);
        final SupplyPartitionCoefficient yInA = row(AGREEMENT_A, SUPPLY_Y, "2025-01-01", null);
        final SupplyPartitionCoefficient zInA = row(AGREEMENT_A, SUPPLY_Z, "2025-01-01", null);
        final SupplyPartitionCoefficient xInB = row(AGREEMENT_B, SUPPLY_X, null, null);
        final SupplyPartitionCoefficient yInB = row(AGREEMENT_B, SUPPLY_Y, null, null);

        List<SupplyPartitionCoefficient> timeline(SupplyPartitionCoefficient... extra) {
            List<SupplyPartitionCoefficient> rows = new ArrayList<>(List.of(xInA, yInA, zInA, xInB, yInB));
            rows.addAll(Arrays.asList(extra));
            return rows;
        }
    }

    // ---- SHA-012 ----

    @Test
    @DisplayName("SHA-012 coefficients of one agreement with different dates are each placed at their own date")
    void placesEachCoefficientAtItsOwnDate() {
        Scenario s = new Scenario();

        CoefficientDatesEffects effects = effects(plan(s.timeline(),
                List.of(date(s.yInB, "2025-03-05"), date(s.xInB, "2025-03-01")),
                List.of(closure(SUPPLY_Z, "2025-03-03"))));

        assertThat(effects.coefficients()).containsExactly(
                new CoefficientPeriodChange(s.xInB.getId(), SUPPLY_X, null, null, day("2025-03-01"), null, false),
                new CoefficientPeriodChange(s.yInB.getId(), SUPPLY_Y, null, null, day("2025-03-05"), null, false));
        assertThat(effects.neighbours()).containsExactly(
                new NeighbourChange(s.xInA.getId(), SUPPLY_X, AGREEMENT_A, null, day("2025-03-01")),
                new NeighbourChange(s.yInA.getId(), SUPPLY_Y, AGREEMENT_A, null, day("2025-03-05")));
        assertThat(effects.leavingClosures()).containsExactly(new LeavingClosureChange(SUPPLY_Z, s.zInA.getId(),
                AGREEMENT_A, null, day("2025-03-03"), LeavingClosureSource.REQUEST));
        assertThat(effects.agreements()).containsExactly(
                new AgreementStatusChange(AGREEMENT_B, SharingAgreementStatus.PUBLISHED, SharingAgreementStatus.PUBLISHED),
                new AgreementStatusChange(AGREEMENT_A, SharingAgreementStatus.PUBLISHED, SharingAgreementStatus.SUPERSEDED));
        assertThat(effects.communityStart()).isEqualTo(new CommunityStartChange(day("2025-01-01"), day("2025-01-01")));
    }

    @Test
    @DisplayName("SHA-012 coefficients are applied in ascending date order, whatever order the request lists them in")
    void appliesInAscendingDateOrder() {
        SupplyPartitionCoefficient xInA = row(AGREEMENT_A, SUPPLY_X, "2025-01-01", null);
        SupplyPartitionCoefficient xInB = row(AGREEMENT_B, SUPPLY_X, null, null);
        SupplyPartitionCoefficient yInB = row(AGREEMENT_B, SUPPLY_Y, "2025-02-01", null);

        // Corrects Y first by order of date, then activates X.
        CoefficientDatesEffects effects = effects(plan(List.of(xInA, xInB, yInB),
                List.of(date(xInB, "2025-04-01"), date(yInB, "2025-01-15"))));

        assertThat(effects.coefficients()).extracting(CoefficientPeriodChange::coefficientId)
                .containsExactly(yInB.getId(), xInB.getId());
    }

    private static CoefficientDatesPlan.Result plan(List<SupplyPartitionCoefficient> timeline,
                                                    List<CoefficientDate> coefficients) {
        return plan(timeline, coefficients, List.of());
    }

    @Test
    @DisplayName("SHA-012 items with the same date are applied by coefficient id")
    void tiesAreBrokenByCoefficientId() {
        UUID low = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID high = UUID.fromString("f0000000-0000-0000-0000-000000000001");
        SupplyPartitionCoefficient xInB = row(high, AGREEMENT_B, SUPPLY_X, null, null);
        SupplyPartitionCoefficient yInB = row(low, AGREEMENT_B, SUPPLY_Y, null, null);

        CoefficientDatesEffects effects = effects(plan(List.of(xInB, yInB),
                List.of(date(xInB, "2025-03-01"), date(yInB, "2025-03-01"))));

        assertThat(effects.coefficients()).extracting(CoefficientPeriodChange::coefficientId)
                .containsExactly(low, high);
    }

    @Test
    @DisplayName("SHA-012 an item whose validFrom is already stored changes nothing")
    void anItemAlreadyStoredIsANoOp() {
        SupplyPartitionCoefficient xInB = row(AGREEMENT_B, SUPPLY_X, "2025-03-01", null);

        CoefficientDatesEffects effects = effects(plan(List.of(xInB), List.of(date(xInB, "2025-03-01"))));

        assertThat(effects.coefficients()).isEmpty();
        assertThat(effects.agreements()).isEmpty();
    }

    // ---- SHA-013 ----

    @Test
    @DisplayName("SHA-013 on a first activation, an omitted leaving supply is closed at the earliest date of the request")
    void closesOmittedLeavingSuppliesOnAFirstActivation() {
        Scenario s = new Scenario();

        CoefficientDatesEffects effects = effects(plan(s.timeline(),
                List.of(date(s.xInB, "2025-03-01"), date(s.yInB, "2025-03-05")), List.of()));

        assertThat(effects.leavingClosures()).containsExactly(new LeavingClosureChange(SUPPLY_Z, s.zInA.getId(),
                AGREEMENT_A, null, day("2025-03-01"), LeavingClosureSource.DEFAULT));
    }

    @Test
    @DisplayName("SHA-013 on a later request, an omitted leaving supply is untouched")
    void laterRequestsCloseNothingByDefault() {
        Scenario s = new Scenario();
        SupplyPartitionCoefficient xInB = row(AGREEMENT_B, SUPPLY_X, "2025-03-01", null);
        SupplyPartitionCoefficient xInA = row(AGREEMENT_A, SUPPLY_X, "2025-01-01", "2025-03-01");

        CoefficientDatesEffects effects = effects(plan(List.of(xInA, xInB, s.yInA, s.yInB, s.zInA),
                List.of(date(s.yInB, "2025-03-05")), List.of()));

        assertThat(effects.leavingClosures()).isEmpty();
    }

    @Test
    @DisplayName("SHA-013 a listed leaving supply is shortened to its closing date")
    void shortensTheRowBeforeTheAgreement() {
        SupplyPartitionCoefficient zInA = row(AGREEMENT_A, SUPPLY_Z, "2025-01-01", "2025-06-01");
        SupplyPartitionCoefficient xInB = row(AGREEMENT_B, SUPPLY_X, "2025-06-01", null);

        CoefficientDatesEffects effects = effects(plan(List.of(zInA, xInB), List.of(),
                List.of(closure(SUPPLY_Z, "2025-05-01"))));

        assertThat(effects.leavingClosures()).containsExactly(new LeavingClosureChange(SUPPLY_Z, zInA.getId(),
                AGREEMENT_A, day("2025-06-01"), day("2025-05-01"), LeavingClosureSource.REQUEST));
    }

    @Test
    @DisplayName("SHA-013 a listed leaving supply is extended to its closing date when nothing starts before it")
    void extendsTheRowBeforeTheAgreement() {
        SupplyPartitionCoefficient zInA = row(AGREEMENT_A, SUPPLY_Z, "2025-01-01", "2025-06-01");
        SupplyPartitionCoefficient xInB = row(AGREEMENT_B, SUPPLY_X, "2025-06-01", null);

        CoefficientDatesEffects effects = effects(plan(List.of(zInA, xInB), List.of(),
                List.of(closure(SUPPLY_Z, "2025-07-01"))));

        assertThat(effects.leavingClosures()).containsExactly(new LeavingClosureChange(SUPPLY_Z, zInA.getId(),
                AGREEMENT_A, day("2025-06-01"), day("2025-07-01"), LeavingClosureSource.REQUEST));
    }

    @Test
    @DisplayName("SHA-013 extending a leaving supply's row up to the next start of the supply is accepted")
    void extendsUpToTheNextStart() {
        SupplyPartitionCoefficient zInA = row(AGREEMENT_A, SUPPLY_Z, "2025-01-01", "2025-06-01");
        SupplyPartitionCoefficient zInC = row(AGREEMENT_C, SUPPLY_Z, "2025-06-15", null);
        SupplyPartitionCoefficient xInB = row(AGREEMENT_B, SUPPLY_X, "2025-06-01", null);

        CoefficientDatesEffects effects = effects(plan(List.of(zInA, zInC, xInB), List.of(),
                List.of(closure(SUPPLY_Z, "2025-06-15"))));

        assertThat(effects.leavingClosures()).extracting(LeavingClosureChange::validToAfter)
                .containsExactly(day("2025-06-15"));
    }

    @Test
    @DisplayName("SHA-013 extending a leaving supply's row past the next start of the supply is refused as an overlap")
    void refusesAnExtensionPastTheNextStart() {
        SupplyPartitionCoefficient zInA = row(AGREEMENT_A, SUPPLY_Z, "2025-01-01", "2025-06-01");
        SupplyPartitionCoefficient zInC = row(AGREEMENT_C, SUPPLY_Z, "2025-06-15", null);
        SupplyPartitionCoefficient xInB = row(AGREEMENT_B, SUPPLY_X, "2025-06-01", null);

        assertThat(errors(plan(List.of(zInA, zInC, xInB), List.of(), List.of(closure(SUPPLY_Z, "2025-07-01")))))
                .containsExactly(new RowError(CoefficientActivationErrorCode.PERIOD_OVERLAP, zInA.getId(), SUPPLY_Z,
                        zInC.getId()));
    }

    @Test
    @DisplayName("SHA-013 the closed row is the one before the agreement's earliest start after this request's placements")
    void anchorsOnTheAgreementStartAfterThePlacements() {
        Scenario s = new Scenario();
        SupplyPartitionCoefficient zInC = row(AGREEMENT_C, SUPPLY_Z, "2025-02-01", null);
        SupplyPartitionCoefficient zInA = row(AGREEMENT_A, SUPPLY_Z, "2025-01-01", "2025-02-01");

        // B starts on 2025-03-01, so Z's row before it is C's, not A's.
        CoefficientDatesEffects effects = effects(plan(List.of(s.xInA, s.yInA, zInA, zInC, s.xInB, s.yInB),
                List.of(date(s.xInB, "2025-03-01")), List.of(closure(SUPPLY_Z, "2025-03-10"))));

        assertThat(effects.leavingClosures()).containsExactly(new LeavingClosureChange(SUPPLY_Z, zInC.getId(),
                AGREEMENT_C, null, day("2025-03-10"), LeavingClosureSource.REQUEST));
    }

    @Test
    @DisplayName("SHA-013 a listed supply is closed at its own date, and not again by default")
    void aListedSupplyIsNotClosedByDefault() {
        Scenario s = new Scenario();

        CoefficientDatesEffects effects = effects(plan(s.timeline(), List.of(date(s.xInB, "2025-03-01")),
                List.of(closure(SUPPLY_Z, "2025-03-03"))));

        assertThat(effects.leavingClosures()).extracting(LeavingClosureChange::source)
                .containsExactly(LeavingClosureSource.REQUEST);
    }

    // ---- SHA-014 ----

    @Test
    @DisplayName("SHA-014 the community start moves when the earliest validFrom of the community moves")
    void reportsTheCommunityStartChange() {
        SupplyPartitionCoefficient xInB = row(AGREEMENT_B, SUPPLY_X, null, null);

        CoefficientDatesEffects effects = effects(plan(List.of(xInB), List.of(date(xInB, "2024-06-01")), List.of(),
                day("2025-01-01")));

        assertThat(effects.communityStart()).isEqualTo(new CommunityStartChange(day("2025-01-01"), day("2024-06-01")));
    }

    @Test
    @DisplayName("SHA-014 the community start is reported unchanged when the earliest validFrom does not move")
    void reportsAnUnchangedCommunityStart() {
        SupplyPartitionCoefficient xInB = row(AGREEMENT_B, SUPPLY_X, null, null);

        CoefficientDatesEffects effects = effects(plan(List.of(xInB), List.of(date(xInB, "2025-06-01")), List.of(),
                day("2025-01-01")));

        assertThat(effects.communityStart()).isEqualTo(new CommunityStartChange(day("2025-01-01"), day("2025-01-01")));
    }

    @Test
    @DisplayName("SHA-014 the writes are exactly the rows the effects report")
    void writesAreTheReportedRows() {
        Scenario s = new Scenario();

        CoefficientDatesPlan.Planned planned = (CoefficientDatesPlan.Planned) plan(s.timeline(),
                List.of(date(s.xInB, "2025-03-01"), date(s.yInB, "2025-03-05")), List.of());

        assertThat(planned.writes()).extracting(SupplyPartitionCoefficient::getId).containsExactlyInAnyOrder(
                s.xInB.getId(), s.yInB.getId(), s.xInA.getId(), s.yInA.getId(), s.zInA.getId());
    }

    // ---- SHA-015 ----

    @Test
    @DisplayName("SHA-015 every invalid row is reported, and nothing is planned")
    void reportsEveryInvalidRow() {
        Scenario s = new Scenario();
        SupplyPartitionCoefficient wInC = row(AGREEMENT_C, SUPPLY_W, "2025-01-01", null);
        UUID unknown = UUID.randomUUID();
        UUID unknownSupply = UUID.randomUUID();

        List<RowError> errors = errors(plan(s.timeline(wInC),
                List.of(date(s.xInB, "2026-02-01"), date(s.yInB, "2025-01-01"), new CoefficientDate(unknown,
                        LocalDate.parse("2025-03-01")), date(wInC, "2025-02-01")),
                List.of(closure(SUPPLY_X, "2025-03-01"), closure(unknownSupply, "2025-03-01"))));

        assertThat(errors).containsExactlyInAnyOrder(
                new RowError(CoefficientActivationErrorCode.DATE_IN_FUTURE, s.xInB.getId(), SUPPLY_X, null),
                new RowError(CoefficientActivationErrorCode.PERIOD_OVERLAP, s.yInB.getId(), SUPPLY_Y, s.yInA.getId()),
                new RowError(CoefficientActivationErrorCode.COEFFICIENT_NOT_IN_AGREEMENT, unknown, null, null),
                new RowError(CoefficientActivationErrorCode.COEFFICIENT_NOT_IN_AGREEMENT, wInC.getId(), null, null),
                new RowError(CoefficientActivationErrorCode.LEAVING_SUPPLY_IN_AGREEMENT, null, SUPPLY_X, null),
                new RowError(CoefficientActivationErrorCode.NO_COEFFICIENT_TO_CLOSE, null, unknownSupply, null));
    }

    @Test
    @DisplayName("SHA-015 a closing date in the future is refused")
    void refusesAFutureClosingDate() {
        SupplyPartitionCoefficient zInA = row(AGREEMENT_A, SUPPLY_Z, "2025-01-01", "2025-06-01");
        SupplyPartitionCoefficient xInB = row(AGREEMENT_B, SUPPLY_X, "2025-06-01", null);

        assertThat(errors(plan(List.of(zInA, xInB), List.of(), List.of(closure(SUPPLY_Z, "2026-02-01")))))
                .containsExactly(new RowError(CoefficientActivationErrorCode.DATE_IN_FUTURE, zInA.getId(), SUPPLY_Z, null));
    }

    @Test
    @DisplayName("SHA-015 a closing date not after the closed row's start is refused")
    void refusesAClosingDateNotAfterTheStart() {
        SupplyPartitionCoefficient zInA = row(AGREEMENT_A, SUPPLY_Z, "2025-01-01", "2025-06-01");
        SupplyPartitionCoefficient xInB = row(AGREEMENT_B, SUPPLY_X, "2025-06-01", null);

        assertThat(errors(plan(List.of(zInA, xInB), List.of(), List.of(closure(SUPPLY_Z, "2025-01-01")))))
                .containsExactly(new RowError(CoefficientActivationErrorCode.CLOSURE_DATE_NOT_AFTER_ACTIVATION,
                        zInA.getId(), SUPPLY_Z, null));
    }

    @Test
    @DisplayName("SHA-015 a leaving closure on an agreement that has no start yet finds nothing to close")
    void refusesAClosureWhenTheAgreementHasNoStart() {
        Scenario s = new Scenario();

        assertThat(errors(plan(s.timeline(), List.of(), List.of(closure(SUPPLY_Z, "2025-03-01")))))
                .containsExactly(new RowError(CoefficientActivationErrorCode.NO_COEFFICIENT_TO_CLOSE, null, SUPPLY_Z,
                        null));
    }

    @Test
    @DisplayName("SHA-015 a correction on or after the coefficient's own close is refused")
    void refusesACorrectionPastTheOwnClose() {
        SupplyPartitionCoefficient xInB = row(AGREEMENT_B, SUPPLY_X, "2025-03-01", "2025-05-01");

        assertThat(errors(plan(List.of(xInB), List.of(date(xInB, "2025-05-01")))))
                .containsExactly(new RowError(CoefficientActivationErrorCode.ACTIVATION_DATE_NOT_BEFORE_SUCCESSOR,
                        xInB.getId(), SUPPLY_X, null));
    }

    // ---- SHA-020 ----

    @Test
    @DisplayName("SHA-020 leaving by zero changes no placement, closure, status or community start")
    void leavingByZeroChangesNothingElse() {
        SupplyPartitionCoefficient xInA = row(AGREEMENT_A, SUPPLY_X, "0.04", "2025-01-01", null);
        SupplyPartitionCoefficient yInA = row(AGREEMENT_A, SUPPLY_Y, "0.03", "2025-01-01", null);
        SupplyPartitionCoefficient zInA = row(AGREEMENT_A, SUPPLY_Z, "0.02", "2025-01-01", null);
        SupplyPartitionCoefficient xInB = row(AGREEMENT_B, SUPPLY_X, "0", null, null);
        SupplyPartitionCoefficient yInB = row(AGREEMENT_B, SUPPLY_Y, "0.03", null, null);
        SupplyPartitionCoefficient wInB = row(AGREEMENT_B, SUPPLY_W, "0.05", null, null);

        CoefficientDatesEffects effects = effects(plan(List.of(xInA, yInA, zInA, xInB, yInB, wInB),
                List.of(date(xInB, "2025-03-01"), date(yInB, "2025-03-01"), date(wInB, "2025-03-01")), List.of()));

        assertThat(effects.coefficients()).extracting(CoefficientPeriodChange::coefficientId,
                        CoefficientPeriodChange::supplyId, CoefficientPeriodChange::validFromBefore,
                        CoefficientPeriodChange::validToBefore, CoefficientPeriodChange::validFromAfter,
                        CoefficientPeriodChange::validToAfter)
                .containsExactlyInAnyOrder(
                        tuple(xInB.getId(), SUPPLY_X, null, null, day("2025-03-01"), null),
                        tuple(yInB.getId(), SUPPLY_Y, null, null, day("2025-03-01"), null),
                        tuple(wInB.getId(), SUPPLY_W, null, null, day("2025-03-01"), null));
        assertThat(effects.neighbours()).containsExactlyInAnyOrder(
                new NeighbourChange(xInA.getId(), SUPPLY_X, AGREEMENT_A, null, day("2025-03-01")),
                new NeighbourChange(yInA.getId(), SUPPLY_Y, AGREEMENT_A, null, day("2025-03-01")));
        assertThat(effects.leavingClosures()).containsExactly(new LeavingClosureChange(SUPPLY_Z, zInA.getId(),
                AGREEMENT_A, null, day("2025-03-01"), LeavingClosureSource.DEFAULT));
        assertThat(effects.agreements()).containsExactlyInAnyOrder(
                new AgreementStatusChange(AGREEMENT_B, SharingAgreementStatus.PUBLISHED, SharingAgreementStatus.PUBLISHED),
                new AgreementStatusChange(AGREEMENT_A, SharingAgreementStatus.PUBLISHED, SharingAgreementStatus.SUPERSEDED));
        assertThat(effects.communityStart()).isEqualTo(new CommunityStartChange(day("2025-01-01"), day("2025-01-01")));
    }

    @Test
    @DisplayName("SHA-020 a coefficient at 0 after 4 % in the previous agreement leaves; its predecessor's end moves as any")
    void dropToZeroLeaves() {
        SupplyPartitionCoefficient xInA = row(AGREEMENT_A, SUPPLY_X, "0.04", "2025-01-01", null);
        SupplyPartitionCoefficient yInA = row(AGREEMENT_A, SUPPLY_Y, "0.03", "2025-01-01", null);
        SupplyPartitionCoefficient xInB = row(AGREEMENT_B, SUPPLY_X, "0.000000", null, null);
        SupplyPartitionCoefficient yInB = row(AGREEMENT_B, SUPPLY_Y, "0.03", null, null);

        CoefficientDatesEffects effects = effects(plan(List.of(xInA, yInA, xInB, yInB),
                List.of(date(xInB, "2025-03-01"), date(yInB, "2025-03-01")), List.of()));

        assertThat(effects.coefficients()).containsExactlyInAnyOrder(
                new CoefficientPeriodChange(xInB.getId(), SUPPLY_X, null, null, day("2025-03-01"), null, true),
                new CoefficientPeriodChange(yInB.getId(), SUPPLY_Y, null, null, day("2025-03-01"), null, false));
        assertThat(effects.neighbours()).containsExactlyInAnyOrder(
                new NeighbourChange(xInA.getId(), SUPPLY_X, AGREEMENT_A, null, day("2025-03-01")),
                new NeighbourChange(yInA.getId(), SUPPLY_Y, AGREEMENT_A, null, day("2025-03-01")));
        assertThat(effects.leavingClosures()).isEmpty();
    }

    @Test
    @DisplayName("SHA-020 a coefficient at 0 in the supply's first agreement does not leave")
    void zeroInTheFirstAgreementDoesNotLeave() {
        SupplyPartitionCoefficient xInA = row(AGREEMENT_A, SUPPLY_X, "0", null, null);
        SupplyPartitionCoefficient yInA = row(AGREEMENT_A, SUPPLY_Y, "1", null, null);

        CoefficientDatesEffects effects = effects(plan(AGREEMENT_A, AGREEMENTS, List.of(xInA, yInA),
                List.of(date(xInA, "2025-01-01"), date(yInA, "2025-01-01")), List.of(), null));

        assertThat(effects.coefficients()).extracting(CoefficientPeriodChange::leavesDistribution)
                .containsExactly(false, false);
    }

    @Test
    @DisplayName("SHA-020 a coefficient at 0 after 0 in the previous agreement does not leave")
    void zeroAfterZeroDoesNotLeave() {
        SupplyPartitionCoefficient xInA = row(AGREEMENT_A, SUPPLY_X, "0", "2025-01-01", null);
        SupplyPartitionCoefficient yInA = row(AGREEMENT_A, SUPPLY_Y, "1", "2025-01-01", null);
        SupplyPartitionCoefficient xInB = row(AGREEMENT_B, SUPPLY_X, "0", null, null);
        SupplyPartitionCoefficient yInB = row(AGREEMENT_B, SUPPLY_Y, "1", null, null);

        CoefficientDatesEffects effects = effects(plan(List.of(xInA, yInA, xInB, yInB),
                List.of(date(xInB, "2025-03-01"), date(yInB, "2025-03-01")), List.of()));

        assertThat(effects.coefficients()).extracting(CoefficientPeriodChange::leavesDistribution)
                .containsExactly(false, false);
    }

    @Test
    @DisplayName("SHA-020 a supply at 4 % in A, absent from B and at 0 in C leaves in B by absence, not again in C")
    void zeroAfterAnAbsenceDoesNotLeave() {
        SupplyPartitionCoefficient xInA = row(AGREEMENT_A, SUPPLY_X, "0.04", "2025-01-01", null);
        SupplyPartitionCoefficient yInA = row(AGREEMENT_A, SUPPLY_Y, "0.96", "2025-01-01", null);
        SupplyPartitionCoefficient yInB = row(AGREEMENT_B, SUPPLY_Y, "1", null, null);
        SupplyPartitionCoefficient xInC = row(AGREEMENT_C, SUPPLY_X, "0", null, null);
        SupplyPartitionCoefficient yInC = row(AGREEMENT_C, SUPPLY_Y, "1", null, null);

        CoefficientDatesEffects datingB = effects(plan(List.of(xInA, yInA, yInB, xInC, yInC),
                List.of(date(yInB, "2025-02-01")), List.of()));

        assertThat(datingB.leavingClosures()).containsExactly(new LeavingClosureChange(SUPPLY_X, xInA.getId(),
                AGREEMENT_A, null, day("2025-02-01"), LeavingClosureSource.DEFAULT));

        SupplyPartitionCoefficient xClosedInA = row(xInA.getId(), AGREEMENT_A, SUPPLY_X, "0.04", "2025-01-01",
                "2025-02-01");
        SupplyPartitionCoefficient yClosedInA = row(yInA.getId(), AGREEMENT_A, SUPPLY_Y, "0.96", "2025-01-01",
                "2025-02-01");
        SupplyPartitionCoefficient yDatedInB = row(yInB.getId(), AGREEMENT_B, SUPPLY_Y, "1", "2025-02-01", null);
        CoefficientDatesEffects datingC = effects(plan(AGREEMENT_C, AGREEMENTS,
                List.of(xClosedInA, yClosedInA, yDatedInB, xInC, yInC),
                List.of(date(xInC, "2025-03-01"), date(yInC, "2025-03-01")), List.of(), null));

        assertThat(datingC.coefficients()).extracting(CoefficientPeriodChange::coefficientId,
                        CoefficientPeriodChange::leavesDistribution)
                .containsExactlyInAnyOrder(tuple(xInC.getId(), false), tuple(yInC.getId(), false));
    }

    @Test
    @DisplayName("SHA-020 a coefficient at 3 % after 4 % does not leave")
    void nonZeroValueDoesNotLeave() {
        SupplyPartitionCoefficient xInA = row(AGREEMENT_A, SUPPLY_X, "0.04", "2025-01-01", null);
        SupplyPartitionCoefficient yInA = row(AGREEMENT_A, SUPPLY_Y, "0.96", "2025-01-01", null);
        SupplyPartitionCoefficient xInB = row(AGREEMENT_B, SUPPLY_X, "0.03", null, null);
        SupplyPartitionCoefficient yInB = row(AGREEMENT_B, SUPPLY_Y, "0.97", null, null);

        CoefficientDatesEffects effects = effects(plan(List.of(xInA, yInA, xInB, yInB),
                List.of(date(xInB, "2025-03-01"), date(yInB, "2025-03-01")), List.of()));

        assertThat(effects.coefficients()).extracting(CoefficientPeriodChange::leavesDistribution)
                .containsExactly(false, false);
    }

    @Test
    @DisplayName("SHA-020 a DRAFT is ignored when finding the previous agreement")
    void draftIsNotThePreviousAgreement() {
        UUID draftId = UUID.randomUUID();
        // Sent on 2025-02-01, the draft would come right before B, which starts on 2025-03-01, were it in scope.
        SharingAgreement draft = new SharingAgreement.Builder()
                .withId(draftId)
                .withPlantId(PLANT_ID)
                .withStatus(SharingAgreementStatus.DRAFT)
                .withCreatedAt(Instant.EPOCH)
                .withDistributorFileSentOn(LocalDate.parse("2025-02-01"))
                .build();
        SupplyPartitionCoefficient xInA = row(AGREEMENT_A, SUPPLY_X, "0.04", "2025-01-01", null);
        SupplyPartitionCoefficient yInA = row(AGREEMENT_A, SUPPLY_Y, "0.96", "2025-01-01", null);
        SupplyPartitionCoefficient xInDraft = row(draftId, SUPPLY_X, "0", null, null);
        SupplyPartitionCoefficient xInB = row(AGREEMENT_B, SUPPLY_X, "0", null, null);
        SupplyPartitionCoefficient yInB = row(AGREEMENT_B, SUPPLY_Y, "1", null, null);
        List<SharingAgreement> agreements = new ArrayList<>(AGREEMENTS);
        agreements.add(draft);

        CoefficientDatesEffects effects = effects(plan(AGREEMENT_B, agreements,
                List.of(xInA, yInA, xInDraft, xInB, yInB),
                List.of(date(xInB, "2025-03-01"), date(yInB, "2025-03-01")), List.of(), null));

        assertThat(effects.coefficients()).extracting(CoefficientPeriodChange::coefficientId,
                        CoefficientPeriodChange::leavesDistribution)
                .containsExactlyInAnyOrder(tuple(xInB.getId(), true), tuple(yInB.getId(), false));
    }

    @Test
    @DisplayName("SHA-020 a coefficient at 0 whose validFrom is already the requested one is not placed, so not reported")
    void zeroAlreadyAtTheRequestedDateIsNotReported() {
        SupplyPartitionCoefficient xInA = row(AGREEMENT_A, SUPPLY_X, "0.04", "2025-01-01", "2025-03-01");
        SupplyPartitionCoefficient yInA = row(AGREEMENT_A, SUPPLY_Y, "0.96", "2025-01-01", null);
        SupplyPartitionCoefficient xInB = row(AGREEMENT_B, SUPPLY_X, "0", "2025-03-01", null);
        SupplyPartitionCoefficient yInB = row(AGREEMENT_B, SUPPLY_Y, "1", null, null);

        CoefficientDatesEffects effects = effects(plan(List.of(xInA, yInA, xInB, yInB),
                List.of(date(xInB, "2025-03-01"), date(yInB, "2025-03-05")), List.of()));

        assertThat(effects.coefficients()).extracting(CoefficientPeriodChange::coefficientId,
                        CoefficientPeriodChange::leavesDistribution)
                .containsExactly(tuple(yInB.getId(), false));
    }

    @Test
    @DisplayName("SHA-020 the registered order is read on the timeline as the request leaves it")
    void registeredOrderIsTheProjectedOne() {
        // None has a sent date. Before the request B has no start and comes last, after C (x at 0); dated on
        // 2025-01-15, it comes between A (x at 4 %) and C.
        SupplyPartitionCoefficient xInA = row(AGREEMENT_A, SUPPLY_X, "0.04", "2025-01-01", "2025-02-01");
        SupplyPartitionCoefficient yInA = row(AGREEMENT_A, SUPPLY_Y, "0.96", "2025-01-01", "2025-02-01");
        SupplyPartitionCoefficient xInC = row(AGREEMENT_C, SUPPLY_X, "0", "2025-02-01", null);
        SupplyPartitionCoefficient yInC = row(AGREEMENT_C, SUPPLY_Y, "1", "2025-02-01", null);
        SupplyPartitionCoefficient xInB = row(AGREEMENT_B, SUPPLY_X, "0", null, null);
        SupplyPartitionCoefficient yInB = row(AGREEMENT_B, SUPPLY_Y, "1", null, null);

        CoefficientDatesEffects effects = effects(plan(List.of(xInA, yInA, xInC, yInC, xInB, yInB),
                List.of(date(xInB, "2025-01-15"), date(yInB, "2025-01-15")), List.of()));

        assertThat(effects.coefficients()).extracting(CoefficientPeriodChange::coefficientId,
                        CoefficientPeriodChange::validToAfter, CoefficientPeriodChange::leavesDistribution)
                .containsExactlyInAnyOrder(tuple(xInB.getId(), day("2025-02-01"), true),
                        tuple(yInB.getId(), day("2025-02-01"), false));
    }
}

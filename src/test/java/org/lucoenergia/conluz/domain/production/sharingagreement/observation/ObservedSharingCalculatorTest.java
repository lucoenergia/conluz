package org.lucoenergia.conluz.domain.production.sharingagreement.observation;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.lucoenergia.conluz.domain.production.sharingagreement.observation.ObservationFixture.agreementOf;
import static org.lucoenergia.conluz.domain.production.sharingagreement.observation.ObservationFixture.anomaliesOf;
import static org.lucoenergia.conluz.domain.production.sharingagreement.observation.ObservationFixture.day;
import static org.lucoenergia.conluz.domain.production.sharingagreement.observation.ObservationFixture.leavingsOf;
import static org.lucoenergia.conluz.domain.production.sharingagreement.observation.ObservationFixture.rowOf;

/**
 * Unit tests of the observed sharing computation, written from the rules of {@code docs/specs/observed-sharing.md}
 * and the acceptance criteria of #579. Every fixture holds more than one supply and more than one agreement.
 */
class ObservedSharingCalculatorTest {

    private static final LocalDate FROM = day("2024-04-01");
    private static final LocalDate TO = day("2024-06-30");
    private static final LocalDate REGRESSION_CHANGE = day("2024-05-10");

    @Nested
    class ApplicationDates {

        @Test
        @DisplayName("OBS-001 a run of N matching valid days dates the coefficient on the run's first day")
        void runOfNDaysDatesTheCoefficient() {
            ObservationFixture fixture = new ObservationFixture(FROM, TO);
            ObservedSupply first = fixture.supply("S1");
            ObservedSupply second = fixture.supply("S2");
            ObservedAgreement a = fixture.agreement("A", day("2024-03-01"));
            ObservedAgreement b = fixture.agreement("B", day("2024-05-01"));
            fixture.row(first, a, "0.03", day("2024-03-01"), day("2024-05-10"));
            fixture.row(second, a, "0.02", day("2024-03-01"), day("2024-05-10"));
            ObservedCoefficient firstInB = fixture.row(first, b, "0.04", day("2024-05-10"), null);
            ObservedCoefficient secondInB = fixture.row(second, b, "0.05", day("2024-05-10"), null);
            fixture.beta(first, FROM, day("2024-05-09"), 0.03).beta(first, day("2024-05-10"), TO, 0.04);
            fixture.beta(second, FROM, day("2024-05-09"), 0.02).beta(second, day("2024-05-10"), TO, 0.05);

            PlantSharingObservation result = fixture.calculate();

            assertThat(rowOf(result, firstInB).observedDate()).isEqualTo(ObservedDate.exact(day("2024-05-10")));
            assertThat(rowOf(result, secondInB).observedDate()).isEqualTo(ObservedDate.exact(day("2024-05-10")));
            assertThat(rowOf(result, firstInB).status()).isEqualTo(ObservationStatus.APPLIED_SAME_DATE);
        }

        @Test
        @DisplayName("OBS-001 fewer than N matching valid days observe nothing")
        void fewerThanNDaysObserveNothing() {
            ObservationFixture fixture = new ObservationFixture(FROM, TO);
            ObservedSupply first = fixture.supply("S1");
            ObservedSupply second = fixture.supply("S2");
            ObservedAgreement a = fixture.agreement("A", day("2024-03-01"));
            ObservedAgreement b = fixture.agreement("B", null);
            fixture.row(first, a, "0.03", day("2024-03-01"), null);
            fixture.row(second, a, "0.02", day("2024-03-01"), null);
            ObservedCoefficient firstInB = fixture.row(first, b, "0.04", (LocalDate) null, null);
            fixture.row(second, b, "0.02", (LocalDate) null, null);
            fixture.beta(first, FROM, TO, 0.03).beta(first, day("2024-05-10"), day("2024-05-11"), 0.04);
            fixture.beta(second, FROM, TO, 0.02);

            PlantSharingObservation result = fixture.calculate();

            assertThat(rowOf(result, firstInB).observedDate()).isNull();
            assertThat(rowOf(result, firstInB).status()).isEqualTo(ObservationStatus.NOT_OBSERVED);
        }

        @Test
        @DisplayName("OBS-001 a gap before the run yields a range spanning it")
        void gapBeforeTheRunYieldsARange() {
            ObservationFixture fixture = new ObservationFixture(FROM, TO);
            ObservedSupply first = fixture.supply("S1");
            ObservedSupply second = fixture.supply("S2");
            ObservedAgreement a = fixture.agreement("A", day("2024-03-01"));
            ObservedAgreement b = fixture.agreement("B", day("2024-05-01"));
            fixture.row(first, a, "0.03", day("2024-03-01"), null);
            fixture.row(second, a, "0.02", day("2024-03-01"), null);
            ObservedCoefficient firstInB = fixture.row(first, b, "0.04", day("2024-05-09"), null);
            fixture.row(second, b, "0.02", day("2024-05-09"), null);
            fixture.beta(first, FROM, day("2024-05-07"), 0.03).beta(first, day("2024-05-10"), TO, 0.04);
            fixture.beta(second, FROM, TO, 0.02);
            fixture.noPlantData(day("2024-05-08"), day("2024-05-09"));

            PlantSharingObservation result = fixture.calculate();

            assertThat(rowOf(result, firstInB).observedDate())
                    .isEqualTo(new ObservedDate(day("2024-05-08"), day("2024-05-10")));
            assertThat(rowOf(result, firstInB).status()).isEqualTo(ObservationStatus.APPLIED_SAME_DATE);
        }

        @Test
        @DisplayName("OBS-001 a run starting on the supply's first valid day is open: on or before that day")
        void runOnTheFirstValidDayIsOpen() {
            ObservationFixture fixture = new ObservationFixture(FROM, TO);
            ObservedSupply first = fixture.supply("S1");
            ObservedSupply second = fixture.supply("S2");
            ObservedAgreement a = fixture.agreement("A", day("2023-01-10"));
            ObservedAgreement b = fixture.agreement("B", day("2024-05-01"));
            ObservedCoefficient firstInA = fixture.row(first, a, "0.03", day("2023-01-15"), day("2024-05-10"));
            fixture.row(second, a, "0.02", day("2023-01-15"), day("2024-05-10"));
            fixture.row(first, b, "0.04", day("2024-05-10"), null);
            fixture.row(second, b, "0.05", day("2024-05-10"), null);
            fixture.beta(first, FROM, day("2024-05-09"), 0.03).beta(first, day("2024-05-10"), TO, 0.04);
            fixture.beta(second, FROM, day("2024-05-09"), 0.02).beta(second, day("2024-05-10"), TO, 0.05);

            PlantSharingObservation result = fixture.calculate();

            assertThat(rowOf(result, firstInA).observedDate()).isEqualTo(ObservedDate.onOrBefore(FROM));
        }

        @Test
        @DisplayName("OBS-001 ambiguous days, matching both the current and the new value, widen the new value's range")
        void ambiguousDaysWidenTheRange() {
            ObservationFixture fixture = new ObservationFixture(FROM, TO);
            ObservedSupply first = fixture.supply("S1");
            ObservedSupply second = fixture.supply("S2");
            ObservedAgreement a = fixture.agreement("A", day("2024-03-01"));
            ObservedAgreement c = fixture.agreement("C", day("2024-05-01"));
            fixture.row(first, a, "0.0300", day("2024-03-01"), day("2024-05-10"));
            fixture.row(second, a, "0.02", day("2024-03-01"), day("2024-05-10"));
            ObservedCoefficient firstInC = fixture.row(first, c, "0.0315", day("2024-05-10"), null);
            fixture.row(second, c, "0.02", day("2024-05-10"), null);
            fixture.beta(first, FROM, day("2024-05-09"), 0.0300);
            // β around 3.08 %: within 0.1 pp of both 3.00 % and 3.15 %.
            fixture.beta(first, day("2024-05-10"), day("2024-05-10"), 0.0307)
                    .beta(first, day("2024-05-11"), day("2024-05-11"), 0.0309)
                    .beta(first, day("2024-05-12"), day("2024-05-12"), 0.0308)
                    .beta(first, day("2024-05-13"), day("2024-05-13"), 0.0307);
            fixture.beta(first, day("2024-05-14"), TO, 0.0315);
            fixture.beta(second, FROM, TO, 0.02);

            PlantSharingObservation result = fixture.calculate();

            assertThat(rowOf(result, firstInC).observedDate())
                    .isEqualTo(new ObservedDate(day("2024-05-10"), day("2024-05-14")));
            assertThat(rowOf(result, firstInC).status()).isEqualTo(ObservationStatus.APPLIED_SAME_DATE);
            assertThat(anomaliesOf(result, first)).isEmpty();
        }

        @Test
        @DisplayName("OBS-001 each row of an agreement holding several for one supply is its own candidate")
        void eachRowIsItsOwnCandidate() {
            ObservationFixture fixture = new ObservationFixture(FROM, TO);
            ObservedSupply first = fixture.supply("S1");
            ObservedSupply second = fixture.supply("S2");
            ObservedAgreement synthetic = fixture.agreement("Synthetic", null);
            ObservedAgreement later = fixture.agreement("Later", day("2024-06-01"));
            ObservedCoefficient early = fixture.row(first, synthetic, "0.02", day("2024-01-01"), day("2024-05-01"));
            ObservedCoefficient second2 = fixture.row(first, synthetic, "0.03", day("2024-05-01"), null);
            fixture.row(second, synthetic, "0.05", day("2024-01-01"), null);
            fixture.row(first, later, "0.06", (LocalDate) null, null);
            fixture.row(second, later, "0.05", (LocalDate) null, null);
            fixture.beta(first, FROM, day("2024-04-30"), 0.02).beta(first, day("2024-05-01"), TO, 0.03);
            fixture.beta(second, FROM, TO, 0.05);

            PlantSharingObservation result = fixture.calculate();

            assertThat(rowOf(result, early).observedDate()).isEqualTo(ObservedDate.onOrBefore(FROM));
            assertThat(rowOf(result, second2).observedDate()).isEqualTo(ObservedDate.exact(day("2024-05-01")));
            assertThat(rowOf(result, second2).status()).isEqualTo(ObservationStatus.APPLIED_SAME_DATE);
            assertThat(anomaliesOf(result, first)).isEmpty();
        }

        @Test
        @DisplayName("OBS-001 a stored start at 00:00 UTC of the observed local day is the same date")
        void storedStartAtUtcMidnightIsTheSameLocalDay() {
            ObservationFixture fixture = new ObservationFixture(FROM, TO);
            ObservedSupply first = fixture.supply("S1");
            ObservedSupply second = fixture.supply("S2");
            ObservedAgreement a = fixture.agreement("A", day("2024-03-01"));
            ObservedAgreement b = fixture.agreement("B", day("2024-05-01"));
            fixture.row(first, a, "0.03", day("2024-03-01"), null);
            fixture.row(second, a, "0.02", day("2024-03-01"), null);
            ObservedCoefficient firstInB = fixture.rowAt(first, b, "0.04",
                    day("2024-05-10").atStartOfDay(ZoneOffset.UTC).toInstant(), null);
            fixture.rowAt(second, b, "0.02", day("2024-05-10").atStartOfDay(ZoneOffset.UTC).toInstant(), null);
            fixture.beta(first, FROM, day("2024-05-09"), 0.03).beta(first, day("2024-05-10"), TO, 0.04);
            fixture.beta(second, FROM, TO, 0.02);

            PlantSharingObservation result = fixture.calculate();

            assertThat(rowOf(result, firstInB).storedValidFrom()).isEqualTo(day("2024-05-10"));
            assertThat(rowOf(result, firstInB).status()).isEqualTo(ObservationStatus.APPLIED_SAME_DATE);
        }
    }

    @Nested
    class ValidDays {

        @Test
        @DisplayName("OBS-001 days below ENM-001 coverage are not used, the 23 hours of a daylight saving day included")
        void daysBelowCoverageAreNotUsed() {
            LocalDate from = day("2024-03-25");
            ObservationFixture fixture = new ObservationFixture(from, day("2024-04-30"));
            ObservedSupply first = fixture.supply("S1");
            ObservedSupply second = fixture.supply("S2");
            ObservedAgreement a = fixture.agreement("A", day("2024-03-01"));
            ObservedAgreement b = fixture.agreement("B", day("2024-03-01"));
            fixture.row(first, a, "0.03", day("2024-03-01"), null);
            fixture.row(second, a, "0.02", day("2024-03-01"), null);
            ObservedCoefficient firstInB = fixture.row(first, b, "0.04", (LocalDate) null, null);
            ObservedCoefficient secondInB = fixture.row(second, b, "0.05", (LocalDate) null, null);
            // S1 moves to B on 30 March: its first day there is published for 21 of 24 hours, under 90 %.
            fixture.beta(first, from, day("2024-03-29"), 0.03).beta(first, day("2024-03-30"), day("2024-04-30"), 0.04)
                    .supplyPublishedHours(first, day("2024-03-30"), 21);
            // S2 moves to B on 31 March, a 23-hour day: 21 of its 23 hours is over 90 %.
            fixture.beta(second, from, day("2024-03-30"), 0.02).beta(second, day("2024-03-31"), day("2024-04-30"), 0.05)
                    .supplyPublishedHours(second, day("2024-03-31"), 21);
            // On 4 April the plant supply itself is published for 21 hours only.
            fixture.plantPublishedHours(day("2024-04-04"), 21);

            PlantSharingObservation result = fixture.calculate();

            assertThat(rowOf(result, firstInB).observedDate())
                    .isEqualTo(new ObservedDate(day("2024-03-30"), day("2024-03-31")));
            assertThat(rowOf(result, secondInB).observedDate()).isEqualTo(ObservedDate.exact(day("2024-03-31")));
            // B is applied from 31 March to 30 April: 31 days, less 4 April, when the plant supply is not published.
            assertThat(agreementOf(result, b).unallocatedShare().days()).isEqualTo(30);
        }

        @Test
        @DisplayName("OBS-001 on a large plant the peak power term decides: under 0.5 kWh/kWp the day is not used")
        void largePlantUsesThePeakPowerTerm() {
            // 200 kWp: the threshold is 100 kWh, above the 12 kWh floor.
            assertThat(observedWithProductionOnTheRunsFirstDay(200, 99.99)).isNull();
            assertThat(observedWithProductionOnTheRunsFirstDay(200, 100.0))
                    .isEqualTo(ObservedDate.exact(day("2024-05-10")));
        }

        @Test
        @DisplayName("OBS-001 on an 8 kWp plant the 12 kWh floor decides: under 12 kWh the day is not used")
        void smallPlantUsesTheFloor() {
            // 8 kWp: 0.5 kWh/kWp would accept 4 kWh; the floor requires 12 kWh.
            assertThat(observedWithProductionOnTheRunsFirstDay(8, 11.99)).isNull();
            assertThat(observedWithProductionOnTheRunsFirstDay(8, 12.0))
                    .isEqualTo(ObservedDate.exact(day("2024-05-10")));
        }

        /**
         * B's date when the run of N = 3 days starting on 10 May has the given production on its first day, the
         * other days producing well over the threshold: a run that loses a day is not observed.
         */
        private ObservedDate observedWithProductionOnTheRunsFirstDay(double peakPowerKWp, double productionKWh) {
            ObservationFixture fixture = new ObservationFixture(FROM, TO).peakPowerKWp(peakPowerKWp);
            for (LocalDate day = FROM; !day.isAfter(TO); day = day.plusDays(1)) {
                fixture.production(day, 300);
            }
            ObservedSupply first = fixture.supply("S1");
            ObservedSupply second = fixture.supply("S2");
            ObservedAgreement a = fixture.agreement("A", day("2024-03-01"));
            ObservedAgreement b = fixture.agreement("B", day("2024-05-01"));
            fixture.row(first, a, "0.03", day("2024-03-01"), null);
            fixture.row(second, a, "0.02", day("2024-03-01"), null);
            ObservedCoefficient firstInB = fixture.row(first, b, "0.04", day("2024-05-10"), null);
            fixture.row(second, b, "0.02", day("2024-05-10"), null);
            fixture.beta(first, FROM, day("2024-05-09"), 0.03)
                    .beta(first, day("2024-05-10"), day("2024-05-12"), 0.04)
                    .beta(first, day("2024-05-13"), TO, 0.03);
            fixture.beta(second, FROM, TO, 0.02);
            fixture.production(day("2024-05-10"), productionKWh);
            return rowOf(fixture.calculate(), firstInB).observedDate();
        }
    }

    @Nested
    class Statuses {

        @Test
        @DisplayName("OBS-002 an observed coefficient without a stored start is applied on a different date")
        void undatedObservedCoefficientIsDifferent() {
            ObservationFixture fixture = twoAgreementsWithChangeOn(day("2024-05-10"), null);

            PlantSharingObservation result = fixture.calculate();

            assertThat(rowOf(result, changed).status()).isEqualTo(ObservationStatus.APPLIED_DIFFERENT_DATE);
            assertThat(rowOf(result, changed).observedDate()).isEqualTo(ObservedDate.exact(day("2024-05-10")));
        }

        @Test
        @DisplayName("OBS-002 an observed coefficient stored on another day is applied on a different date")
        void observedCoefficientStoredOnAnotherDayIsDifferent() {
            ObservationFixture fixture = twoAgreementsWithChangeOn(day("2024-05-10"), day("2024-05-01"));

            assertThat(rowOf(fixture.calculate(), changed).status())
                    .isEqualTo(ObservationStatus.APPLIED_DIFFERENT_DATE);
        }

        @Test
        @DisplayName("OBS-002 an unobserved coefficient of a sent agreement is pending application, of an unsent one not observed")
        void unobservedCoefficientIsPendingOrNotObserved() {
            ObservationFixture fixture = new ObservationFixture(FROM, TO);
            ObservedSupply first = fixture.supply("S1");
            ObservedSupply second = fixture.supply("S2");
            ObservedAgreement a = fixture.agreement("A", day("2024-03-01"));
            ObservedAgreement sent = fixture.agreement("Sent", day("2024-06-20"));
            ObservedAgreement unsent = fixture.agreement("Unsent", null);
            fixture.row(first, a, "0.03", day("2024-03-01"), null);
            fixture.row(second, a, "0.02", day("2024-03-01"), null);
            ObservedCoefficient firstInSent = fixture.row(first, sent, "0.04", (LocalDate) null, null);
            fixture.row(second, sent, "0.01", (LocalDate) null, null);
            ObservedCoefficient firstInUnsent = fixture.row(first, unsent, "0.05", (LocalDate) null, null);
            fixture.row(second, unsent, "0.01", (LocalDate) null, null);
            fixture.beta(first, FROM, TO, 0.03).beta(second, FROM, TO, 0.02);

            PlantSharingObservation result = fixture.calculate();

            assertThat(rowOf(result, firstInSent).status()).isEqualTo(ObservationStatus.PENDING_APPLICATION);
            assertThat(rowOf(result, firstInUnsent).status()).isEqualTo(ObservationStatus.NOT_OBSERVED);
        }

        @Test
        @DisplayName("OBS-002 an open date admits a stored date on or before it, and no later one")
        void openDateAdmitsAnEarlierStoredDate() {
            ObservationFixture fixture = new ObservationFixture(FROM, TO);
            ObservedSupply first = fixture.supply("S1");
            ObservedSupply second = fixture.supply("S2");
            ObservedAgreement a = fixture.agreement("A", day("2023-01-10"));
            ObservedAgreement b = fixture.agreement("B", day("2024-05-01"));
            ObservedCoefficient before = fixture.row(first, a, "0.03", day("2023-06-01"), null);
            ObservedCoefficient after = fixture.row(second, a, "0.02", day("2024-04-15"), null);
            fixture.row(first, b, "0.04", (LocalDate) null, null);
            fixture.row(second, b, "0.05", (LocalDate) null, null);
            fixture.beta(first, FROM, TO, 0.03).beta(second, FROM, TO, 0.02);

            PlantSharingObservation result = fixture.calculate();

            assertThat(rowOf(result, before).status()).isEqualTo(ObservationStatus.APPLIED_SAME_DATE);
            assertThat(rowOf(result, after).status()).isEqualTo(ObservationStatus.APPLIED_DIFFERENT_DATE);
        }

        @Test
        @DisplayName("OBS-002 a supply without any valid day has no data")
        void supplyWithoutValidDayHasNoData() {
            ObservationFixture fixture = new ObservationFixture(FROM, TO);
            ObservedSupply first = fixture.supply("S1");
            ObservedSupply silent = fixture.supply("S2");
            ObservedAgreement a = fixture.agreement("A", day("2024-03-01"));
            ObservedAgreement b = fixture.agreement("B", null);
            fixture.row(first, a, "0.03", day("2024-03-01"), null);
            ObservedCoefficient silentInA = fixture.row(silent, a, "0.02", day("2024-03-01"), null);
            fixture.row(first, b, "0.04", (LocalDate) null, null);
            ObservedCoefficient silentInB = fixture.row(silent, b, "0.02", (LocalDate) null, null);
            fixture.beta(first, FROM, TO, 0.03);

            PlantSharingObservation result = fixture.calculate();

            assertThat(rowOf(result, silentInA).status()).isEqualTo(ObservationStatus.NO_DATA);
            assertThat(rowOf(result, silentInB).status()).isEqualTo(ObservationStatus.NO_DATA);
            assertThat(result.analysis().suppliesWithoutData()).containsExactly(silent);
            assertThat(result.analysis().suppliesWithData()).containsExactly(first);
        }

        private ObservedCoefficient changed;

        private ObservationFixture twoAgreementsWithChangeOn(LocalDate change, LocalDate storedValidFrom) {
            ObservationFixture fixture = new ObservationFixture(FROM, TO);
            ObservedSupply first = fixture.supply("S1");
            ObservedSupply second = fixture.supply("S2");
            ObservedAgreement a = fixture.agreement("A", day("2024-03-01"));
            ObservedAgreement b = fixture.agreement("B", day("2024-05-01"));
            fixture.row(first, a, "0.03", day("2024-03-01"), null);
            fixture.row(second, a, "0.02", day("2024-03-01"), null);
            changed = fixture.row(first, b, "0.04", storedValidFrom, null);
            fixture.row(second, b, "0.02", storedValidFrom, null);
            fixture.beta(first, FROM, change.minusDays(1), 0.03).beta(first, change, TO, 0.04);
            fixture.beta(second, FROM, TO, 0.02);
            return fixture;
        }
    }

    @Nested
    class NotDistinguishable {

        @Test
        @DisplayName("OBS-003 a coefficient equal to the previous observed one is not distinguishable, proposed the agreement's most frequent date")
        void sameValueAsPreviousIsNotDistinguishable() {
            ObservationFixture fixture = new ObservationFixture(FROM, TO);
            ObservedSupply first = fixture.supply("S1");
            ObservedSupply second = fixture.supply("S2");
            ObservedSupply third = fixture.supply("S3");
            ObservedAgreement a = fixture.agreement("A", day("2024-03-01"));
            ObservedAgreement b = fixture.agreement("B", day("2024-05-01"));
            fixture.row(first, a, "0.03", day("2024-03-01"), null);
            fixture.row(second, a, "0.02", day("2024-03-01"), null);
            fixture.row(third, a, "0.01", day("2024-03-01"), null);
            ObservedCoefficient unchanged = fixture.row(first, b, "0.0305", (LocalDate) null, null);
            fixture.row(second, b, "0.05", (LocalDate) null, null);
            fixture.row(third, b, "0.04", (LocalDate) null, null);
            fixture.beta(first, FROM, TO, 0.03);
            fixture.beta(second, FROM, day("2024-05-09"), 0.02).beta(second, day("2024-05-10"), TO, 0.05);
            fixture.beta(third, FROM, day("2024-05-09"), 0.01).beta(third, day("2024-05-10"), TO, 0.04);

            PlantSharingObservation result = fixture.calculate();

            assertThat(rowOf(result, unchanged).status()).isEqualTo(ObservationStatus.NOT_DISTINGUISHABLE);
            assertThat(rowOf(result, unchanged).proposedDate()).isEqualTo(ObservedDate.exact(day("2024-05-10")));
            assertThat(agreementOf(result, b).mostFrequentObservedDate())
                    .isEqualTo(ObservedDate.exact(day("2024-05-10")));
        }

        @Test
        @DisplayName("OBS-003 undated agreements created out of order are ordered by their sent dates: tie, previous agreement and leaving row")
        void sentDatesOrderUndatedAgreements() {
            ObservationFixture fixture = new ObservationFixture(FROM, TO);
            ObservedSupply first = fixture.supply("S1");
            ObservedSupply leaving = fixture.supply("S2");
            // Created first, but sent after the other one.
            ObservedAgreement late = fixture.agreement("Late", day("2024-06-15"));
            ObservedAgreement early = fixture.agreement("Early", day("2024-02-01"));
            ObservedCoefficient firstInEarly = fixture.row(first, early, "0.03", (LocalDate) null, null);
            ObservedCoefficient firstInLate = fixture.row(first, late, "0.03", (LocalDate) null, null);
            ObservedCoefficient leavingInEarly = fixture.row(leaving, early, "0.05", (LocalDate) null, null);
            fixture.beta(first, FROM, TO, 0.03).beta(leaving, FROM, TO, 0.05);

            PlantSharingObservation result = fixture.calculate();

            // The tie between two unobserved agreements of the same value goes to the earlier sent one.
            assertThat(rowOf(result, firstInEarly).observedDate()).isEqualTo(ObservedDate.onOrBefore(FROM));
            // The later one has Early as previous agreement, with the same value.
            assertThat(rowOf(result, firstInLate).status()).isEqualTo(ObservationStatus.NOT_DISTINGUISHABLE);
            // S2 is absent from Late, which comes after Early: a leaving row, pending since Late was sent.
            assertThat(leavingsOf(result, late, leaving)).singleElement().satisfies(row -> {
                assertThat(row.previousAgreement().id()).isEqualTo(early.id());
                assertThat(row.previousCoefficientId()).isEqualTo(leavingInEarly.id());
                assertThat(row.status()).isEqualTo(ObservationStatus.PENDING_APPLICATION);
            });
        }
    }

    @Nested
    class Anomalies {

        @Test
        @DisplayName("OBS-004 leaving the distribution is matched against the implicit 0 and produces no anomaly")
        void leavingProducesNoAnomaly() {
            ObservationFixture fixture = new ObservationFixture(FROM, TO);
            ObservedSupply staying = fixture.supply("S1");
            ObservedSupply leaving = fixture.supply("S2");
            ObservedAgreement a = fixture.agreement("A", day("2024-03-01"));
            ObservedAgreement b = fixture.agreement("B", day("2024-05-01"));
            fixture.row(staying, a, "0.95", day("2024-03-01"), null);
            fixture.row(leaving, a, "0.05", day("2024-03-01"), day("2024-05-10"));
            fixture.row(staying, b, "1", day("2024-05-10"), null);
            fixture.beta(staying, FROM, day("2024-05-09"), 0.95).beta(staying, day("2024-05-10"), TO, 1.0);
            fixture.beta(leaving, FROM, day("2024-05-09"), 0.05).beta(leaving, day("2024-05-10"), TO, 0.0);

            PlantSharingObservation result = fixture.calculate();

            assertThat(anomaliesOf(result, leaving)).isEmpty();
            assertThat(anomaliesOf(result, staying)).isEmpty();
        }

        @Test
        @DisplayName("OBS-004 days without energy before a supply's first observation produce no anomaly")
        void noEnergyBeforeTheFirstObservationIsNoAnomaly() {
            ObservationFixture fixture = new ObservationFixture(FROM, TO);
            ObservedSupply joining = fixture.supply("S1");
            ObservedSupply other = fixture.supply("S2");
            ObservedAgreement a = fixture.agreement("A", day("2024-03-01"));
            ObservedAgreement b = fixture.agreement("B", day("2024-05-01"));
            ObservedCoefficient joiningInA = fixture.row(joining, a, "0.03", day("2024-03-01"), null);
            fixture.row(other, a, "0.02", day("2024-03-01"), null);
            fixture.row(joining, b, "0.04", (LocalDate) null, null);
            fixture.row(other, b, "0.02", (LocalDate) null, null);
            // S1 is in every agreement with a non-zero value, yet receives nothing until 15 May.
            fixture.beta(joining, FROM, day("2024-05-14"), 0.0).beta(joining, day("2024-05-15"), TO, 0.03);
            fixture.beta(other, FROM, TO, 0.02);

            PlantSharingObservation result = fixture.calculate();

            assertThat(anomaliesOf(result, joining)).isEmpty();
            assertThat(rowOf(result, joiningInA).observedDate()).isEqualTo(ObservedDate.exact(day("2024-05-15")));
        }

        @Test
        @DisplayName("OBS-004 an unknown coefficient is reported and attributed to the agreement applied before it")
        void unknownCoefficient() {
            ObservationFixture fixture = new ObservationFixture(FROM, TO);
            ObservedSupply first = fixture.supply("S1");
            ObservedSupply second = fixture.supply("S2");
            ObservedAgreement a = fixture.agreement("A", day("2024-03-01"));
            ObservedAgreement b = fixture.agreement("B", null);
            fixture.row(first, a, "0.03", day("2024-03-01"), null);
            fixture.row(second, a, "0.02", day("2024-03-01"), null);
            fixture.row(first, b, "0.04", (LocalDate) null, null);
            fixture.row(second, b, "0.02", (LocalDate) null, null);
            fixture.beta(first, FROM, TO, 0.03).beta(first, day("2024-05-10"), day("2024-05-14"), 0.05);
            fixture.beta(second, FROM, TO, 0.02);

            PlantSharingObservation result = fixture.calculate();

            assertThat(anomaliesOf(result, first)).singleElement().satisfies(anomaly -> {
                assertThat(anomaly.type()).isEqualTo(SharingAnomalyType.UNKNOWN_COEFFICIENT);
                assertThat(anomaly.start()).isEqualTo(ObservedDate.exact(day("2024-05-10")));
                assertThat(anomaly.end()).isEqualTo(day("2024-05-14"));
                assertThat(anomaly.attributedAgreementId()).isEqualTo(a.id());
            });
        }

        @Test
        @DisplayName("OBS-004 energy received by a supply in no agreement is reported, attributed to none")
        void energyOutsideAgreement() {
            ObservationFixture fixture = new ObservationFixture(FROM, TO);
            ObservedSupply member = fixture.supply("S1");
            ObservedSupply outsider = fixture.supply("S2");
            ObservedAgreement a = fixture.agreement("A", day("2024-03-01"));
            ObservedAgreement b = fixture.agreement("B", null);
            fixture.row(member, a, "1", day("2024-03-01"), null);
            fixture.row(member, b, "1", (LocalDate) null, null);
            fixture.beta(member, FROM, TO, 1.0).beta(outsider, day("2024-05-01"), day("2024-05-20"), 0.02);
            fixture.beta(outsider, FROM, day("2024-04-30"), 0.0).beta(outsider, day("2024-05-21"), TO, 0.0);

            PlantSharingObservation result = fixture.calculate();

            assertThat(result.unattributedAnomalies()).singleElement().satisfies(anomaly -> {
                assertThat(anomaly.type()).isEqualTo(SharingAnomalyType.ENERGY_OUTSIDE_AGREEMENT);
                assertThat(anomaly.supply()).isEqualTo(outsider);
                assertThat(anomaly.start()).isEqualTo(ObservedDate.exact(day("2024-05-01")));
                assertThat(anomaly.end()).isEqualTo(day("2024-05-20"));
            });
        }

        @Test
        @DisplayName("OBS-004 no energy for a supply every agreement gives some is reported after its first observation")
        void noEnergyInAgreement() {
            ObservationFixture fixture = new ObservationFixture(FROM, TO);
            ObservedSupply first = fixture.supply("S1");
            ObservedSupply second = fixture.supply("S2");
            ObservedAgreement a = fixture.agreement("A", day("2024-03-01"));
            ObservedAgreement b = fixture.agreement("B", null);
            fixture.row(first, a, "0.03", day("2024-03-01"), null);
            fixture.row(second, a, "0.02", day("2024-03-01"), null);
            fixture.row(first, b, "0.04", (LocalDate) null, null);
            fixture.row(second, b, "0.02", (LocalDate) null, null);
            fixture.beta(first, FROM, TO, 0.03).beta(first, day("2024-06-25"), TO, 0.0);
            fixture.beta(second, FROM, TO, 0.02);

            PlantSharingObservation result = fixture.calculate();

            assertThat(anomaliesOf(result, first)).singleElement().satisfies(anomaly -> {
                assertThat(anomaly.type()).isEqualTo(SharingAnomalyType.NO_ENERGY_IN_AGREEMENT);
                assertThat(anomaly.start()).isEqualTo(ObservedDate.exact(day("2024-06-25")));
                // Still going at the end of the data.
                assertThat(anomaly.end()).isNull();
                assertThat(anomaly.attributedAgreementId()).isEqualTo(a.id());
            });
        }

        @Test
        @DisplayName("OBS-004 re-applying a coefficient after a later-started one is reverted, attributed to the later one")
        void reverted() {
            ObservationFixture fixture = new ObservationFixture(FROM, TO);
            ObservedSupply first = fixture.supply("S1");
            ObservedSupply second = fixture.supply("S2");
            ObservedAgreement a = fixture.agreement("A", day("2024-03-01"));
            ObservedAgreement b = fixture.agreement("B", day("2024-04-25"));
            fixture.row(first, a, "0.03", day("2024-03-01"), day("2024-05-01"));
            fixture.row(second, a, "0.02", day("2024-03-01"), day("2024-05-01"));
            fixture.row(first, b, "0.04", day("2024-05-01"), null);
            fixture.row(second, b, "0.02", day("2024-05-01"), null);
            fixture.beta(first, FROM, day("2024-04-30"), 0.03)
                    .beta(first, day("2024-05-01"), day("2024-05-31"), 0.04)
                    .beta(first, day("2024-06-01"), day("2024-06-10"), 0.03)
                    .beta(first, day("2024-06-11"), TO, 0.04);
            fixture.beta(second, FROM, TO, 0.02);

            PlantSharingObservation result = fixture.calculate();

            assertThat(anomaliesOf(result, first)).singleElement().satisfies(anomaly -> {
                assertThat(anomaly.type()).isEqualTo(SharingAnomalyType.REVERTED);
                assertThat(anomaly.start()).isEqualTo(ObservedDate.exact(day("2024-06-01")));
                assertThat(anomaly.end()).isEqualTo(day("2024-06-10"));
                assertThat(anomaly.attributedAgreementId()).isEqualTo(b.id());
            });
        }

        @Test
        @DisplayName("OBS-004 an application before the sent date is reported, attributed to the late-sent agreement")
        void appliedBeforeSent() {
            ObservationFixture fixture = new ObservationFixture(FROM, TO);
            ObservedSupply first = fixture.supply("S1");
            ObservedSupply second = fixture.supply("S2");
            ObservedAgreement a = fixture.agreement("A", day("2024-03-01"));
            ObservedAgreement b = fixture.agreement("B", day("2024-05-15"));
            fixture.row(first, a, "0.03", day("2024-03-01"), day("2024-05-10"));
            fixture.row(second, a, "0.02", day("2024-03-01"), day("2024-05-10"));
            fixture.row(first, b, "0.04", day("2024-05-10"), null);
            fixture.row(second, b, "0.02", day("2024-05-10"), null);
            fixture.beta(first, FROM, day("2024-05-09"), 0.03).beta(first, day("2024-05-10"), TO, 0.04);
            fixture.beta(second, FROM, TO, 0.02);

            PlantSharingObservation result = fixture.calculate();

            assertThat(anomaliesOf(result, first)).singleElement().satisfies(anomaly -> {
                assertThat(anomaly.type()).isEqualTo(SharingAnomalyType.APPLIED_BEFORE_SENT);
                assertThat(anomaly.start()).isEqualTo(ObservedDate.exact(day("2024-05-10")));
                assertThat(anomaly.end()).isEqualTo(day("2024-05-14"));
                assertThat(anomaly.attributedAgreementId()).isEqualTo(b.id());
            });
            assertThat(agreementOf(result, b).anomalies()).hasSize(1);
            assertThat(agreementOf(result, a).anomalies()).isEmpty();
        }

        @Test
        @DisplayName("OBS-004 a change to an agreement not yet observed for the supply is an application, never an anomaly")
        void changeToANotYetObservedAgreementIsNoAnomaly() {
            ObservationFixture fixture = new ObservationFixture(FROM, TO);
            ObservedSupply first = fixture.supply("S1");
            ObservedSupply second = fixture.supply("S2");
            ObservedAgreement a = fixture.agreement("A", day("2024-03-01"));
            ObservedAgreement b = fixture.agreement("B", day("2024-05-01"));
            ObservedAgreement c = fixture.agreement("C", day("2024-06-01"));
            fixture.row(first, a, "0.03", day("2024-03-01"), null);
            fixture.row(second, a, "0.02", day("2024-03-01"), null);
            fixture.row(first, b, "0.04", (LocalDate) null, null);
            fixture.row(second, b, "0.02", (LocalDate) null, null);
            ObservedCoefficient firstInC = fixture.row(first, c, "0.05", (LocalDate) null, null);
            fixture.row(second, c, "0.02", (LocalDate) null, null);
            // B is skipped: the supply goes from A straight to C.
            fixture.beta(first, FROM, day("2024-06-09"), 0.03).beta(first, day("2024-06-10"), TO, 0.05);
            fixture.beta(second, FROM, TO, 0.02);

            PlantSharingObservation result = fixture.calculate();

            assertThat(anomaliesOf(result, first)).isEmpty();
            assertThat(rowOf(result, firstInC).observedDate()).isEqualTo(ObservedDate.exact(day("2024-06-10")));
        }

        @Test
        @DisplayName("OBS-004 ten consecutive anomalous days are one anomaly, two runs separated by matching days are two, with stable ids")
        void anomalousDaysAreMerged() {
            ObservationFixture fixture = new ObservationFixture(FROM, TO);
            ObservedSupply first = fixture.supply("S1");
            ObservedSupply second = fixture.supply("S2");
            ObservedAgreement a = fixture.agreement("A", day("2024-03-01"));
            ObservedAgreement b = fixture.agreement("B", null);
            fixture.row(first, a, "0.03", day("2024-03-01"), null);
            fixture.row(second, a, "0.02", day("2024-03-01"), null);
            fixture.row(first, b, "0.04", (LocalDate) null, null);
            fixture.row(second, b, "0.02", (LocalDate) null, null);
            fixture.beta(first, FROM, TO, 0.03).beta(first, day("2024-05-01"), day("2024-05-10"), 0.06);
            fixture.beta(second, FROM, TO, 0.02)
                    .beta(second, day("2024-05-01"), day("2024-05-04"), 0.07)
                    .beta(second, day("2024-05-07"), day("2024-05-10"), 0.07);

            PlantSharingObservation result = fixture.calculate();

            assertThat(anomaliesOf(result, first)).singleElement().satisfies(anomaly -> {
                assertThat(anomaly.start().to()).isEqualTo(day("2024-05-01"));
                assertThat(anomaly.end()).isEqualTo(day("2024-05-10"));
            });
            assertThat(anomaliesOf(result, second)).extracting(anomaly -> anomaly.start().to())
                    .containsExactly(day("2024-05-01"), day("2024-05-07"));
            List<SharingAnomaly> again = anomaliesOf(fixture.calculate(), second);
            assertThat(again).extracting(SharingAnomaly::id)
                    .containsExactlyElementsOf(anomaliesOf(result, second).stream().map(SharingAnomaly::id).toList());
        }
    }

    @Nested
    class Recommendations {

        @Test
        @DisplayName("OBS-005 recommendations: set dates, deactivate the synthetic agreement, review anomalies, or none")
        void recommendations() {
            ObservationFixture fixture = new ObservationFixture(FROM, TO);
            ObservedSupply first = fixture.supply("S1");
            ObservedSupply second = fixture.supply("S2");
            // Never sent and holding several rows per supply, all before the data.
            ObservedAgreement synthetic = fixture.agreement("Synthetic", null);
            ObservedAgreement a = fixture.agreement("A", day("2024-03-01"));
            ObservedAgreement b = fixture.agreement("B", day("2024-05-01"));
            ObservedAgreement c = fixture.agreement("C", day("2024-06-01"));
            fixture.row(first, synthetic, "0.01", day("2022-01-01"), day("2022-06-01"));
            fixture.row(first, synthetic, "0.015", day("2022-06-01"), day("2023-01-01"));
            fixture.row(second, synthetic, "0.01", day("2022-01-01"), day("2023-01-01"));
            fixture.row(first, a, "0.03", day("2023-01-01"), day("2024-05-10"));
            fixture.row(second, a, "0.02", day("2023-01-01"), day("2024-05-10"));
            // B's change is observed on 10 May but stored on 1 May; S2's unchanged values in B and C are not
            // distinguishable from A's.
            fixture.row(first, b, "0.04", day("2024-05-01"), day("2024-06-10"));
            fixture.row(second, b, "0.02", day("2024-05-01"), day("2024-06-10"));
            fixture.row(first, c, "0.05", day("2024-06-10"), null);
            fixture.row(second, c, "0.02", day("2024-06-10"), null);
            fixture.beta(first, FROM, day("2024-05-09"), 0.03)
                    .beta(first, day("2024-05-10"), day("2024-06-09"), 0.04)
                    .beta(first, day("2024-06-10"), TO, 0.05);
            fixture.beta(second, FROM, TO, 0.02)
                    .beta(second, day("2024-04-10"), day("2024-04-15"), 0.08);

            PlantSharingObservation result = fixture.calculate();

            assertThat(agreementOf(result, synthetic).recommendations())
                    .containsExactly(SharingRecommendation.DEACTIVATE);
            // S2's anomaly in April is attributed to A, applied before it.
            assertThat(agreementOf(result, a).recommendations())
                    .containsExactly(SharingRecommendation.REVIEW_ANOMALIES);
            assertThat(agreementOf(result, b).recommendations())
                    .containsExactly(SharingRecommendation.SET_DATES);
            assertThat(agreementOf(result, c).recommendations())
                    .containsExactly(SharingRecommendation.SET_DATES);
        }

        @Test
        @DisplayName("OBS-005 an agreement whose rows are all observed on their stored dates gets none; several apply in order")
        void noneAndSeveralInOrder() {
            ObservationFixture fixture = new ObservationFixture(FROM, TO);
            ObservedSupply first = fixture.supply("S1");
            ObservedSupply second = fixture.supply("S2");
            ObservedAgreement a = fixture.agreement("A", day("2024-03-01"));
            ObservedAgreement b = fixture.agreement("B", day("2024-05-01"));
            ObservedAgreement c = fixture.agreement("C", day("2024-06-15"));
            fixture.row(first, a, "0.03", day("2024-03-01"), day("2024-05-10"));
            fixture.row(second, a, "0.02", day("2024-03-01"), day("2024-05-10"));
            fixture.row(first, b, "0.04", day("2024-05-10"), day("2024-06-10"));
            fixture.row(second, b, "0.05", day("2024-05-10"), day("2024-06-10"));
            // C is applied on 10 June, before its file was sent, and stored nowhere.
            fixture.row(first, c, "0.06", (LocalDate) null, null);
            fixture.row(second, c, "0.05", (LocalDate) null, null);
            fixture.beta(first, FROM, day("2024-05-09"), 0.03)
                    .beta(first, day("2024-05-10"), day("2024-06-09"), 0.04)
                    .beta(first, day("2024-06-10"), TO, 0.06);
            fixture.beta(second, FROM, day("2024-05-09"), 0.02).beta(second, day("2024-05-10"), TO, 0.05);

            PlantSharingObservation result = fixture.calculate();

            assertThat(agreementOf(result, b).recommendations()).containsExactly(SharingRecommendation.NONE);
            assertThat(agreementOf(result, c).recommendations())
                    .containsExactly(SharingRecommendation.SET_DATES, SharingRecommendation.REVIEW_ANOMALIES);
        }
    }

    @Nested
    class Scope {

        @Test
        @DisplayName("OBS-006 draft agreements are ignored")
        void draftAgreementsAreIgnored() {
            ObservationFixture fixture = new ObservationFixture(FROM, TO);
            ObservedSupply first = fixture.supply("S1");
            ObservedSupply second = fixture.supply("S2");
            ObservedAgreement a = fixture.agreement("A", day("2024-03-01"));
            ObservedAgreement draft = fixture.agreement("Draft", SharingAgreementStatus.DRAFT, null);
            ObservedAgreement superseded = fixture.agreement("Old", SharingAgreementStatus.SUPERSEDED,
                    day("2023-03-01"));
            fixture.row(first, a, "0.03", day("2024-03-01"), null);
            fixture.row(second, a, "0.02", day("2024-03-01"), null);
            fixture.row(first, draft, "0.05", (LocalDate) null, null);
            fixture.row(second, draft, "0.02", (LocalDate) null, null);
            fixture.row(first, superseded, "0.01", day("2023-03-01"), day("2024-03-01"));
            fixture.row(second, superseded, "0.01", day("2023-03-01"), day("2024-03-01"));
            fixture.beta(first, FROM, TO, 0.03).beta(first, day("2024-05-10"), day("2024-05-14"), 0.05);
            fixture.beta(second, FROM, TO, 0.02);

            PlantSharingObservation result = fixture.calculate();

            assertThat(result.agreements()).extracting(observation -> observation.agreement().name())
                    .containsExactly("Old", "A");
            // Matching only the draft's value is matching nothing.
            assertThat(anomaliesOf(result, first)).extracting(SharingAnomaly::type)
                    .containsExactly(SharingAnomalyType.UNKNOWN_COEFFICIENT);
        }

        @Test
        @DisplayName("OBS-006 the plant supply is never analysed as a consumer")
        void plantSupplyIsNeverAConsumer() {
            ObservationFixture fixture = new ObservationFixture(FROM, TO);
            ObservedSupply first = fixture.supply("S1");
            ObservedSupply second = fixture.supply("S2");
            ObservedSupply plant = fixture.plantSupply();
            ObservedAgreement a = fixture.agreement("A", day("2024-03-01"));
            fixture.agreement("B", null);
            fixture.row(first, a, "0.5", day("2024-03-01"), null);
            fixture.row(second, a, "0.5", day("2024-03-01"), null);
            fixture.beta(first, FROM, TO, 0.5).beta(second, FROM, TO, 0.5).beta(plant, FROM, TO, 0.3);

            PlantSharingObservation result = fixture.calculate();

            assertThat(result.analysis().suppliesWithData()).containsExactly(first, second);
            assertThat(result.analysis().suppliesWithoutData()).isEmpty();
            assertThat(anomaliesOf(result, plant)).isEmpty();
        }

        @Test
        @DisplayName("OBS-006 without data from the plant supply the plant is not computable")
        void plantSupplyWithoutDataIsNotComputable() {
            ObservationFixture fixture = new ObservationFixture(FROM, TO).plantWithoutData();
            ObservedSupply first = fixture.supply("S1");
            ObservedSupply second = fixture.supply("S2");
            ObservedAgreement a = fixture.agreement("A", day("2024-03-01"));
            ObservedAgreement b = fixture.agreement("B", null);
            fixture.row(first, a, "0.5", day("2024-03-01"), null);
            fixture.row(second, b, "0.5", (LocalDate) null, null);
            fixture.beta(first, FROM, TO, 0.5).beta(second, FROM, TO, 0.5);

            PlantSharingObservation result = fixture.calculate();

            assertThat(result.analysis().computable()).isFalse();
            assertThat(result.analysis().notComputableReason())
                    .isEqualTo(NotComputableReason.PLANT_SUPPLY_WITHOUT_DATA);
            assertThat(result.agreements()).hasSize(2)
                    .allSatisfy(observation -> {
                        assertThat(observation.coefficients()).isEmpty();
                        assertThat(observation.recommendations()).isEmpty();
                    });
        }

        @Test
        @DisplayName("OBS-006 a supply with coefficients in two plants is not computable and kept out of Σβ")
        void supplyInTwoPlantsIsNotComputable() {
            ObservationFixture fixture = new ObservationFixture(FROM, TO);
            ObservedSupply first = fixture.supply("S1");
            ObservedSupply second = fixture.supply("S2");
            ObservedSupply shared = fixture.notComputableSupply("S3");
            ObservedAgreement a = fixture.agreement("A", day("2024-03-01"));
            fixture.agreement("B", null);
            ObservedCoefficient firstInA = fixture.row(first, a, "0.5", day("2024-03-01"), null);
            fixture.row(second, a, "0.3", day("2024-03-01"), null);
            fixture.row(shared, a, "0.2", day("2024-03-01"), null);
            fixture.beta(first, FROM, TO, 0.5).beta(second, FROM, TO, 0.3);

            PlantSharingObservation result = fixture.calculate();

            assertThat(result.analysis().notComputableSupplies()).containsExactly(shared);
            assertThat(agreementOf(result, a).coefficients()).extracting(CoefficientObservation::supply)
                    .containsExactly(first, second);
            UnallocatedShare share = agreementOf(result, a).unallocatedShare();
            assertThat(share.unallocated()).isCloseTo(0.2, org.assertj.core.data.Offset.offset(1e-9));
            assertThat(share.registeredShareWithoutData()).isCloseTo(0.2, org.assertj.core.data.Offset.offset(1e-9));
            assertThat(rowOf(result, firstInA).observedDate()).isEqualTo(ObservedDate.onOrBefore(FROM));
        }

        @Test
        @DisplayName("OBS-007 an agreement's slice is its part of the plant result, and nothing else")
        void sliceIsThePlantsPart() {
            ObservationFixture fixture = new ObservationFixture(FROM, TO);
            ObservedSupply first = fixture.supply("S1");
            ObservedSupply second = fixture.supply("S2");
            ObservedAgreement a = fixture.agreement("A", day("2024-03-01"));
            ObservedAgreement b = fixture.agreement("B", day("2024-05-01"));
            fixture.row(first, a, "0.5", day("2024-03-01"), null);
            fixture.row(second, a, "0.5", day("2024-03-01"), null);
            fixture.row(first, b, "0.6", (LocalDate) null, null);
            fixture.row(second, b, "0.4", (LocalDate) null, null);
            fixture.beta(first, FROM, TO, 0.5).beta(second, FROM, TO, 0.5);

            PlantSharingObservation result = fixture.calculate();

            assertThat(result.sliceFor(b.id())).hasValueSatisfying(slice -> {
                assertThat(slice.agreement()).isEqualTo(agreementOf(result, b));
                assertThat(slice.analysis()).isEqualTo(result.analysis());
                assertThat(slice.consecutiveDays()).isEqualTo(result.consecutiveDays());
            });
            assertThat(result.sliceFor(java.util.UUID.randomUUID())).isEmpty();
        }

        @Test
        @DisplayName("OBS-001 a run must last at least one valid day")
        void runLastsAtLeastOneDay() {
            assertThatThrownBy(() -> new ObservedSharingCalculator(0)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class Leavings {

        @Test
        @DisplayName("OBS-008 a supply leaving in B after A was observed is a leaving row of B, applied on the same date when A's row ends there")
        void leavingOnTheStoredEnd() {
            assertThat(leavingStatusWithStoredEnd(day("2024-05-10")).status())
                    .isEqualTo(ObservationStatus.APPLIED_SAME_DATE);
        }

        @Test
        @DisplayName("OBS-008 a leaving row whose previous row is open or ends another day is applied on a different date")
        void leavingWithAnotherEnd() {
            assertThat(leavingStatusWithStoredEnd(null).status()).isEqualTo(ObservationStatus.APPLIED_DIFFERENT_DATE);
            assertThat(leavingStatusWithStoredEnd(day("2024-05-20")).status())
                    .isEqualTo(ObservationStatus.APPLIED_DIFFERENT_DATE);
        }

        @Test
        @DisplayName("OBS-008 a supply never observed with the previous agreement has no leaving row")
        void neverObservedHasNoLeavingRow() {
            ObservationFixture fixture = new ObservationFixture(FROM, TO);
            ObservedSupply staying = fixture.supply("S1");
            ObservedSupply leaving = fixture.supply("S2");
            ObservedAgreement a = fixture.agreement("A", day("2024-03-01"));
            ObservedAgreement b = fixture.agreement("B", day("2024-05-01"));
            fixture.row(staying, a, "0.95", day("2024-03-01"), day("2024-05-10"));
            fixture.row(leaving, a, "0.05", day("2024-03-01"), day("2024-05-10"));
            fixture.row(staying, b, "1", day("2024-05-10"), null);
            fixture.beta(staying, FROM, day("2024-05-09"), 0.95).beta(staying, day("2024-05-10"), TO, 1.0);
            // S2's data only starts after it left.
            fixture.beta(leaving, day("2024-05-20"), TO, 0.0);

            PlantSharingObservation result = fixture.calculate();

            assertThat(agreementOf(result, b).leavingRows()).isEmpty();
        }

        @Test
        @DisplayName("OBS-008 a leaving before the agreement's sent date is applied before sent, attributed to the leaving agreement")
        void leavingBeforeSent() {
            ObservationFixture fixture = new ObservationFixture(FROM, TO);
            ObservedSupply staying = fixture.supply("S1");
            ObservedSupply leaving = fixture.supply("S2");
            ObservedAgreement a = fixture.agreement("A", day("2024-03-01"));
            ObservedAgreement b = fixture.agreement("B", day("2024-05-20"));
            fixture.row(staying, a, "0.95", day("2024-03-01"), day("2024-05-10"));
            fixture.row(leaving, a, "0.05", day("2024-03-01"), day("2024-05-10"));
            fixture.row(staying, b, "1", day("2024-05-10"), null);
            fixture.beta(staying, FROM, day("2024-05-09"), 0.95).beta(staying, day("2024-05-10"), TO, 1.0);
            fixture.beta(leaving, FROM, day("2024-05-09"), 0.05).beta(leaving, day("2024-05-10"), TO, 0.0);

            PlantSharingObservation result = fixture.calculate();

            assertThat(anomaliesOf(result, leaving)).singleElement().satisfies(anomaly -> {
                assertThat(anomaly.type()).isEqualTo(SharingAnomalyType.APPLIED_BEFORE_SENT);
                assertThat(anomaly.start()).isEqualTo(ObservedDate.exact(day("2024-05-10")));
                assertThat(anomaly.end()).isEqualTo(day("2024-05-19"));
                assertThat(anomaly.attributedAgreementId()).isEqualTo(b.id());
            });
        }

        private LeavingObservation leavingStatusWithStoredEnd(LocalDate storedEnd) {
            ObservationFixture fixture = new ObservationFixture(FROM, TO);
            ObservedSupply staying = fixture.supply("S1");
            ObservedSupply leaving = fixture.supply("S2");
            ObservedAgreement a = fixture.agreement("A", day("2024-03-01"));
            ObservedAgreement b = fixture.agreement("B", day("2024-05-01"));
            fixture.row(staying, a, "0.95", day("2024-03-01"), day("2024-05-10"));
            ObservedCoefficient leavingInA = fixture.row(leaving, a, "0.05", day("2024-03-01"), storedEnd);
            fixture.row(staying, b, "1", day("2024-05-10"), null);
            fixture.beta(staying, FROM, day("2024-05-09"), 0.95).beta(staying, day("2024-05-10"), TO, 1.0);
            fixture.beta(leaving, FROM, day("2024-05-09"), 0.05).beta(leaving, day("2024-05-10"), TO, 0.0);

            PlantSharingObservation result = fixture.calculate();

            List<LeavingObservation> rows = leavingsOf(result, b, leaving);
            assertThat(rows).singleElement().satisfies(row -> {
                assertThat(row.previousAgreement().id()).isEqualTo(a.id());
                assertThat(row.previousCoefficientId()).isEqualTo(leavingInA.id());
                assertThat(row.observedLeavingDate()).isEqualTo(ObservedDate.exact(day("2024-05-10")));
                assertThat(row.storedValidTo()).isEqualTo(storedEnd);
            });
            assertThat(anomaliesOf(result, leaving)).isEmpty();
            return rows.get(0);
        }
    }

    @Nested
    class ObservedBetas {

        private static final LocalDate CHANGE = day("2024-05-10");

        @Test
        @DisplayName("OBS-009 a coefficient row reports the median of its supply's daily β over the agreement's window, an odd count")
        void rowOverAnOddCount() {
            Rows rows = twoAgreements();

            // A's window runs from its open date (on or before FROM) to the day before B's: 19 days at 2.98 % and
            // 20 at 3.02 %, so the 20th of 39 is the first at 3.02 %.
            assertThat(rowOf(rows.result(), rows.firstInA()).observedBeta())
                    .isEqualTo(new ObservedBeta(new BigDecimal("0.030200"), 39));
            assertThat(rowOf(rows.result(), rows.secondInA()).observedBeta())
                    .isEqualTo(new ObservedBeta(new BigDecimal("0.020000"), 39));
        }

        @Test
        @DisplayName("OBS-009 a coefficient row reports the median of its supply's daily β over the agreement's window, an even count")
        void rowOverAnEvenCount() {
            Rows rows = twoAgreements();

            // B's window runs from its observed date to the end of the range: 26 days at 3.99 % and 26 at 4.01 %.
            assertThat(rowOf(rows.result(), rows.firstInB()).observedBeta())
                    .isEqualTo(new ObservedBeta(new BigDecimal("0.040000"), 52));
            assertThat(rowOf(rows.result(), rows.secondInB()).observedBeta())
                    .isEqualTo(new ObservedBeta(new BigDecimal("0.050000"), 52));
        }

        @Test
        @DisplayName("OBS-009 a row with an observed date rests on the supply's days from that date to the end of the agreement's window")
        void observedRowStartsOnItsObservedDate() {
            ObservationFixture fixture = new ObservationFixture(FROM, TO);
            ObservedSupply first = fixture.supply("S1");
            ObservedSupply second = fixture.supply("S2");
            ObservedSupply third = fixture.supply("S3");
            ObservedAgreement a = fixture.agreement("A", day("2024-03-01"));
            ObservedAgreement b = fixture.agreement("B", day("2024-05-01"));
            fixture.row(first, a, "0.03", day("2024-03-01"), CHANGE);
            fixture.row(second, a, "0.02", day("2024-03-01"), CHANGE);
            fixture.row(third, a, "0.01", day("2024-03-01"), CHANGE);
            fixture.row(first, b, "0.04", CHANGE, null);
            fixture.row(second, b, "0.05", CHANGE, null);
            ObservedCoefficient thirdInB = fixture.row(third, b, "0.06", CHANGE, null);
            fixture.beta(first, FROM, day("2024-05-09"), 0.03).beta(first, CHANGE, TO, 0.04);
            fixture.beta(second, FROM, day("2024-05-09"), 0.02).beta(second, CHANGE, TO, 0.05);
            // S3 moves to B ten days after the others: its row leaves out the 10 days still at A's value.
            fixture.beta(third, FROM, day("2024-05-19"), 0.01).beta(third, day("2024-05-20"), TO, 0.06);

            PlantSharingObservation result = fixture.calculate();

            assertThat(rowOf(result, thirdInB).observedDate()).isEqualTo(ObservedDate.exact(day("2024-05-20")));
            assertThat(rowOf(result, thirdInB).observedBeta())
                    .isEqualTo(new ObservedBeta(new BigDecimal("0.060000"), 42));
        }

        @Test
        @DisplayName("OBS-009 several rows of one supply in an agreement each start on their own observed date; rows never observed share the agreement's window")
        void severalRowsOfOneSupply() {
            ObservationFixture fixture = new ObservationFixture(FROM, TO);
            ObservedSupply first = fixture.supply("S1");
            ObservedSupply second = fixture.supply("S2");
            ObservedAgreement a = fixture.agreement("A", day("2024-03-01"));
            ObservedAgreement b = fixture.agreement("B", day("2024-05-01"));
            fixture.row(first, a, "0.03", day("2024-03-01"), CHANGE);
            fixture.row(second, a, "0.02", day("2024-03-01"), CHANGE);
            ObservedCoefficient firstInBBefore = fixture.row(first, b, "0.04", CHANGE, day("2024-06-01"));
            ObservedCoefficient firstInBAfter = fixture.row(first, b, "0.05", day("2024-06-01"), null);
            ObservedCoefficient firstInBNever = fixture.row(first, b, "0.07", day("2024-06-01"), null);
            fixture.row(second, b, "0.05", CHANGE, null);
            fixture.beta(first, FROM, day("2024-05-09"), 0.03)
                    .beta(first, CHANGE, day("2024-05-31"), 0.04)
                    .beta(first, day("2024-06-01"), TO, 0.05);
            fixture.beta(second, FROM, day("2024-05-09"), 0.02).beta(second, CHANGE, TO, 0.05);

            PlantSharingObservation result = fixture.calculate();

            // B's window holds 22 days at 4 % and then 30 at 5 %.
            assertThat(rowOf(result, firstInBBefore).observedBeta())
                    .isEqualTo(new ObservedBeta(new BigDecimal("0.050000"), 52));
            assertThat(rowOf(result, firstInBAfter).observedBeta())
                    .isEqualTo(new ObservedBeta(new BigDecimal("0.050000"), 30));
            assertThat(rowOf(result, firstInBNever).observedDate()).isNull();
            assertThat(rowOf(result, firstInBNever).observedBeta())
                    .isEqualTo(new ObservedBeta(new BigDecimal("0.050000"), 52));
        }

        @Test
        @DisplayName("OBS-009 a leaving row reports the median over the leaving agreement's window: 0 after the leaving")
        void leavingRowOverTheLeavingAgreementsWindow() {
            ObservationFixture fixture = new ObservationFixture(FROM, TO);
            ObservedSupply staying = fixture.supply("S1");
            ObservedSupply leaving = fixture.supply("S2");
            ObservedAgreement a = fixture.agreement("A", day("2024-03-01"));
            ObservedAgreement b = fixture.agreement("B", day("2024-05-01"));
            fixture.row(staying, a, "0.95", day("2024-03-01"), CHANGE);
            fixture.row(leaving, a, "0.05", day("2024-03-01"), CHANGE);
            fixture.row(staying, b, "1", CHANGE, null);
            fixture.beta(staying, FROM, day("2024-05-09"), 0.95).beta(staying, CHANGE, TO, 1.0);
            // A trickle on the first two days after the leaving does not move the median.
            fixture.beta(leaving, FROM, day("2024-05-09"), 0.05).beta(leaving, CHANGE, TO, 0.0)
                    .beta(leaving, CHANGE, day("2024-05-11"), 0.0005);

            PlantSharingObservation result = fixture.calculate();

            assertThat(leavingsOf(result, b, leaving)).singleElement().satisfies(row ->
                    assertThat(row.observedBeta()).isEqualTo(new ObservedBeta(new BigDecimal("0.000000"), 52)));
        }

        @Test
        @DisplayName("OBS-009 an anomaly reports the median over its own valid days: a 5-day unknown coefficient at 4.12 % reports 4.12 %")
        void streakOverItsValidDays() {
            ObservationFixture fixture = new ObservationFixture(FROM, TO);
            ObservedSupply first = fixture.supply("S1");
            ObservedSupply second = fixture.supply("S2");
            ObservedAgreement a = fixture.agreement("A", day("2024-03-01"));
            ObservedAgreement b = fixture.agreement("B", null);
            fixture.row(first, a, "0.03", day("2024-03-01"), null);
            fixture.row(second, a, "0.02", day("2024-03-01"), null);
            fixture.row(first, b, "0.04", (LocalDate) null, null);
            fixture.row(second, b, "0.02", (LocalDate) null, null);
            fixture.beta(first, FROM, TO, 0.03).beta(first, day("2024-05-10"), day("2024-05-15"), 0.0412);
            fixture.beta(second, FROM, TO, 0.02);
            // Not a valid day: inside the anomaly's dates, but not one of its days.
            fixture.production(day("2024-05-12"), 1.0);

            PlantSharingObservation result = fixture.calculate();

            assertThat(anomaliesOf(result, first)).singleElement().satisfies(anomaly -> {
                assertThat(anomaly.type()).isEqualTo(SharingAnomalyType.UNKNOWN_COEFFICIENT);
                assertThat(anomaly.observedBeta()).isEqualTo(new ObservedBeta(new BigDecimal("0.041200"), 5));
            });
        }

        @Test
        @DisplayName("OBS-009 an anomaly open until the end of the data reports the median up to the supply's last valid day")
        void openStreakUpToTheLastValidDay() {
            ObservationFixture fixture = new ObservationFixture(FROM, TO);
            ObservedSupply first = fixture.supply("S1");
            ObservedSupply second = fixture.supply("S2");
            ObservedAgreement a = fixture.agreement("A", day("2024-03-01"));
            ObservedAgreement b = fixture.agreement("B", null);
            fixture.row(first, a, "0.03", day("2024-03-01"), null);
            fixture.row(second, a, "0.02", day("2024-03-01"), null);
            fixture.row(first, b, "0.04", (LocalDate) null, null);
            fixture.row(second, b, "0.02", (LocalDate) null, null);
            fixture.beta(first, FROM, TO, 0.03).beta(first, day("2024-06-25"), TO, 0.0);
            fixture.beta(second, FROM, TO, 0.02);

            PlantSharingObservation result = fixture.calculate();

            assertThat(anomaliesOf(result, first)).singleElement().satisfies(anomaly -> {
                assertThat(anomaly.type()).isEqualTo(SharingAnomalyType.NO_ENERGY_IN_AGREEMENT);
                assertThat(anomaly.end()).isNull();
                assertThat(anomaly.observedBeta()).isEqualTo(new ObservedBeta(new BigDecimal("0.000000"), 6));
            });
        }

        @Test
        @DisplayName("OBS-009 a reverted application reports the median over the valid days it lasted")
        void revertedOverItsExtent() {
            ObservationFixture fixture = new ObservationFixture(FROM, TO);
            ObservedSupply first = fixture.supply("S1");
            ObservedSupply second = fixture.supply("S2");
            ObservedAgreement a = fixture.agreement("A", day("2024-03-01"));
            ObservedAgreement b = fixture.agreement("B", day("2024-04-25"));
            fixture.row(first, a, "0.03", day("2024-03-01"), day("2024-05-01"));
            fixture.row(second, a, "0.02", day("2024-03-01"), day("2024-05-01"));
            fixture.row(first, b, "0.04", day("2024-05-01"), null);
            fixture.row(second, b, "0.02", day("2024-05-01"), null);
            fixture.beta(first, FROM, day("2024-04-30"), 0.03)
                    .beta(first, day("2024-05-01"), day("2024-05-31"), 0.04)
                    .beta(first, day("2024-06-01"), day("2024-06-10"), 0.0302)
                    .beta(first, day("2024-06-11"), TO, 0.04);
            fixture.beta(second, FROM, TO, 0.02);

            PlantSharingObservation result = fixture.calculate();

            assertThat(anomaliesOf(result, first)).singleElement().satisfies(anomaly -> {
                assertThat(anomaly.type()).isEqualTo(SharingAnomalyType.REVERTED);
                assertThat(anomaly.observedBeta()).isEqualTo(new ObservedBeta(new BigDecimal("0.030200"), 10));
            });
        }

        @Test
        @DisplayName("OBS-009 an application before the sent date reports the median over the valid days before the sent date")
        void appliedBeforeSentOverItsExtent() {
            ObservationFixture fixture = new ObservationFixture(FROM, TO);
            ObservedSupply first = fixture.supply("S1");
            ObservedSupply second = fixture.supply("S2");
            ObservedAgreement a = fixture.agreement("A", day("2024-03-01"));
            ObservedAgreement b = fixture.agreement("B", day("2024-05-15"));
            fixture.row(first, a, "0.03", day("2024-03-01"), CHANGE);
            fixture.row(second, a, "0.02", day("2024-03-01"), CHANGE);
            fixture.row(first, b, "0.04", CHANGE, null);
            fixture.row(second, b, "0.02", CHANGE, null);
            fixture.beta(first, FROM, day("2024-05-09"), 0.03)
                    .beta(first, CHANGE, day("2024-05-14"), 0.0402)
                    .beta(first, day("2024-05-15"), TO, 0.04);
            fixture.beta(second, FROM, TO, 0.02);

            PlantSharingObservation result = fixture.calculate();

            assertThat(anomaliesOf(result, first)).singleElement().satisfies(anomaly -> {
                assertThat(anomaly.type()).isEqualTo(SharingAnomalyType.APPLIED_BEFORE_SENT);
                assertThat(anomaly.observedBeta()).isEqualTo(new ObservedBeta(new BigDecimal("0.040200"), 5));
            });
        }

        @Test
        @DisplayName("OBS-009 the rows of an agreement never observed applied report null over 0 days, like its 1 − Σβ")
        void agreementWithoutWindowIsNull() {
            ObservationFixture fixture = new ObservationFixture(FROM, TO);
            ObservedSupply first = fixture.supply("S1");
            ObservedSupply second = fixture.supply("S2");
            ObservedAgreement a = fixture.agreement("A", day("2024-03-01"));
            fixture.agreement("B", day("2024-05-01"));
            ObservedAgreement never = fixture.agreement("C", null);
            fixture.row(first, a, "0.03", day("2024-03-01"), null);
            fixture.row(second, a, "0.02", day("2024-03-01"), null);
            fixture.row(first, never, "0.07", (LocalDate) null, null);
            fixture.row(second, never, "0.02", (LocalDate) null, null);
            fixture.beta(first, FROM, TO, 0.03).beta(second, FROM, TO, 0.02);

            PlantSharingObservation result = fixture.calculate();

            assertThat(agreementOf(result, never).unallocatedShare()).isNull();
            assertThat(agreementOf(result, never).coefficients()).hasSize(2)
                    .allSatisfy(row -> assertThat(row.observedBeta()).isEqualTo(new ObservedBeta(null, 0)));
        }

        @Test
        @DisplayName("OBS-009 a supply without a valid day in the agreement's window reports null over 0 days")
        void supplyWithoutValidDayInTheWindowIsNull() {
            ObservationFixture fixture = new ObservationFixture(FROM, TO);
            ObservedSupply first = fixture.supply("S1");
            ObservedSupply gone = fixture.supply("S2");
            ObservedSupply silent = fixture.supply("S3");
            ObservedAgreement a = fixture.agreement("A", day("2024-03-01"));
            ObservedAgreement b = fixture.agreement("B", day("2024-05-01"));
            fixture.row(first, a, "0.03", day("2024-03-01"), CHANGE);
            ObservedCoefficient goneInA = fixture.row(gone, a, "0.01", day("2024-03-01"), CHANGE);
            ObservedCoefficient silentInA = fixture.row(silent, a, "0.01", day("2024-03-01"), CHANGE);
            fixture.row(first, b, "0.04", CHANGE, null);
            ObservedCoefficient goneInB = fixture.row(gone, b, "0.01", CHANGE, null);
            fixture.beta(first, FROM, day("2024-05-09"), 0.03).beta(first, CHANGE, TO, 0.04);
            // S2's data stops before B's window; S3 has none.
            fixture.beta(gone, FROM, day("2024-05-09"), 0.01);

            PlantSharingObservation result = fixture.calculate();

            assertThat(rowOf(result, goneInA).observedBeta())
                    .isEqualTo(new ObservedBeta(new BigDecimal("0.010000"), 39));
            assertThat(rowOf(result, goneInB).observedBeta()).isEqualTo(new ObservedBeta(null, 0));
            assertThat(rowOf(result, silentInA).status()).isEqualTo(ObservationStatus.NO_DATA);
            assertThat(rowOf(result, silentInA).observedBeta()).isEqualTo(new ObservedBeta(null, 0));
        }

        @Test
        @DisplayName("OBS-009 a plant that is not computable has no row and no anomaly to report an observed β on")
        void notComputablePlantHasNothingToReportOn() {
            ObservationFixture fixture = new ObservationFixture(FROM, TO).plantWithoutData();
            ObservedSupply first = fixture.supply("S1");
            ObservedSupply second = fixture.supply("S2");
            ObservedAgreement a = fixture.agreement("A", day("2024-03-01"));
            ObservedAgreement b = fixture.agreement("B", day("2024-05-01"));
            fixture.row(first, a, "0.03", day("2024-03-01"), CHANGE);
            fixture.row(second, a, "0.02", day("2024-03-01"), CHANGE);
            fixture.row(first, b, "0.04", CHANGE, null);
            fixture.beta(first, FROM, TO, 0.0412).beta(second, FROM, TO, 0.02);

            PlantSharingObservation result = fixture.calculate();

            assertThat(result.analysis().computable()).isFalse();
            assertThat(result.unattributedAnomalies()).isEmpty();
            assertThat(result.agreements()).hasSize(2).allSatisfy(observation -> {
                assertThat(observation.coefficients()).isEmpty();
                assertThat(observation.leavingRows()).isEmpty();
                assertThat(observation.anomalies()).isEmpty();
            });
        }

        @Test
        @DisplayName("OBS-009 a row applied after more than half of the agreement's window reports its registered value, not the previous one")
        void lateRowReportsItsRegisteredValue() {
            ObservationFixture fixture = new ObservationFixture(FROM, TO);
            ObservedSupply first = fixture.supply("S1");
            ObservedSupply late = fixture.supply("S2");
            ObservedAgreement a = fixture.agreement("A", day("2024-03-01"));
            ObservedAgreement b = fixture.agreement("B", day("2024-05-01"));
            fixture.row(first, a, "0.03", day("2024-03-01"), CHANGE);
            fixture.row(late, a, "0.01", day("2024-03-01"), CHANGE);
            fixture.row(first, b, "0.04", CHANGE, null);
            ObservedCoefficient lateInB = fixture.row(late, b, "0.06", CHANGE, null);
            fixture.beta(first, FROM, day("2024-05-09"), 0.03).beta(first, CHANGE, TO, 0.04);
            // B's window runs from 10 May to 30 June, 52 days: S2 is still at A's value on the first 31 of them.
            fixture.beta(late, FROM, day("2024-06-09"), 0.01).beta(late, day("2024-06-10"), TO, 0.06);

            PlantSharingObservation result = fixture.calculate();

            assertThat(rowOf(result, lateInB).status()).isEqualTo(ObservationStatus.APPLIED_DIFFERENT_DATE);
            assertThat(rowOf(result, lateInB).observedBeta())
                    .isEqualTo(new ObservedBeta(new BigDecimal("0.060000"), 21));
        }

        @Test
        @DisplayName("OBS-009 a leaving row reports ≈ 0 from its own leaving date, not over the days the supply still received energy")
        void leavingRowFromItsOwnLeavingDate() {
            ObservationFixture fixture = new ObservationFixture(FROM, TO);
            ObservedSupply staying = fixture.supply("S1");
            ObservedSupply leaving = fixture.supply("S2");
            ObservedAgreement a = fixture.agreement("A", day("2024-03-01"));
            ObservedAgreement b = fixture.agreement("B", day("2024-05-01"));
            fixture.row(staying, a, "0.95", day("2024-03-01"), CHANGE);
            fixture.row(leaving, a, "0.05", day("2024-03-01"), CHANGE);
            fixture.row(staying, b, "1", CHANGE, null);
            fixture.beta(staying, FROM, day("2024-05-09"), 0.95).beta(staying, CHANGE, TO, 1.0);
            // S2 keeps receiving A's value for 31 of B's 52 days, then leaves on 10 June.
            fixture.beta(leaving, FROM, day("2024-06-09"), 0.05).beta(leaving, day("2024-06-10"), TO, 0.0);

            PlantSharingObservation result = fixture.calculate();

            assertThat(leavingsOf(result, b, leaving)).singleElement().satisfies(row -> {
                assertThat(row.observedLeavingDate()).isEqualTo(ObservedDate.exact(day("2024-06-10")));
                assertThat(row.observedBeta()).isEqualTo(new ObservedBeta(new BigDecimal("0.000000"), 21));
            });
        }

        @Test
        @DisplayName("OBS-009 a row whose observed date falls after the end of the agreement's window reports null over 0 days")
        void rowStartingAfterTheWindowIsNull() {
            ObservationFixture fixture = new ObservationFixture(FROM, TO);
            ObservedSupply first = fixture.supply("S1");
            ObservedSupply second = fixture.supply("S2");
            ObservedSupply newcomer = fixture.supply("S3");
            ObservedAgreement a = fixture.agreement("A", day("2024-03-01"));
            ObservedAgreement b = fixture.agreement("B", day("2024-05-01"));
            fixture.row(first, a, "0.03", day("2024-03-01"), CHANGE);
            fixture.row(second, a, "0.02", day("2024-03-01"), CHANGE);
            ObservedCoefficient newcomerInA = fixture.row(newcomer, a, "0.07", day("2024-03-01"), CHANGE);
            fixture.row(first, b, "0.04", CHANGE, null);
            fixture.row(second, b, "0.05", CHANGE, null);
            ObservedCoefficient newcomerInB = fixture.row(newcomer, b, "0.07", CHANGE, null);
            fixture.beta(first, FROM, day("2024-05-09"), 0.03).beta(first, CHANGE, TO, 0.04);
            fixture.beta(second, FROM, day("2024-05-09"), 0.02).beta(second, CHANGE, TO, 0.05);
            // S3 receives nothing until 15 May, after A's window ends on 9 May.
            fixture.beta(newcomer, FROM, day("2024-05-14"), 0.0).beta(newcomer, day("2024-05-15"), TO, 0.07);

            PlantSharingObservation result = fixture.calculate();

            assertThat(rowOf(result, newcomerInA).observedDate()).isEqualTo(ObservedDate.exact(day("2024-05-15")));
            assertThat(rowOf(result, newcomerInA).observedBeta()).isEqualTo(new ObservedBeta(null, 0));
            // Not observed on its own, the row in B keeps B's whole window.
            assertThat(rowOf(result, newcomerInB).status()).isEqualTo(ObservationStatus.NOT_DISTINGUISHABLE);
            assertThat(rowOf(result, newcomerInB).observedBeta())
                    .isEqualTo(new ObservedBeta(new BigDecimal("0.070000"), 52));
        }

        private Rows twoAgreements() {
            ObservationFixture fixture = new ObservationFixture(FROM, TO);
            ObservedSupply first = fixture.supply("S1");
            ObservedSupply second = fixture.supply("S2");
            ObservedAgreement a = fixture.agreement("A", day("2024-03-01"));
            ObservedAgreement b = fixture.agreement("B", day("2024-05-01"));
            ObservedCoefficient firstInA = fixture.row(first, a, "0.03", day("2024-03-01"), CHANGE);
            ObservedCoefficient secondInA = fixture.row(second, a, "0.02", day("2024-03-01"), CHANGE);
            ObservedCoefficient firstInB = fixture.row(first, b, "0.04", CHANGE, null);
            ObservedCoefficient secondInB = fixture.row(second, b, "0.05", CHANGE, null);
            fixture.beta(first, FROM, day("2024-04-19"), 0.0298)
                    .beta(first, day("2024-04-20"), day("2024-05-09"), 0.0302)
                    .beta(first, CHANGE, day("2024-06-04"), 0.0399)
                    .beta(first, day("2024-06-05"), TO, 0.0401);
            fixture.beta(second, FROM, day("2024-05-09"), 0.02).beta(second, CHANGE, TO, 0.05);
            return new Rows(fixture.calculate(), firstInA, secondInA, firstInB, secondInB);
        }

        private record Rows(PlantSharingObservation result, ObservedCoefficient firstInA,
                            ObservedCoefficient secondInA, ObservedCoefficient firstInB,
                            ObservedCoefficient secondInB) {
        }
    }

    @Nested
    class StoredPeriods {

        @Test
        @DisplayName("OBS-010 a coefficient row reports its stored period as local days: a closed end exclusive, an open one null")
        void storedPeriod() {
            ObservationFixture fixture = new ObservationFixture(FROM, TO);
            ObservedSupply first = fixture.supply("S1");
            ObservedSupply second = fixture.supply("S2");
            ObservedAgreement a = fixture.agreement("A", day("2024-03-01"));
            ObservedAgreement b = fixture.agreement("B", day("2024-05-01"));
            ObservedCoefficient closed = fixture.row(first, a, "0.03", day("2024-03-01"), day("2024-05-10"));
            // Stored at 00:00 UTC, 02:00 in Madrid: the same local day.
            ObservedCoefficient atUtcMidnight = fixture.rowAt(second, a, "0.02",
                    Instant.parse("2024-03-01T00:00:00Z"), Instant.parse("2024-05-10T00:00:00Z"));
            ObservedCoefficient open = fixture.row(first, b, "0.04", day("2024-05-10"), null);
            fixture.row(second, b, "0.05", day("2024-05-10"), null);
            fixture.beta(first, FROM, day("2024-05-09"), 0.03).beta(first, day("2024-05-10"), TO, 0.04);
            fixture.beta(second, FROM, day("2024-05-09"), 0.02).beta(second, day("2024-05-10"), TO, 0.05);

            PlantSharingObservation result = fixture.calculate();

            assertThat(rowOf(result, closed).storedValidFrom()).isEqualTo(day("2024-03-01"));
            assertThat(rowOf(result, closed).storedValidTo()).isEqualTo(day("2024-05-10"));
            assertThat(rowOf(result, atUtcMidnight).storedValidFrom()).isEqualTo(day("2024-03-01"));
            assertThat(rowOf(result, atUtcMidnight).storedValidTo()).isEqualTo(day("2024-05-10"));
            assertThat(rowOf(result, open).storedValidTo()).isNull();
        }
    }

    @Nested
    class PlantSupplyRows {

        private static final Offset<Double> PRECISION = Offset.offset(1e-9);

        @Test
        @DisplayName("OBS-011 a plant supply's coefficient is listed as not observable, with its stored period, and counted")
        void plantSupplyRowIsNotObservable() {
            ObservationFixture fixture = new ObservationFixture(FROM, TO);
            ObservedSupply first = fixture.supply("S1");
            ObservedSupply second = fixture.supply("S2");
            ObservedSupply plant = fixture.plantSupply();
            ObservedAgreement a = fixture.agreement("A", day("2024-03-01"));
            ObservedAgreement b = fixture.agreement("B", day("2024-05-01"));
            fixture.row(first, a, "0.5", day("2024-03-01"), day("2024-05-01"));
            fixture.row(second, a, "0.5", day("2024-03-01"), day("2024-05-01"));
            fixture.row(first, b, "0.6", day("2024-05-01"), null);
            fixture.row(second, b, "0.39", day("2024-05-01"), null);
            ObservedCoefficient plantInB = fixture.row(plant, b, "0.01", day("2024-05-01"), day("2024-07-01"));
            fixture.beta(first, FROM, day("2024-04-30"), 0.5).beta(first, day("2024-05-01"), TO, 0.6);
            fixture.beta(second, FROM, day("2024-04-30"), 0.5).beta(second, day("2024-05-01"), TO, 0.39);

            PlantSharingObservation result = fixture.calculate();

            CoefficientObservation row = rowOf(result, plantInB);
            assertThat(row.supply()).isEqualTo(plant);
            assertThat(row.coefficient()).isEqualByComparingTo("0.01");
            assertThat(row.status()).isEqualTo(ObservationStatus.NOT_OBSERVABLE);
            assertThat(row.storedValidFrom()).isEqualTo(day("2024-05-01"));
            assertThat(row.storedValidTo()).isEqualTo(day("2024-07-01"));
            assertThat(row.observedDate()).isNull();
            assertThat(row.proposedDate()).isNull();
            assertThat(row.observedBeta()).isEqualTo(ObservedBeta.NONE);
            assertThat(agreementOf(result, b).coefficients()).extracting(CoefficientObservation::supply)
                    .containsExactlyInAnyOrder(plant, first, second);
            assertThat(agreementOf(result, b).coefficientStatusCounts())
                    .containsEntry(ObservationStatus.NOT_OBSERVABLE, 1)
                    .containsEntry(ObservationStatus.APPLIED_SAME_DATE, 2);
            assertThat(agreementOf(result, a).coefficientStatusCounts())
                    .containsEntry(ObservationStatus.NOT_OBSERVABLE, 0);
            assertThat(agreementOf(result, b).leavingStatusCounts())
                    .containsEntry(ObservationStatus.NOT_OBSERVABLE, 0);
        }

        @Test
        @DisplayName("OBS-002 a row of the plant's own supply is not observable, never no data, while a consumer without valid days is")
        void plantSupplyRowIsNeverNoData() {
            ObservationFixture fixture = new ObservationFixture(FROM, TO);
            ObservedSupply first = fixture.supply("S1");
            ObservedSupply silent = fixture.supply("S2");
            ObservedSupply plant = fixture.plantSupply();
            ObservedAgreement a = fixture.agreement("A", day("2024-03-01"));
            fixture.agreement("B", null);
            fixture.row(first, a, "0.9", day("2024-03-01"), null);
            ObservedCoefficient silentInA = fixture.row(silent, a, "0.09", day("2024-03-01"), null);
            ObservedCoefficient plantInA = fixture.row(plant, a, "0.01", day("2024-03-01"), null);
            fixture.beta(first, FROM, TO, 0.9);

            PlantSharingObservation result = fixture.calculate();

            assertThat(rowOf(result, silentInA).status()).isEqualTo(ObservationStatus.NO_DATA);
            assertThat(rowOf(result, plantInA).status()).isEqualTo(ObservationStatus.NOT_OBSERVABLE);
            assertThat(result.analysis().suppliesWithoutData()).containsExactly(silent);
        }

        @Test
        @DisplayName("OBS-011 several rows of the plant supply in one agreement are each listed and counted")
        void severalPlantSupplyRows() {
            PlantSharingObservation result = switchingMidWindow();
            AgreementObservation b = result.agreements().get(1);

            assertThat(b.coefficients()).filteredOn(row -> row.supply().code().equals("PLANT"))
                    .extracting(CoefficientObservation::coefficient, CoefficientObservation::status)
                    .containsExactly(
                            tuple(new BigDecimal("0.01"), ObservationStatus.NOT_OBSERVABLE),
                            tuple(new BigDecimal("0.02"), ObservationStatus.NOT_OBSERVABLE));
            assertThat(b.coefficientStatusCounts()).containsEntry(ObservationStatus.NOT_OBSERVABLE, 2);
        }

        @Test
        @DisplayName("OBS-011 the share without data follows the plant supply's row whose stored period holds each day")
        void shareWithoutDataFollowsTheRowOfTheDay() {
            PlantSharingObservation result = switchingMidWindow();

            // B is applied from 2024-05-01: 31 days at 100 kWh under 0.01, then 30 days at 200 kWh under 0.02.
            UnallocatedShare share = result.agreements().get(1).unallocatedShare();
            assertThat(share.days()).isEqualTo(61);
            assertThat(share.registeredShareWithoutData())
                    .isCloseTo((31 * 100 * 0.01 + 30 * 200 * 0.02) / (31 * 100 + 30 * 200), PRECISION);
            // A holds no row of the plant supply: its days before the switch are unchanged.
            assertThat(result.agreements().get(0).unallocatedShare().registeredShareWithoutData())
                    .isCloseTo(0, PRECISION);
        }

        /**
         * A, then B from 2024-05-01, in which the plant supply holds 0.01 until 2024-06-01 and 0.02 from then on,
         * while the plant produces 200 kWh a day in June.
         */
        private PlantSharingObservation switchingMidWindow() {
            ObservationFixture fixture = new ObservationFixture(FROM, TO)
                    .production(day("2024-06-01"), 200);
            for (LocalDate day = day("2024-06-02"); !day.isAfter(TO); day = day.plusDays(1)) {
                fixture.production(day, 200);
            }
            ObservedSupply first = fixture.supply("S1");
            ObservedSupply second = fixture.supply("S2");
            ObservedSupply plant = fixture.plantSupply();
            ObservedAgreement a = fixture.agreement("A", day("2024-03-01"));
            ObservedAgreement b = fixture.agreement("B", day("2024-05-01"));
            fixture.row(first, a, "0.5", day("2024-03-01"), day("2024-05-01"));
            fixture.row(second, a, "0.5", day("2024-03-01"), day("2024-05-01"));
            fixture.row(first, b, "0.6", day("2024-05-01"), null);
            fixture.row(second, b, "0.38", day("2024-05-01"), null);
            fixture.row(plant, b, "0.02", day("2024-06-01"), null);
            fixture.row(plant, b, "0.01", day("2024-05-01"), day("2024-06-01"));
            fixture.beta(first, FROM, day("2024-04-30"), 0.5).beta(first, day("2024-05-01"), TO, 0.6);
            fixture.beta(second, FROM, day("2024-04-30"), 0.5).beta(second, day("2024-05-01"), TO, 0.38);
            return fixture.calculate();
        }

        @Test
        @DisplayName("OBS-011 the plant supply's coefficient counts as without data, and 1 − Σβ does not change")
        void plantCoefficientCountsAsWithoutData() {
            UnallocatedShare without = shareOfB(false);
            UnallocatedShare with = shareOfB(true);

            assertThat(with.registeredShareWithoutData())
                    .isCloseTo(without.registeredShareWithoutData() + 0.01, PRECISION);
            assertThat(with.unallocated()).isEqualTo(without.unallocated());
            assertThat(with.days()).isEqualTo(without.days());
        }

        private UnallocatedShare shareOfB(boolean plantHoldsARow) {
            ObservationFixture fixture = new ObservationFixture(FROM, TO);
            ObservedSupply first = fixture.supply("S1");
            ObservedSupply second = fixture.supply("S2");
            ObservedSupply silent = fixture.supply("S3");
            ObservedSupply plant = fixture.plantSupply();
            ObservedAgreement a = fixture.agreement("A", day("2024-03-01"));
            ObservedAgreement b = fixture.agreement("B", day("2024-05-01"));
            fixture.row(first, a, "0.5", day("2024-03-01"), day("2024-05-01"));
            fixture.row(second, a, "0.5", day("2024-03-01"), day("2024-05-01"));
            fixture.row(first, b, "0.6", day("2024-05-01"), null);
            fixture.row(second, b, "0.34", day("2024-05-01"), null);
            fixture.row(silent, b, "0.05", day("2024-05-01"), null);
            if (plantHoldsARow) {
                fixture.row(plant, b, "0.01", day("2024-05-01"), null);
            }
            fixture.beta(first, FROM, day("2024-04-30"), 0.5).beta(first, day("2024-05-01"), TO, 0.6);
            fixture.beta(second, FROM, day("2024-04-30"), 0.5).beta(second, day("2024-05-01"), TO, 0.34);
            return agreementOf(fixture.calculate(), b).unallocatedShare();
        }

        @Test
        @DisplayName("OBS-011 a production month's shape: the plant supply's 0.3167 % coefficient explains the unallocated share up to rounding")
        void productionMonthShapeIsExplained() {
            ObservationFixture fixture = new ObservationFixture(FROM, TO);
            ObservedSupply first = fixture.supply("S1");
            ObservedSupply second = fixture.supply("S2");
            ObservedSupply withoutData = fixture.supply("S3");
            ObservedSupply alsoWithoutData = fixture.supply("S4");
            ObservedSupply plant = fixture.plantSupply();
            ObservedAgreement synthetic = fixture.agreement("Synthetic", null);
            ObservedAgreement second2026 = fixture.agreement("Second", day("2024-03-01"));
            fixture.row(first, synthetic, "0.7", (LocalDate) null, null);
            fixture.row(second, synthetic, "0.3", (LocalDate) null, null);
            // Registered for the supplies with data: 0.9435, which is what they receive.
            fixture.row(first, second2026, "0.5", day("2024-03-01"), null);
            fixture.row(second, second2026, "0.4435", day("2024-03-01"), null);
            fixture.row(withoutData, second2026, "0.020000", day("2024-03-01"), null);
            fixture.row(alsoWithoutData, second2026, "0.033333", day("2024-03-01"), null);
            fixture.row(plant, second2026, "0.003167", day("2024-03-01"), null);
            fixture.beta(first, FROM, TO, 0.5).beta(second, FROM, TO, 0.4435);

            UnallocatedShare share = agreementOf(fixture.calculate(), second2026).unallocatedShare();

            assertThat(share.unallocated()).isCloseTo(0.0565, Offset.offset(1e-6));
            assertThat(share.registeredShareWithoutData()).isCloseTo(0.0565, Offset.offset(1e-6));
            assertThat(share.unallocated() - share.registeredShareWithoutData())
                    .isCloseTo(0, Offset.offset(1e-6));
        }

        @Test
        @DisplayName("OBS-011 the plant supply gets no β, anomaly, leaving row or recommendation, whatever its own data")
        void plantSupplyIsNeverObserved() {
            ObservationFixture fixture = new ObservationFixture(FROM, TO);
            ObservedSupply first = fixture.supply("S1");
            ObservedSupply second = fixture.supply("S2");
            ObservedSupply plant = fixture.plantSupply();
            ObservedAgreement a = fixture.agreement("A", day("2024-03-01"));
            ObservedAgreement b = fixture.agreement("B", day("2024-05-01"));
            fixture.row(first, a, "0.5", day("2024-03-01"), day("2024-05-01"));
            fixture.row(second, a, "0.49", day("2024-03-01"), day("2024-05-01"));
            // Held in A, absent from B: a consumer would leave in B.
            ObservedCoefficient plantInA = fixture.row(plant, a, "0.01", (LocalDate) null, day("2024-03-15"));
            fixture.row(first, b, "0.6", day("2024-05-01"), null);
            fixture.row(second, b, "0.4", day("2024-05-01"), null);
            fixture.beta(first, FROM, day("2024-04-30"), 0.5).beta(first, day("2024-05-01"), TO, 0.6);
            fixture.beta(second, FROM, day("2024-04-30"), 0.49).beta(second, day("2024-05-01"), TO, 0.4);
            // Its own data matches its row in A, then nothing: it must not be read as a consumer.
            fixture.beta(plant, FROM, day("2024-04-30"), 0.01).beta(plant, day("2024-05-01"), TO, 0.3);

            PlantSharingObservation result = fixture.calculate();

            assertThat(rowOf(result, plantInA).status()).isEqualTo(ObservationStatus.NOT_OBSERVABLE);
            assertThat(rowOf(result, plantInA).observedBeta()).isEqualTo(ObservedBeta.NONE);
            assertThat(leavingsOf(result, b, plant)).isEmpty();
            assertThat(anomaliesOf(result, plant)).isEmpty();
            assertThat(result.analysis().suppliesWithData()).containsExactly(first, second);
            // Its stored dates differ from anything observed, yet nothing asks to set them.
            assertThat(agreementOf(result, a).recommendations()).containsExactly(SharingRecommendation.NONE);
            assertThat(agreementOf(result, b).recommendations()).containsExactly(SharingRecommendation.NONE);
        }

        @Test
        @DisplayName("OBS-011 an unsent agreement whose only coefficient is the plant supply's is still to deactivate")
        void onlyPlantCoefficientIsNotObserved() {
            ObservationFixture fixture = new ObservationFixture(FROM, TO);
            ObservedSupply first = fixture.supply("S1");
            ObservedSupply second = fixture.supply("S2");
            ObservedSupply plant = fixture.plantSupply();
            ObservedAgreement a = fixture.agreement("A", day("2024-03-01"));
            ObservedAgreement plantOnly = fixture.agreement("Plant only", null);
            fixture.row(first, a, "0.5", day("2024-03-01"), null);
            fixture.row(second, a, "0.5", day("2024-03-01"), null);
            ObservedCoefficient plantRow = fixture.row(plant, plantOnly, "1", (LocalDate) null, null);
            fixture.beta(first, FROM, TO, 0.5).beta(second, FROM, TO, 0.5);

            PlantSharingObservation result = fixture.calculate();

            AgreementObservation observation = agreementOf(result, plantOnly);
            assertThat(observation.coefficients()).extracting(CoefficientObservation::coefficientId)
                    .containsExactly(plantRow.id());
            assertThat(observation.coefficientStatusCounts()).containsEntry(ObservationStatus.NOT_OBSERVABLE, 1);
            assertThat(observation.recommendations()).containsExactly(SharingRecommendation.DEACTIVATE);
            assertThat(observation.mostFrequentObservedDate()).isNull();
            assertThat(observation.unallocatedShare()).isNull();
        }

        @Test
        @DisplayName("OBS-011 an agreement's slice holds the plant supply's row exactly as the plant result does")
        void sliceHoldsThePlantSupplyRow() {
            ObservationFixture fixture = new ObservationFixture(FROM, TO);
            ObservedSupply first = fixture.supply("S1");
            ObservedSupply second = fixture.supply("S2");
            ObservedSupply plant = fixture.plantSupply();
            ObservedAgreement a = fixture.agreement("A", day("2024-03-01"));
            ObservedAgreement b = fixture.agreement("B", day("2024-05-01"));
            fixture.row(first, a, "0.5", day("2024-03-01"), null);
            fixture.row(second, a, "0.5", day("2024-03-01"), null);
            fixture.row(first, b, "0.6", (LocalDate) null, null);
            fixture.row(second, b, "0.39", (LocalDate) null, null);
            ObservedCoefficient plantInB = fixture.row(plant, b, "0.01", (LocalDate) null, null);
            fixture.beta(first, FROM, TO, 0.5).beta(second, FROM, TO, 0.5);

            PlantSharingObservation result = fixture.calculate();

            assertThat(result.sliceFor(b.id())).hasValueSatisfying(slice -> {
                assertThat(slice.agreement()).isEqualTo(agreementOf(result, b));
                assertThat(slice.agreement().coefficients()).contains(rowOf(result, plantInB));
            });
            assertThat(rowOf(result, plantInB).status()).isEqualTo(ObservationStatus.NOT_OBSERVABLE);
        }

        @Test
        @DisplayName("OBS-011 a plant that is not computable lists no row, the plant supply's included")
        void notComputablePlantListsNoPlantSupplyRow() {
            ObservationFixture fixture = new ObservationFixture(FROM, TO).plantWithoutData();
            ObservedSupply first = fixture.supply("S1");
            ObservedSupply second = fixture.supply("S2");
            ObservedSupply plant = fixture.plantSupply();
            ObservedAgreement a = fixture.agreement("A", day("2024-03-01"));
            fixture.agreement("B", null);
            fixture.row(first, a, "0.5", day("2024-03-01"), null);
            fixture.row(second, a, "0.49", day("2024-03-01"), null);
            fixture.row(plant, a, "0.01", day("2024-03-01"), null);

            PlantSharingObservation result = fixture.calculate();

            assertThat(result.agreements()).hasSize(2).allSatisfy(observation -> {
                assertThat(observation.coefficients()).isEmpty();
                assertThat(observation.coefficientStatusCounts()).containsEntry(ObservationStatus.NOT_OBSERVABLE, 0);
            });
        }
    }

    @Nested
    class ExpectedBeta {

        private static final LocalDate CHANGE = day("2024-05-10");

        @Test
        @DisplayName("OBS-012 an unknown coefficient attributed to B reports the supply's value in B as expected, next to what it received")
        void unknownCoefficientReportsTheValueInTheAttributedAgreement() {
            ObservationFixture fixture = new ObservationFixture(FROM, TO);
            ObservedSupply first = fixture.supply("S1");
            ObservedSupply second = fixture.supply("S2");
            ObservedAgreement a = fixture.agreement("A", day("2024-03-01"));
            ObservedAgreement b = fixture.agreement("B", day("2024-05-01"));
            fixture.row(first, a, "0.03", day("2024-03-01"), CHANGE);
            fixture.row(second, a, "0.02", day("2024-03-01"), CHANGE);
            fixture.row(first, b, "0.0315", CHANGE, null);
            fixture.row(second, b, "0.02", CHANGE, null);
            fixture.beta(first, FROM, day("2024-05-09"), 0.03).beta(first, CHANGE, TO, 0.0315)
                    .beta(first, day("2024-06-01"), day("2024-06-05"), 0.025);
            fixture.beta(second, FROM, TO, 0.02);

            PlantSharingObservation result = fixture.calculate();

            assertThat(anomaliesOf(result, first)).singleElement().satisfies(anomaly -> {
                assertThat(anomaly.type()).isEqualTo(SharingAnomalyType.UNKNOWN_COEFFICIENT);
                assertThat(anomaly.attributedAgreementId()).isEqualTo(b.id());
                assertThat(anomaly.expectedBeta()).isEqualTo(new BigDecimal("0.031500"));
                assertThat(anomaly.observedBeta().value()).isEqualTo(new BigDecimal("0.025000"));
            });
        }

        @Test
        @DisplayName("OBS-012 no energy in an agreement reports the supply's value in the agreement it is attributed to")
        void noEnergyReportsTheValueInTheAttributedAgreement() {
            ObservationFixture fixture = new ObservationFixture(FROM, TO);
            ObservedSupply first = fixture.supply("S1");
            ObservedSupply second = fixture.supply("S2");
            ObservedAgreement a = fixture.agreement("A", day("2024-03-01"));
            ObservedAgreement b = fixture.agreement("B", day("2024-05-01"));
            fixture.row(first, a, "0.03", day("2024-03-01"), CHANGE);
            fixture.row(second, a, "0.02", day("2024-03-01"), CHANGE);
            fixture.row(first, b, "0.04", CHANGE, null);
            fixture.row(second, b, "0.05", CHANGE, null);
            fixture.beta(first, FROM, day("2024-05-09"), 0.03).beta(first, CHANGE, TO, 0.04)
                    .beta(first, day("2024-06-20"), TO, 0.0);
            fixture.beta(second, FROM, day("2024-05-09"), 0.02).beta(second, CHANGE, TO, 0.05);

            PlantSharingObservation result = fixture.calculate();

            assertThat(anomaliesOf(result, first)).singleElement().satisfies(anomaly -> {
                assertThat(anomaly.type()).isEqualTo(SharingAnomalyType.NO_ENERGY_IN_AGREEMENT);
                assertThat(anomaly.attributedAgreementId()).isEqualTo(b.id());
                assertThat(anomaly.expectedBeta()).isEqualTo(new BigDecimal("0.040000"));
            });
        }

        @Test
        @DisplayName("OBS-012 a reverted application reports the value of the later agreement it is attributed to, not the value it reverts to")
        void revertedReportsTheLaterAgreementsValue() {
            ObservationFixture fixture = new ObservationFixture(FROM, TO);
            ObservedSupply first = fixture.supply("S1");
            ObservedSupply second = fixture.supply("S2");
            ObservedAgreement a = fixture.agreement("A", day("2024-03-01"));
            ObservedAgreement b = fixture.agreement("B", day("2024-04-25"));
            fixture.row(first, a, "0.03", day("2024-03-01"), day("2024-05-01"));
            fixture.row(second, a, "0.02", day("2024-03-01"), day("2024-05-01"));
            fixture.row(first, b, "0.04", day("2024-05-01"), null);
            fixture.row(second, b, "0.02", day("2024-05-01"), null);
            fixture.beta(first, FROM, day("2024-04-30"), 0.03)
                    .beta(first, day("2024-05-01"), day("2024-05-31"), 0.04)
                    .beta(first, day("2024-06-01"), day("2024-06-10"), 0.03)
                    .beta(first, day("2024-06-11"), TO, 0.04);
            fixture.beta(second, FROM, TO, 0.02);

            PlantSharingObservation result = fixture.calculate();

            assertThat(anomaliesOf(result, first)).singleElement().satisfies(anomaly -> {
                assertThat(anomaly.type()).isEqualTo(SharingAnomalyType.REVERTED);
                assertThat(anomaly.attributedAgreementId()).isEqualTo(b.id());
                assertThat(anomaly.expectedBeta()).isEqualTo(new BigDecimal("0.040000"));
                // What it reverted to is what it received, not what it should have received.
                assertThat(anomaly.expectedBeta()).isNotEqualByComparingTo(new BigDecimal("0.03"));
                assertThat(anomaly.observedBeta().value()).isEqualTo(new BigDecimal("0.030000"));
            });
        }

        @Test
        @DisplayName("OBS-012 an anomaly attributed to an agreement the supply holds no row in reports 0: an unknown coefficient after leaving")
        void attributedToAnAgreementWithoutARowReportsZero() {
            ObservationFixture fixture = new ObservationFixture(FROM, TO);
            ObservedSupply staying = fixture.supply("S1");
            ObservedSupply leaving = fixture.supply("S2");
            ObservedAgreement a = fixture.agreement("A", day("2024-03-01"));
            ObservedAgreement b = fixture.agreement("B", day("2024-05-01"));
            fixture.row(staying, a, "0.97", day("2024-03-01"), CHANGE);
            fixture.row(leaving, a, "0.03", day("2024-03-01"), CHANGE);
            fixture.row(staying, b, "1", CHANGE, null);
            fixture.beta(staying, FROM, day("2024-05-09"), 0.97).beta(staying, CHANGE, TO, 1.0);
            // Leaves in B, then receives a share no agreement registers.
            fixture.beta(leaving, FROM, day("2024-05-09"), 0.03).beta(leaving, CHANGE, TO, 0.0)
                    .beta(leaving, day("2024-06-01"), day("2024-06-05"), 0.05);

            PlantSharingObservation result = fixture.calculate();

            assertThat(anomaliesOf(result, leaving)).singleElement().satisfies(anomaly -> {
                assertThat(anomaly.type()).isEqualTo(SharingAnomalyType.UNKNOWN_COEFFICIENT);
                assertThat(anomaly.attributedAgreementId()).isEqualTo(b.id());
                assertThat(anomaly.expectedBeta()).isEqualTo(new BigDecimal("0.000000"));
            });
        }

        @Test
        @DisplayName("OBS-012 energy outside the agreements of a supply registered at 0 reports 0, the value of the agreement it is attributed to")
        void energyOutsideAgreementOfASupplyRegisteredAtZeroReportsZero() {
            ObservationFixture fixture = new ObservationFixture(FROM, TO);
            ObservedSupply member = fixture.supply("S1");
            ObservedSupply atZero = fixture.supply("S2");
            ObservedAgreement a = fixture.agreement("A", day("2024-03-01"));
            ObservedAgreement b = fixture.agreement("B", null);
            fixture.row(member, a, "1", day("2024-03-01"), null);
            fixture.row(atZero, a, "0", day("2024-03-01"), null);
            fixture.row(member, b, "1", (LocalDate) null, null);
            fixture.beta(member, FROM, TO, 1.0);
            fixture.beta(atZero, FROM, TO, 0.0).beta(atZero, day("2024-05-01"), day("2024-05-10"), 0.02);

            PlantSharingObservation result = fixture.calculate();

            assertThat(anomaliesOf(result, atZero)).singleElement().satisfies(anomaly -> {
                assertThat(anomaly.type()).isEqualTo(SharingAnomalyType.ENERGY_OUTSIDE_AGREEMENT);
                assertThat(anomaly.attributedAgreementId()).isEqualTo(a.id());
                assertThat(anomaly.expectedBeta()).isEqualTo(new BigDecimal("0.000000"));
            });
        }

        @Test
        @DisplayName("OBS-012 an anomaly attributed to no agreement reports no expected β")
        void unattributedReportsNull() {
            ObservationFixture fixture = new ObservationFixture(FROM, TO);
            ObservedSupply member = fixture.supply("S1");
            ObservedSupply outsider = fixture.supply("S2");
            ObservedAgreement a = fixture.agreement("A", day("2024-03-01"));
            ObservedAgreement b = fixture.agreement("B", null);
            fixture.row(member, a, "1", day("2024-03-01"), null);
            fixture.row(member, b, "1", (LocalDate) null, null);
            fixture.beta(member, FROM, TO, 1.0).beta(outsider, FROM, TO, 0.0)
                    .beta(outsider, day("2024-05-01"), day("2024-05-20"), 0.02);

            PlantSharingObservation result = fixture.calculate();

            assertThat(result.unattributedAnomalies()).singleElement().satisfies(anomaly -> {
                assertThat(anomaly.type()).isEqualTo(SharingAnomalyType.ENERGY_OUTSIDE_AGREEMENT);
                assertThat(anomaly.attributedAgreementId()).isNull();
                assertThat(anomaly.expectedBeta()).isNull();
            });
        }

        @Test
        @DisplayName("OBS-012 an application before the sent date reports the value of the late-sent agreement it is attributed to")
        void appliedBeforeSentReportsTheLateSentAgreementsValue() {
            ObservationFixture fixture = new ObservationFixture(FROM, TO);
            ObservedSupply first = fixture.supply("S1");
            ObservedSupply second = fixture.supply("S2");
            ObservedAgreement a = fixture.agreement("A", day("2024-03-01"));
            ObservedAgreement b = fixture.agreement("B", day("2024-05-15"));
            fixture.row(first, a, "0.03", day("2024-03-01"), CHANGE);
            fixture.row(second, a, "0.02", day("2024-03-01"), CHANGE);
            fixture.row(first, b, "0.04", CHANGE, null);
            fixture.row(second, b, "0.02", CHANGE, null);
            fixture.beta(first, FROM, day("2024-05-09"), 0.03).beta(first, CHANGE, TO, 0.04);
            fixture.beta(second, FROM, TO, 0.02);

            PlantSharingObservation result = fixture.calculate();

            assertThat(anomaliesOf(result, first)).singleElement().satisfies(anomaly -> {
                assertThat(anomaly.type()).isEqualTo(SharingAnomalyType.APPLIED_BEFORE_SENT);
                assertThat(anomaly.attributedAgreementId()).isEqualTo(b.id());
                // It matches what the supply received: the anomaly is about the date, not the value.
                assertThat(anomaly.expectedBeta()).isEqualTo(new BigDecimal("0.040000"));
                assertThat(anomaly.observedBeta().value()).isEqualTo(new BigDecimal("0.040000"));
            });
        }

        @Test
        @DisplayName("OBS-012 an agreement's slice reports the same expected β as the plant's result for each of its anomalies")
        void sliceReportsTheSameExpectedBeta() {
            PlantSharingObservation result = regressionFixture().fixture().calculate();

            assertThat(result.agreements()).allSatisfy(observation -> {
                SharingAgreementObservation slice = result.sliceFor(observation.agreement().id()).orElseThrow();
                assertThat(slice.agreement().anomalies())
                        .extracting(SharingAnomaly::id, SharingAnomaly::expectedBeta)
                        .containsExactlyElementsOf(observation.anomalies().stream()
                                .map(anomaly -> tuple(anomaly.id(), anomaly.expectedBeta()))
                                .toList());
            });
            assertThat(result.agreements()).flatExtracting(AgreementObservation::anomalies).isNotEmpty();
        }

        @Test
        @DisplayName("OBS-012 reporting the expected β changes no status, date, anomaly, recommendation, observed β or 1 − Σβ")
        void changesNothingElse() {
            assertNothingElseChanged(regressionFixture());
        }
    }

    @Nested
    class WindowDays {

        private static final LocalDate CHANGE = day("2024-05-10");

        @Test
        @DisplayName("OBS-013 a row observed 91 days before its agreement's 335-day window reports 426 window days; one observed on the window's start or with no observed date reports 335")
        void rowWindowFromItsOwnObservedDate() {
            LocalDate from = day("2024-04-01");
            LocalDate windowStart = from.plusDays(91);
            LocalDate to = from.plusDays(425);
            ObservationFixture fixture = new ObservationFixture(from, to);
            ObservedSupply early = fixture.supply("S1");
            ObservedSupply onTime = fixture.supply("S2");
            ObservedSupply alsoOnTime = fixture.supply("S3");
            ObservedSupply unchanged = fixture.supply("S4");
            ObservedAgreement a = fixture.agreement("A", day("2024-03-01"));
            ObservedAgreement b = fixture.agreement("B", null);
            fixture.row(early, a, "0.05", day("2024-03-01"), windowStart);
            fixture.row(onTime, a, "0.02", day("2024-03-01"), windowStart);
            fixture.row(alsoOnTime, a, "0.04", day("2024-03-01"), windowStart);
            fixture.row(unchanged, a, "0.07", day("2024-03-01"), windowStart);
            ObservedCoefficient earlyInB = fixture.row(early, b, "0.10", windowStart, null);
            ObservedCoefficient onTimeInB = fixture.row(onTime, b, "0.03", windowStart, null);
            fixture.row(alsoOnTime, b, "0.06", windowStart, null);
            ObservedCoefficient unchangedInB = fixture.row(unchanged, b, "0.07", windowStart, null);
            // S1 receives B's value from the first day of the data: observed "on or before" it.
            fixture.beta(early, from, to, 0.10);
            fixture.beta(onTime, from, windowStart.minusDays(1), 0.02).beta(onTime, windowStart, to, 0.03);
            fixture.beta(alsoOnTime, from, windowStart.minusDays(1), 0.04).beta(alsoOnTime, windowStart, to, 0.06);
            fixture.beta(unchanged, from, to, 0.07);

            PlantSharingObservation result = fixture.calculate();

            assertThat(agreementOf(result, b).unallocatedShare().days()).isEqualTo(335);
            assertThat(rowOf(result, earlyInB).observedDate()).isEqualTo(ObservedDate.onOrBefore(from));
            assertThat(rowOf(result, earlyInB).windowDays()).isEqualTo(426);
            assertThat(rowOf(result, onTimeInB).observedDate()).isEqualTo(ObservedDate.exact(windowStart));
            assertThat(rowOf(result, onTimeInB).windowDays()).isEqualTo(335);
            assertThat(rowOf(result, unchangedInB).observedDate()).isNull();
            assertThat(rowOf(result, unchangedInB).windowDays()).isEqualTo(335);
            // Every row's slice reports the same window days as the plant's result.
            assertThat(result.agreements()).allSatisfy(observation ->
                    assertThat(result.sliceFor(observation.agreement().id()).orElseThrow().agreement().coefficients())
                            .extracting(CoefficientObservation::coefficientId, CoefficientObservation::windowDays)
                            .containsExactlyElementsOf(observation.coefficients().stream()
                                    .map(row -> tuple(row.coefficientId(), row.windowDays()))
                                    .toList()));
        }

        @Test
        @DisplayName("OBS-013 a row without a row window reports 0 window days: its agreement was never observed, or the row starts after the window ends")
        void rowWithoutAWindowReportsZero() {
            ObservationFixture fixture = new ObservationFixture(FROM, TO);
            ObservedSupply first = fixture.supply("S1");
            ObservedSupply second = fixture.supply("S2");
            ObservedSupply newcomer = fixture.supply("S3");
            ObservedAgreement a = fixture.agreement("A", day("2024-03-01"));
            ObservedAgreement b = fixture.agreement("B", day("2024-05-01"));
            ObservedAgreement never = fixture.agreement("Never applied", null);
            fixture.row(first, a, "0.03", day("2024-03-01"), CHANGE);
            fixture.row(second, a, "0.02", day("2024-03-01"), CHANGE);
            ObservedCoefficient newcomerInA = fixture.row(newcomer, a, "0.07", day("2024-03-01"), CHANGE);
            fixture.row(first, b, "0.04", CHANGE, null);
            fixture.row(second, b, "0.05", CHANGE, null);
            ObservedCoefficient newcomerInB = fixture.row(newcomer, b, "0.07", CHANGE, null);
            ObservedCoefficient firstInNever = fixture.row(first, never, "0.5", (LocalDate) null, null);
            ObservedCoefficient secondInNever = fixture.row(second, never, "0.5", (LocalDate) null, null);
            fixture.beta(first, FROM, day("2024-05-09"), 0.03).beta(first, CHANGE, TO, 0.04);
            fixture.beta(second, FROM, day("2024-05-09"), 0.02).beta(second, CHANGE, TO, 0.05);
            // S3 receives nothing until 15 May, after A's window ends on 9 May.
            fixture.beta(newcomer, FROM, day("2024-05-14"), 0.0).beta(newcomer, day("2024-05-15"), TO, 0.07);

            PlantSharingObservation result = fixture.calculate();

            assertThat(agreementOf(result, never).unallocatedShare()).isNull();
            assertThat(rowOf(result, firstInNever).windowDays()).isZero();
            assertThat(rowOf(result, secondInNever).windowDays()).isZero();
            assertThat(rowOf(result, newcomerInA).observedDate()).isEqualTo(ObservedDate.exact(day("2024-05-15")));
            assertThat(rowOf(result, newcomerInA).windowDays()).isZero();
            // Not observed on its own, the row in B keeps B's whole window.
            assertThat(rowOf(result, newcomerInB).windowDays()).isEqualTo(52);
        }

        @Test
        @DisplayName("OBS-013 no row reports more observed valid days than window days, and a supply's data gap shows as fewer")
        void observedValidDaysNeverExceedWindowDays() {
            ObservationFixture fixture = new ObservationFixture(FROM, TO);
            ObservedSupply first = fixture.supply("S1");
            ObservedSupply gapped = fixture.supply("S2");
            ObservedSupply leaving = fixture.supply("S3");
            ObservedAgreement a = fixture.agreement("A", day("2024-03-01"));
            ObservedAgreement b = fixture.agreement("B", day("2024-05-01"));
            fixture.row(first, a, "0.5", day("2024-03-01"), CHANGE);
            fixture.row(gapped, a, "0.3", day("2024-03-01"), CHANGE);
            fixture.row(leaving, a, "0.2", day("2024-03-01"), CHANGE);
            ObservedCoefficient firstInB = fixture.row(first, b, "0.6", CHANGE, null);
            ObservedCoefficient gappedInB = fixture.row(gapped, b, "0.4", CHANGE, null);
            fixture.beta(first, FROM, day("2024-05-09"), 0.5).beta(first, CHANGE, TO, 0.6);
            fixture.beta(leaving, FROM, day("2024-05-09"), 0.2).beta(leaving, CHANGE, TO, 0.0);
            // S2 publishes nothing from 1 to 6 June: six days fewer than its window.
            fixture.beta(gapped, FROM, day("2024-05-09"), 0.3).beta(gapped, CHANGE, day("2024-05-31"), 0.4)
                    .beta(gapped, day("2024-06-07"), TO, 0.4);
            // And the plant has no data on 20 May: a day out of every window.
            fixture.noPlantData(day("2024-05-20"), day("2024-05-20"));

            PlantSharingObservation result = fixture.calculate();

            assertThat(rowOf(result, firstInB).windowDays()).isEqualTo(51);
            assertThat(rowOf(result, firstInB).observedBeta().validDays()).isEqualTo(51);
            assertThat(rowOf(result, gappedInB).windowDays()).isEqualTo(51);
            assertThat(rowOf(result, gappedInB).observedBeta().validDays()).isEqualTo(45);
            assertThat(result.agreements()).allSatisfy(observation -> {
                assertThat(observation.coefficients()).isNotEmpty().allSatisfy(row ->
                        assertThat(row.observedBeta().validDays()).isLessThanOrEqualTo(row.windowDays()));
                observation.leavingRows().forEach(row ->
                        assertThat(row.observedBeta().validDays()).isLessThanOrEqualTo(row.windowDays()));
            });
            assertThat(leavingsOf(result, b, leaving)).singleElement().satisfies(row ->
                    assertThat(row.observedBeta().validDays()).isEqualTo(row.windowDays()).isEqualTo(51));
        }

        @Test
        @DisplayName("OBS-013 a leaving row reports the plant-valid days from its own leaving date to the end of the agreement's window")
        void leavingRowWindowDays() {
            ObservationFixture fixture = new ObservationFixture(FROM, TO);
            ObservedSupply first = fixture.supply("S1");
            ObservedSupply second = fixture.supply("S2");
            ObservedSupply leaving = fixture.supply("S3");
            ObservedAgreement a = fixture.agreement("A", day("2024-03-01"));
            ObservedAgreement b = fixture.agreement("B", day("2024-05-01"));
            fixture.row(first, a, "0.55", day("2024-03-01"), CHANGE);
            fixture.row(second, a, "0.4", day("2024-03-01"), CHANGE);
            fixture.row(leaving, a, "0.05", day("2024-03-01"), CHANGE);
            fixture.row(first, b, "0.6", CHANGE, null);
            fixture.row(second, b, "0.4", CHANGE, null);
            fixture.beta(first, FROM, day("2024-05-09"), 0.55).beta(first, CHANGE, TO, 0.6);
            fixture.beta(second, FROM, TO, 0.4);
            // B is applied on 10 May, but S3 keeps receiving A's share until 19 May.
            fixture.beta(leaving, FROM, day("2024-05-19"), 0.05).beta(leaving, day("2024-05-20"), TO, 0.0);

            PlantSharingObservation result = fixture.calculate();

            assertThat(agreementOf(result, b).unallocatedShare().days()).isEqualTo(52);
            assertThat(leavingsOf(result, b, leaving)).singleElement().satisfies(row -> {
                assertThat(row.observedLeavingDate()).isEqualTo(ObservedDate.exact(day("2024-05-20")));
                assertThat(row.windowDays()).isEqualTo(42);
            });
            assertThat(result.sliceFor(b.id()).orElseThrow().agreement().leavingRows())
                    .extracting(LeavingObservation::windowDays)
                    .containsExactly(42);
        }

        @Test
        @DisplayName("OBS-013 the plant supply's not observable row reports the plant-valid days of its agreement's whole applied window")
        void notObservableRowWindowDays() {
            ObservationFixture fixture = new ObservationFixture(FROM, TO);
            ObservedSupply first = fixture.supply("S1");
            ObservedSupply second = fixture.supply("S2");
            ObservedSupply plant = fixture.plantSupply();
            ObservedAgreement a = fixture.agreement("A", day("2024-03-01"));
            ObservedAgreement b = fixture.agreement("B", day("2024-05-01"));
            fixture.row(first, a, "0.5", day("2024-03-01"), day("2024-05-01"));
            fixture.row(second, a, "0.49", day("2024-03-01"), day("2024-05-01"));
            ObservedCoefficient plantInA = fixture.row(plant, a, "0.01", (LocalDate) null, day("2024-03-15"));
            fixture.row(first, b, "0.6", day("2024-05-01"), null);
            fixture.row(second, b, "0.4", day("2024-05-01"), null);
            fixture.beta(first, FROM, day("2024-04-30"), 0.5).beta(first, day("2024-05-01"), TO, 0.6);
            fixture.beta(second, FROM, day("2024-04-30"), 0.49).beta(second, day("2024-05-01"), TO, 0.4);

            PlantSharingObservation result = fixture.calculate();

            assertThat(rowOf(result, plantInA).status()).isEqualTo(ObservationStatus.NOT_OBSERVABLE);
            assertThat(rowOf(result, plantInA).windowDays())
                    .isEqualTo(agreementOf(result, a).unallocatedShare().days())
                    .isEqualTo(30);
            assertThat(rowOf(result, plantInA).observedBeta()).isEqualTo(ObservedBeta.NONE);
        }

        @Test
        @DisplayName("OBS-013 reporting the window days changes no status, date, anomaly, recommendation, observed β or 1 − Σβ")
        void changesNothingElse() {
            assertNothingElseChanged(regressionFixture());
        }
    }

    @Nested
    class WindowDates {

        @Test
        @DisplayName("OBS-014 each agreement's unallocated share reports its applied window: up to the day before the next agreement's, the last one up to the end of the analysed range, none when never applied")
        void unallocatedShareReportsTheAppliedWindow() {
            CompleteRegression regression = completeRegressionFixture();

            PlantSharingObservation result = regression.fixture().calculate();

            // A starts on the open date of the first valid day; 15 and 16 May and 20 June are not plant-valid, and
            // do not move any bound.
            assertThat(List.of(regression.a(), regression.b(), regression.c()))
                    .extracting(agreement -> agreementOf(result, agreement).unallocatedShare())
                    .extracting(UnallocatedShare::from, UnallocatedShare::to, UnallocatedShare::days)
                    .containsExactly(
                            tuple(FROM, day("2024-05-09"), 39),
                            tuple(day("2024-05-10"), day("2024-05-31"), 20),
                            tuple(day("2024-06-01"), TO, 29));
            assertThat(agreementOf(result, regression.d()).unallocatedShare()).isNull();
        }

        @Test
        @DisplayName("OBS-014 an agreement's slice reports the same applied window and row windows as the plant's result")
        void sliceReportsTheSameWindows() {
            PlantSharingObservation result = completeRegressionFixture().fixture().calculate();

            assertThat(result.agreements()).allSatisfy(observation -> {
                AgreementObservation slice = result.sliceFor(observation.agreement().id()).orElseThrow().agreement();
                assertThat(slice.unallocatedShare()).isEqualTo(observation.unallocatedShare());
                assertThat(slice.coefficients())
                        .extracting(CoefficientObservation::coefficientId, CoefficientObservation::windowFrom,
                                CoefficientObservation::windowTo)
                        .containsExactlyElementsOf(observation.coefficients().stream()
                                .map(row -> tuple(row.coefficientId(), row.windowFrom(), row.windowTo()))
                                .toList());
                assertThat(slice.leavingRows())
                        .extracting(row -> row.supply().id(), LeavingObservation::windowFrom,
                                LeavingObservation::windowTo)
                        .containsExactlyElementsOf(observation.leavingRows().stream()
                                .map(row -> tuple(row.supply().id(), row.windowFrom(), row.windowTo()))
                                .toList());
            });
        }

        @Test
        @DisplayName("OBS-013 a row observed on or before a day earlier than its agreement's window starts there; a row without an observed date, a not observable one included, spans the whole window; a leaving row starts on its leaving date")
        void rowWindowDates() {
            LocalDate from = day("2024-04-01");
            LocalDate windowStart = day("2024-07-01");
            LocalDate leavingDay = day("2024-07-11");
            LocalDate to = day("2025-05-31");
            ObservationFixture fixture = new ObservationFixture(from, to);
            ObservedSupply plant = fixture.plantSupply();
            ObservedSupply early = fixture.supply("S1");
            ObservedSupply onTime = fixture.supply("S2");
            ObservedSupply alsoOnTime = fixture.supply("S3");
            ObservedSupply unchanged = fixture.supply("S4");
            ObservedSupply leaver = fixture.supply("S5");
            ObservedAgreement a = fixture.agreement("A", day("2024-03-01"));
            ObservedAgreement b = fixture.agreement("B", null);
            fixture.row(early, a, "0.05", day("2024-03-01"), windowStart);
            fixture.row(onTime, a, "0.02", day("2024-03-01"), windowStart);
            fixture.row(alsoOnTime, a, "0.04", day("2024-03-01"), windowStart);
            fixture.row(unchanged, a, "0.07", day("2024-03-01"), windowStart);
            fixture.row(leaver, a, "0.05", day("2024-03-01"), leavingDay);
            ObservedCoefficient plantInB = fixture.row(plant, b, "0.01", (LocalDate) null, null);
            ObservedCoefficient earlyInB = fixture.row(early, b, "0.10", windowStart, null);
            ObservedCoefficient onTimeInB = fixture.row(onTime, b, "0.03", windowStart, null);
            fixture.row(alsoOnTime, b, "0.06", windowStart, null);
            ObservedCoefficient unchangedInB = fixture.row(unchanged, b, "0.07", windowStart, null);
            // S1 receives B's value from the first day of the data: observed "on or before" it, 91 days before B's
            // window starts.
            fixture.beta(early, from, to, 0.10);
            fixture.beta(onTime, from, windowStart.minusDays(1), 0.02).beta(onTime, windowStart, to, 0.03);
            fixture.beta(alsoOnTime, from, windowStart.minusDays(1), 0.04).beta(alsoOnTime, windowStart, to, 0.06);
            fixture.beta(unchanged, from, to, 0.07);
            fixture.beta(leaver, from, leavingDay.minusDays(1), 0.05).beta(leaver, leavingDay, to, 0.0);

            PlantSharingObservation result = fixture.calculate();

            assertThat(agreementOf(result, a).unallocatedShare())
                    .extracting(UnallocatedShare::from, UnallocatedShare::to)
                    .containsExactly(from, windowStart.minusDays(1));
            assertThat(agreementOf(result, b).unallocatedShare())
                    .extracting(UnallocatedShare::from, UnallocatedShare::to, UnallocatedShare::days)
                    .containsExactly(windowStart, to, 335);
            assertThat(rowOf(result, earlyInB).observedDate()).isEqualTo(ObservedDate.onOrBefore(from));
            assertThat(List.of(earlyInB, onTimeInB, unchangedInB, plantInB))
                    .extracting(row -> rowOf(result, row))
                    .extracting(CoefficientObservation::status, CoefficientObservation::windowFrom,
                            CoefficientObservation::windowTo, CoefficientObservation::windowDays)
                    .containsExactly(
                            tuple(ObservationStatus.APPLIED_DIFFERENT_DATE, from, to, 426),
                            tuple(ObservationStatus.APPLIED_SAME_DATE, windowStart, to, 335),
                            tuple(ObservationStatus.NOT_DISTINGUISHABLE, windowStart, to, 335),
                            tuple(ObservationStatus.NOT_OBSERVABLE, windowStart, to, 335));
            assertThat(leavingsOf(result, b, leaver))
                    .extracting(LeavingObservation::observedLeavingDate, LeavingObservation::windowFrom,
                            LeavingObservation::windowTo, LeavingObservation::windowDays)
                    .containsExactly(tuple(ObservedDate.exact(leavingDay), leavingDay, to, 325));
        }

        @Test
        @DisplayName("OBS-013 a row without a row window reports no dates and 0 window days: its agreement was never observed, or the row starts after the window ends")
        void rowWithoutAWindowReportsNoDates() {
            ObservationFixture fixture = new ObservationFixture(FROM, TO);
            ObservedSupply first = fixture.supply("S1");
            ObservedSupply second = fixture.supply("S2");
            ObservedSupply newcomer = fixture.supply("S3");
            ObservedAgreement a = fixture.agreement("A", day("2024-03-01"));
            ObservedAgreement b = fixture.agreement("B", day("2024-05-01"));
            ObservedAgreement never = fixture.agreement("Never applied", null);
            LocalDate change = day("2024-05-10");
            fixture.row(first, a, "0.03", day("2024-03-01"), change);
            fixture.row(second, a, "0.02", day("2024-03-01"), change);
            ObservedCoefficient newcomerInA = fixture.row(newcomer, a, "0.07", day("2024-03-01"), change);
            fixture.row(first, b, "0.04", change, null);
            fixture.row(second, b, "0.05", change, null);
            fixture.row(newcomer, b, "0.07", change, null);
            ObservedCoefficient firstInNever = fixture.row(first, never, "0.5", (LocalDate) null, null);
            ObservedCoefficient secondInNever = fixture.row(second, never, "0.5", (LocalDate) null, null);
            fixture.beta(first, FROM, day("2024-05-09"), 0.03).beta(first, change, TO, 0.04);
            fixture.beta(second, FROM, day("2024-05-09"), 0.02).beta(second, change, TO, 0.05);
            // S3 receives nothing until 15 May, after A's window ends on 9 May.
            fixture.beta(newcomer, FROM, day("2024-05-14"), 0.0).beta(newcomer, day("2024-05-15"), TO, 0.07);

            PlantSharingObservation result = fixture.calculate();

            assertThat(rowOf(result, newcomerInA).observedDate()).isEqualTo(ObservedDate.exact(day("2024-05-15")));
            assertThat(List.of(firstInNever, secondInNever, newcomerInA))
                    .extracting(row -> rowOf(result, row))
                    .extracting(CoefficientObservation::windowFrom, CoefficientObservation::windowTo,
                            CoefficientObservation::windowDays)
                    .containsOnly(tuple(null, null, 0));
        }

        @Test
        @DisplayName("OBS-013 every row's window days are the plant-valid days between its window's first and last day, both included")
        void windowDaysAreThePlantValidDaysBetweenTheDates() {
            PlantSharingObservation result = completeRegressionFixture().fixture().calculate();
            // The days completeRegressionFixture leaves without plant data.
            Set<LocalDate> notPlantValid = Set.of(day("2024-05-15"), day("2024-05-16"), day("2024-06-20"));

            List<Object[]> windows = new ArrayList<>();
            result.agreements().forEach(observation -> {
                observation.coefficients().forEach(row ->
                        windows.add(new Object[]{row.windowFrom(), row.windowTo(), row.windowDays()}));
                observation.leavingRows().forEach(row ->
                        windows.add(new Object[]{row.windowFrom(), row.windowTo(), row.windowDays()}));
            });
            // Rows with a window in three agreements, and rows without one.
            assertThat(windows).filteredOn(window -> window[0] != null).extracting(window -> window[0])
                    .contains(FROM, day("2024-05-10"), day("2024-06-01"));
            assertThat(windows).filteredOn(window -> window[0] == null).isNotEmpty();
            assertThat(windows).allSatisfy(window -> {
                if (window[0] == null) {
                    assertThat(window[1]).isNull();
                    assertThat(window[2]).isEqualTo(0);
                    return;
                }
                LocalDate first = (LocalDate) window[0];
                LocalDate last = (LocalDate) window[1];
                long plantValidDays = first.datesUntil(last.plusDays(1))
                        .filter(day -> !notPlantValid.contains(day))
                        .count();
                assertThat(window[2]).isEqualTo((int) plantValidDays);
            });
        }

        @Test
        @DisplayName("OBS-014 OBS-013 reporting the window dates changes no status, date, anomaly, recommendation, observed β, window days or 1 − Σβ")
        void changesNothingElse() {
            assertCompleteRegressionUnchanged(completeRegressionFixture());
        }
    }

    /**
     * Asserts every status, date, anomaly, recommendation, observed β, window days and 1 − Σβ of
     * {@link #completeRegressionFixture()}, as the analysis reported them before the window dates were added (#618).
     */
    private static void assertCompleteRegressionUnchanged(CompleteRegression regression) {
        PlantSharingObservation result = regression.fixture().calculate();
        AgreementObservation a = agreementOf(result, regression.a());
        AgreementObservation b = agreementOf(result, regression.b());
        AgreementObservation c = agreementOf(result, regression.c());
        AgreementObservation d = agreementOf(result, regression.d());
        ObservedDate fromStart = ObservedDate.onOrBefore(FROM);
        ObservedDate tenMay = ObservedDate.exact(day("2024-05-10"));
        ObservedDate firstJune = ObservedDate.exact(day("2024-06-01"));

        // Rows are listed by supply code: PLANT, S1, S2, S3, S4.
        assertThat(a.coefficients()).extracting(row -> row.supply().code(), CoefficientObservation::status,
                        CoefficientObservation::observedDate, CoefficientObservation::proposedDate,
                        CoefficientObservation::observedBeta, CoefficientObservation::windowDays)
                .containsExactly(
                        tuple("S1", ObservationStatus.APPLIED_SAME_DATE, fromStart, null, beta("0.030000", 39), 39),
                        tuple("S2", ObservationStatus.APPLIED_SAME_DATE, fromStart, null, beta("0.020000", 39), 39),
                        tuple("S3", ObservationStatus.APPLIED_SAME_DATE, fromStart, null, beta("0.050000", 39), 39),
                        tuple("S4", ObservationStatus.NO_DATA, null, null, ObservedBeta.NONE, 39));
        assertThat(b.coefficients()).extracting(row -> row.supply().code(), CoefficientObservation::status,
                        CoefficientObservation::observedDate, CoefficientObservation::proposedDate,
                        CoefficientObservation::observedBeta, CoefficientObservation::windowDays)
                .containsExactly(
                        tuple("PLANT", ObservationStatus.NOT_OBSERVABLE, null, null, ObservedBeta.NONE, 20),
                        tuple("S1", ObservationStatus.APPLIED_SAME_DATE, tenMay, null, beta("0.040000", 20), 20),
                        tuple("S2", ObservationStatus.NOT_DISTINGUISHABLE, null, tenMay, beta("0.020000", 20), 20),
                        tuple("S3", ObservationStatus.APPLIED_SAME_DATE, tenMay, null, beta("0.060000", 20), 20),
                        tuple("S4", ObservationStatus.NO_DATA, null, null, ObservedBeta.NONE, 20));
        // S1's 3 % in C completes its run on the same day as A's 3 %, already observed: C, not yet observed, is
        // chosen, so nothing is reverted (OBS-001).
        assertThat(c.coefficients()).extracting(row -> row.supply().code(), CoefficientObservation::status,
                        CoefficientObservation::observedDate, CoefficientObservation::proposedDate,
                        CoefficientObservation::observedBeta, CoefficientObservation::windowDays)
                .containsExactly(
                        tuple("S1", ObservationStatus.APPLIED_DIFFERENT_DATE, firstJune, null, beta("0.030000", 29),
                                29),
                        tuple("S2", ObservationStatus.APPLIED_SAME_DATE, firstJune, null, beta("0.050000", 29), 29),
                        tuple("S4", ObservationStatus.NO_DATA, null, null, ObservedBeta.NONE, 29));
        assertThat(d.coefficients()).extracting(row -> row.supply().code(), CoefficientObservation::status,
                        CoefficientObservation::observedDate, CoefficientObservation::proposedDate,
                        CoefficientObservation::observedBeta, CoefficientObservation::windowDays)
                .containsExactly(
                        tuple("S1", ObservationStatus.PENDING_APPLICATION, null, null, ObservedBeta.NONE, 0),
                        tuple("S2", ObservationStatus.PENDING_APPLICATION, null, null, ObservedBeta.NONE, 0));

        // S3's implicit 0 in C completes its run on the same day as its implicit 0 in D: C, earlier in registered
        // order, is chosen (OBS-001).
        assertThat(a.leavingRows()).isEmpty();
        assertThat(b.leavingRows()).isEmpty();
        assertThat(c.leavingRows()).extracting(row -> row.supply().code(), row -> row.previousAgreement().id(),
                        LeavingObservation::observedLeavingDate, LeavingObservation::storedValidTo,
                        LeavingObservation::status, LeavingObservation::proposedDate, LeavingObservation::observedBeta,
                        LeavingObservation::windowDays)
                .containsExactly(tuple("S3", regression.b().id(), firstJune, day("2024-06-01"),
                        ObservationStatus.APPLIED_SAME_DATE, null, beta("0.000000", 29), 29));
        assertThat(d.leavingRows()).isEmpty();

        assertThat(List.of(a, b, c, d)).extracting(AgreementObservation::earliestObservedDate,
                        AgreementObservation::mostFrequentObservedDate)
                .containsExactly(tuple(fromStart, fromStart), tuple(tenMay, tenMay), tuple(firstJune, firstJune),
                        tuple(null, null));

        assertThat(a.anomalies()).isEmpty();
        assertThat(b.anomalies()).extracting(anomaly -> anomaly.supply().code(), SharingAnomaly::type,
                        SharingAnomaly::start, SharingAnomaly::end, SharingAnomaly::observedBeta,
                        SharingAnomaly::expectedBeta)
                .containsExactly(tuple("S1", SharingAnomalyType.UNKNOWN_COEFFICIENT,
                        ObservedDate.exact(day("2024-05-20")), day("2024-05-24"), beta("0.025000", 5),
                        new BigDecimal("0.040000")));
        assertThat(c.anomalies()).extracting(anomaly -> anomaly.supply().code(), SharingAnomaly::type,
                        SharingAnomaly::start, SharingAnomaly::end, SharingAnomaly::observedBeta,
                        SharingAnomaly::expectedBeta)
                .containsExactly(tuple("S2", SharingAnomalyType.NO_ENERGY_IN_AGREEMENT,
                        ObservedDate.exact(day("2024-06-10")), day("2024-06-13"), beta("0.000000", 4),
                        new BigDecimal("0.050000")));
        assertThat(d.anomalies()).isEmpty();
        assertThat(result.unattributedAnomalies()).extracting(anomaly -> anomaly.supply().code(),
                        SharingAnomaly::type, SharingAnomaly::start, SharingAnomaly::end, SharingAnomaly::observedBeta,
                        SharingAnomaly::expectedBeta)
                .containsExactly(tuple("S5", SharingAnomalyType.ENERGY_OUTSIDE_AGREEMENT,
                        ObservedDate.exact(day("2024-05-01")), day("2024-05-20"), beta("0.020000", 18), null));

        assertThat(a.recommendations()).containsExactly(SharingRecommendation.NONE);
        assertThat(b.recommendations())
                .containsExactly(SharingRecommendation.SET_DATES, SharingRecommendation.REVIEW_ANOMALIES);
        assertThat(c.recommendations())
                .containsExactly(SharingRecommendation.SET_DATES, SharingRecommendation.REVIEW_ANOMALIES);
        assertThat(d.recommendations()).containsExactly(SharingRecommendation.NONE);

        // A: 39 days of 3 % + 2 % + 5 %, and 2 % to S5 on 1-9 May; S4 without data. B: 20 days (15 and 16 May are
        // not plant-valid), S1 at 2.5 % on 5 of them, and 2 % to S5 on 9; S4 and the plant supply without data.
        // C: 29 days (20 June is not plant-valid), S2 without energy on 4 of them, S3 gone; S4 without data.
        Offset<Double> precision = Offset.offset(1e-9);
        assertThat(a.unallocatedShare().unallocated()).isCloseTo(1 - (39 * 0.10 + 9 * 0.02) / 39, precision);
        assertThat(a.unallocatedShare().registeredShareWithoutData()).isCloseTo(0.1, precision);
        assertThat(a.unallocatedShare().days()).isEqualTo(39);
        assertThat(b.unallocatedShare().unallocated())
                .isCloseTo(1 - (15 * 0.04 + 5 * 0.025 + 20 * 0.02 + 20 * 0.06 + 9 * 0.02) / 20, precision);
        assertThat(b.unallocatedShare().registeredShareWithoutData()).isCloseTo(0.11, precision);
        assertThat(b.unallocatedShare().days()).isEqualTo(20);
        assertThat(c.unallocatedShare().unallocated()).isCloseTo(1 - (29 * 0.03 + 25 * 0.05) / 29, precision);
        assertThat(c.unallocatedShare().registeredShareWithoutData()).isCloseTo(0.1, precision);
        assertThat(c.unallocatedShare().days()).isEqualTo(29);
        assertThat(d.unallocatedShare()).isNull();
    }

    /**
     * Four agreements, A, B from 10 May, C from 1 June and D sent but never applied, over six supplies, with every
     * kind of row: applied on its stored date or on another one, not distinguishable, pending, without data, not
     * observable, and a leaving row; an unknown coefficient, no energy in an agreement and energy outside the
     * agreements; and two pairs of candidates completing a run on the same day. 15 and 16 May and 20 June are not
     * plant-valid.
     */
    private static CompleteRegression completeRegressionFixture() {
        LocalDate tenMay = day("2024-05-10");
        LocalDate firstJune = day("2024-06-01");
        ObservationFixture fixture = new ObservationFixture(FROM, TO)
                .noPlantData(day("2024-05-15"), day("2024-05-16"))
                .noPlantData(day("2024-06-20"), day("2024-06-20"));
        ObservedSupply plant = fixture.plantSupply();
        ObservedSupply s1 = fixture.supply("S1");
        ObservedSupply s2 = fixture.supply("S2");
        ObservedSupply s3 = fixture.supply("S3");
        ObservedSupply s4 = fixture.supply("S4");
        ObservedSupply s5 = fixture.supply("S5");
        ObservedAgreement a = fixture.agreement("A", day("2024-03-01"));
        ObservedAgreement b = fixture.agreement("B", day("2024-05-01"));
        ObservedAgreement c = fixture.agreement("C", firstJune);
        ObservedAgreement d = fixture.agreement("D", day("2024-06-25"));
        fixture.row(s1, a, "0.03", day("2024-03-01"), tenMay);
        fixture.row(s2, a, "0.02", day("2024-03-01"), tenMay);
        fixture.row(s3, a, "0.05", day("2024-03-01"), tenMay);
        fixture.row(s4, a, "0.1", day("2024-03-01"), tenMay);
        fixture.row(plant, b, "0.01", day("2024-05-20"), null);
        fixture.row(s1, b, "0.04", tenMay, firstJune);
        fixture.row(s2, b, "0.02", tenMay, firstJune);
        fixture.row(s3, b, "0.06", tenMay, firstJune);
        fixture.row(s4, b, "0.1", tenMay, firstJune);
        // S1 back to A's value in C, stored on another day than observed.
        fixture.row(s1, c, "0.03", day("2024-05-25"), null);
        fixture.row(s2, c, "0.05", firstJune, null);
        fixture.row(s4, c, "0.1", firstJune, null);
        fixture.row(s1, d, "0.07", (LocalDate) null, null);
        fixture.row(s2, d, "0.08", (LocalDate) null, null);
        fixture.beta(s1, FROM, day("2024-05-09"), 0.03).beta(s1, tenMay, day("2024-05-31"), 0.04)
                .beta(s1, day("2024-05-20"), day("2024-05-24"), 0.025).beta(s1, firstJune, TO, 0.03);
        fixture.beta(s2, FROM, day("2024-05-31"), 0.02).beta(s2, firstJune, TO, 0.05)
                .beta(s2, day("2024-06-10"), day("2024-06-13"), 0.0);
        fixture.beta(s3, FROM, day("2024-05-09"), 0.05).beta(s3, tenMay, day("2024-05-31"), 0.06)
                .beta(s3, firstJune, TO, 0.0);
        fixture.beta(s5, FROM, TO, 0.0).beta(s5, day("2024-05-01"), day("2024-05-20"), 0.02);
        return new CompleteRegression(fixture, a, b, c, d);
    }

    private static ObservedBeta beta(String value, int validDays) {
        return new ObservedBeta(new BigDecimal(value), validDays);
    }

    private record CompleteRegression(ObservationFixture fixture, ObservedAgreement a, ObservedAgreement b,
                                      ObservedAgreement c, ObservedAgreement d) {
    }

    /**
     * Asserts every status, date, anomaly, recommendation, observed β and 1 − Σβ of {@link #regressionFixture()}
     * (OBS-012, OBS-013): what the analysis reported before the expected β and the window days were added.
     */
    private static void assertNothingElseChanged(Regression regression) {
        PlantSharingObservation result = regression.fixture().calculate();
        AgreementObservation a = result.agreements().get(0);
        AgreementObservation b = result.agreements().get(1);
        ObservedSupply first = regression.first();
        ObservedSupply outsider = regression.outsider();

        assertThat(a.coefficients()).extracting(CoefficientObservation::status, CoefficientObservation::observedDate,
                        CoefficientObservation::proposedDate, CoefficientObservation::observedBeta)
                .containsExactly(
                        tuple(ObservationStatus.APPLIED_SAME_DATE, ObservedDate.onOrBefore(FROM), null,
                                new ObservedBeta(new BigDecimal("0.030000"), 39)),
                        tuple(ObservationStatus.APPLIED_SAME_DATE, ObservedDate.onOrBefore(FROM), null,
                                new ObservedBeta(new BigDecimal("0.020000"), 39)));
        assertThat(b.coefficients()).extracting(CoefficientObservation::status, CoefficientObservation::observedDate,
                        CoefficientObservation::proposedDate, CoefficientObservation::observedBeta)
                .containsExactly(
                        tuple(ObservationStatus.APPLIED_SAME_DATE, ObservedDate.exact(REGRESSION_CHANGE), null,
                                new ObservedBeta(new BigDecimal("0.040000"), 52)),
                        tuple(ObservationStatus.APPLIED_SAME_DATE, ObservedDate.exact(REGRESSION_CHANGE), null,
                                new ObservedBeta(new BigDecimal("0.050000"), 52)));
        assertThat(a.mostFrequentObservedDate()).isEqualTo(ObservedDate.onOrBefore(FROM));
        assertThat(b.mostFrequentObservedDate()).isEqualTo(ObservedDate.exact(REGRESSION_CHANGE));

        assertThat(b.anomalies()).extracting(SharingAnomaly::id, SharingAnomaly::type, SharingAnomaly::start,
                        SharingAnomaly::end, SharingAnomaly::attributedAgreementId, SharingAnomaly::observedBeta)
                .containsExactly(tuple(
                        AnomalyCollector.idOf(first.id(), SharingAnomalyType.UNKNOWN_COEFFICIENT, day("2024-06-01")),
                        SharingAnomalyType.UNKNOWN_COEFFICIENT, ObservedDate.exact(day("2024-06-01")),
                        day("2024-06-05"), b.agreement().id(), new ObservedBeta(new BigDecimal("0.025000"), 5)));
        assertThat(a.anomalies()).isEmpty();
        assertThat(result.unattributedAnomalies()).extracting(SharingAnomaly::id, SharingAnomaly::type,
                        SharingAnomaly::start, SharingAnomaly::end, SharingAnomaly::attributedAgreementId,
                        SharingAnomaly::observedBeta)
                .containsExactly(tuple(
                        AnomalyCollector.idOf(outsider.id(), SharingAnomalyType.ENERGY_OUTSIDE_AGREEMENT,
                                day("2024-05-01")),
                        SharingAnomalyType.ENERGY_OUTSIDE_AGREEMENT, ObservedDate.exact(day("2024-05-01")),
                        day("2024-05-20"), null, new ObservedBeta(new BigDecimal("0.020000"), 20)));

        assertThat(a.recommendations()).containsExactly(SharingRecommendation.NONE);
        assertThat(b.recommendations()).containsExactly(SharingRecommendation.REVIEW_ANOMALIES);

        // A: 39 days of 3 % + 2 %, and 2 % to the outsider on 1-9 May. B: 52 days, 5 of them at 2.5 % for S1,
        // and 2 % to the outsider on 10-20 May.
        assertThat(a.unallocatedShare().unallocated()).isCloseTo(1 - (39 * 0.05 + 9 * 0.02) / 39, Offset.offset(1e-9));
        assertThat(a.unallocatedShare().registeredShareWithoutData()).isCloseTo(0, Offset.offset(1e-9));
        assertThat(a.unallocatedShare().days()).isEqualTo(39);
        assertThat(b.unallocatedShare().unallocated())
                .isCloseTo(1 - (47 * 0.04 + 5 * 0.025 + 52 * 0.05 + 11 * 0.02) / 52, Offset.offset(1e-9));
        assertThat(b.unallocatedShare().registeredShareWithoutData()).isCloseTo(0, Offset.offset(1e-9));
        assertThat(b.unallocatedShare().days()).isEqualTo(52);
    }

    /**
     * Two supplies moving from A to B on 10 May, the first with an unknown coefficient in B, and a third in no
     * agreement receiving energy: one attributed and one unattributed anomaly.
     */
    private static Regression regressionFixture() {
        ObservationFixture fixture = new ObservationFixture(FROM, TO);
        ObservedSupply first = fixture.supply("S1");
        ObservedSupply second = fixture.supply("S2");
        ObservedSupply outsider = fixture.supply("S3");
        ObservedAgreement a = fixture.agreement("A", day("2024-03-01"));
        ObservedAgreement b = fixture.agreement("B", day("2024-05-01"));
        fixture.row(first, a, "0.03", day("2024-03-01"), REGRESSION_CHANGE);
        fixture.row(second, a, "0.02", day("2024-03-01"), REGRESSION_CHANGE);
        fixture.row(first, b, "0.04", REGRESSION_CHANGE, null);
        fixture.row(second, b, "0.05", REGRESSION_CHANGE, null);
        fixture.beta(first, FROM, day("2024-05-09"), 0.03).beta(first, REGRESSION_CHANGE, TO, 0.04)
                .beta(first, day("2024-06-01"), day("2024-06-05"), 0.025);
        fixture.beta(second, FROM, day("2024-05-09"), 0.02).beta(second, REGRESSION_CHANGE, TO, 0.05);
        fixture.beta(outsider, FROM, TO, 0.0).beta(outsider, day("2024-05-01"), day("2024-05-20"), 0.02);
        return new Regression(fixture, first, outsider);
    }

    private record Regression(ObservationFixture fixture, ObservedSupply first, ObservedSupply outsider) {
    }
}

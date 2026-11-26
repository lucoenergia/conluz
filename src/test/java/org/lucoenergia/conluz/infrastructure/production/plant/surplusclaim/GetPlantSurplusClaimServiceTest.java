package org.lucoenergia.conluz.infrastructure.production.plant.surplusclaim;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SaveSupplyPartitionCoefficientRepository;
import org.lucoenergia.conluz.domain.production.plant.PlantNotFoundException;
import org.lucoenergia.conluz.domain.production.plant.surplusclaim.GetPlantSurplusClaimService;
import org.lucoenergia.conluz.domain.production.plant.surplusclaim.PlantSurplusClaim;
import org.lucoenergia.conluz.domain.production.plant.surplusclaim.SurplusClaimDay;
import org.lucoenergia.conluz.domain.production.plant.surplusclaim.SurplusClaimHour;
import org.lucoenergia.conluz.domain.production.plant.surplusclaim.SurplusClaimMonthNotClosedException;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreement;
import org.lucoenergia.conluz.domain.production.sharingagreement.create.CreateSharingAgreementRepository;
import org.lucoenergia.conluz.infrastructure.admin.community.energy.CommunityEnergyTestFixture;
import org.lucoenergia.conluz.infrastructure.production.plant.surplusclaim.SurplusClaimTestData.PlantWithSupplies;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.lucoenergia.conluz.infrastructure.shared.time.MutableClock;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lucoenergia.conluz.infrastructure.production.plant.surplusclaim.SurplusClaimTestData.MADRID;

/**
 * The claim read from a real PostgreSQL and InfluxDB. The clock is at 2026-10-31T23:30Z, already 1 November 2026 in
 * Madrid though still October in UTC, so October 2026 is the last closed month. The claimed month is September 2026
 * unless a test says otherwise.
 */
class GetPlantSurplusClaimServiceTest extends BaseIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-10-31T23:30:00Z");
    private static final YearMonth SEPTEMBER = YearMonth.of(2026, 9);
    private static final double TOLERANCE = 1e-6;

    @Autowired
    private GetPlantSurplusClaimService service;
    @Autowired
    private CommunityEnergyTestFixture fixture;
    @Autowired
    private CreateSharingAgreementRepository createSharingAgreementRepository;
    @Autowired
    private SaveSupplyPartitionCoefficientRepository saveSupplyPartitionCoefficientRepository;
    @Autowired
    private MutableClock clock;

    private SurplusClaimTestData data;

    @BeforeEach
    void setUp() {
        // Forwards from the instant every test starts at.
        clock.setInstant(NOW);
        data = new SurplusClaimTestData(fixture, createSharingAgreementRepository,
                saveSupplyPartitionCoefficientRepository);
    }

    @AfterEach
    void tearDown() {
        data.clear();
    }

    @Test
    @DisplayName("SCL-002 AC1 two supplies in scope with 2 kWh and 3 kWh of surplus in the same hour make an hour of 5 kWh")
    void twoSuppliesSurplusInTheSameHourIsSummed() {
        PlantWithSupplies plant = plantSharedByTwo();
        LocalDateTime noon = LocalDateTime.of(2026, 9, 10, 12, 0);
        data.surplus(plant.first(), noon, 2f);
        data.surplus(plant.second(), noon, 3f);

        PlantSurplusClaim claim = service.getSurplusClaim(plant.plant().getId(), SEPTEMBER);

        assertEquals(5d, hour(claim, noon).surplusKWh(), TOLERANCE);
        assertEquals(5d, claim.summary().surplusKWh(), TOLERANCE);
    }

    @Test
    @DisplayName("SCL-002 AC4 an hour only one supply has a record for, carrying 0, is 0, and an hour no supply has a record for is null")
    void aZeroRecordIsZeroAndAMissingRecordIsNull() {
        PlantWithSupplies plant = plantSharedByTwo();
        data.surplus(plant.first(), LocalDateTime.of(2026, 9, 10, 10, 0), 0f);
        data.surplus(plant.second(), LocalDateTime.of(2026, 9, 10, 12, 0), 1.25f);

        PlantSurplusClaim claim = service.getSurplusClaim(plant.plant().getId(), SEPTEMBER);

        assertEquals(0d, hour(claim, LocalDateTime.of(2026, 9, 10, 10, 0)).surplusKWh());
        assertNull(hour(claim, LocalDateTime.of(2026, 9, 10, 11, 0)).surplusKWh());
        assertEquals(1.25d, hour(claim, LocalDateTime.of(2026, 9, 10, 12, 0)).surplusKWh(), TOLERANCE);
    }

    @Test
    @DisplayName("SCL-001 AC2 the plant's own supply is not in scope, and SCL-011 its coefficient of 0.05 is reported as plantSupplyShare")
    void thePlantsOwnSupplyIsNotInScope() {
        PlantWithSupplies plant = plantSharedByTwo();
        SharingAgreement agreement = data.agreement(plant.plant());
        data.coefficient(plant.plant().getSupply(), plant.plant(), agreement, "0.05", LocalDate.of(2026, 1, 1),
                null);
        LocalDateTime noon = LocalDateTime.of(2026, 9, 10, 12, 0);
        data.surplus(plant.first(), noon, 2f);
        data.surplus(plant.plant().getSupply(), noon, 50f);

        PlantSurplusClaim claim = service.getSurplusClaim(plant.plant().getId(), SEPTEMBER);

        assertEquals(2, claim.summary().suppliesInScope());
        assertEquals(2d, hour(claim, noon).surplusKWh(), TOLERANCE);
        assertEquals(0, new BigDecimal("0.05").compareTo(claim.warnings().plantSupplyShare()));
        assertTrue(claim.warnings().suppliesWithoutData().stream()
                .noneMatch(supply -> supply.supply().id().equals(plant.plant().getSupply().getId())));
    }

    @Test
    @DisplayName("SCL-001 AC3 a coefficient that ended before the month, or that is still pending, puts no supply in scope; one starting mid-month counts the whole month")
    void onlyTheCoefficientsAppliedInTheMonthPutASupplyInScope() {
        PlantWithSupplies plant = data.plantWithSupplies(4);
        SharingAgreement agreement = data.agreement(plant.plant());
        Supply ended = plant.supplies().get(0);
        Supply pending = plant.supplies().get(1);
        Supply startsMidMonth = plant.supplies().get(2);
        Supply whole = plant.supplies().get(3);
        data.coefficient(ended, plant.plant(), agreement, "0.3", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 9, 1));
        data.coefficient(pending, plant.plant(), agreement, "0.3", null, null);
        data.coefficient(startsMidMonth, plant.plant(), agreement, "0.3", LocalDate.of(2026, 9, 20), null);
        data.coefficient(whole, plant.plant(), agreement, "0.7", LocalDate.of(2026, 1, 1), null);
        LocalDateTime noon = LocalDateTime.of(2026, 9, 5, 12, 0);
        data.surplus(ended, noon, 10f);
        data.surplus(pending, noon, 20f);
        data.surplus(startsMidMonth, noon, 1f);
        data.surplus(whole, noon, 2f);

        PlantSurplusClaim claim = service.getSurplusClaim(plant.plant().getId(), SEPTEMBER);

        assertEquals(2, claim.summary().suppliesInScope());
        assertEquals(3d, hour(claim, noon).surplusKWh(), TOLERANCE);
    }

    @Test
    @DisplayName("SCL-006 AC11 an hour of 2 and 3 kWh at quarter-hour prices of 0.04 to 0.10 €/kWh adds 0.35 €, and an hour without a price is unpriced")
    void theIncomePricesEachHourAtItsMeanPrice() {
        PlantWithSupplies plant = plantSharedByTwo();
        LocalDateTime priced = LocalDateTime.of(2025, 9, 15, 14, 0);
        LocalDateTime unpriced = LocalDateTime.of(2025, 9, 15, 15, 0);
        data.surplus(plant.first(), priced, 2f);
        data.surplus(plant.second(), priced, 3f);
        data.surplus(plant.first(), unpriced, 1.5f);
        data.price(priced, 0.04);
        data.price(priced.plusMinutes(15), 0.06);
        data.price(priced.plusMinutes(30), 0.08);
        data.price(priced.plusMinutes(45), 0.10);

        PlantSurplusClaim claim = service.getSurplusClaim(plant.plant().getId(), YearMonth.of(2025, 9));

        assertEquals(0, new BigDecimal("0.35").compareTo(
                claim.summary().surplusIncome().getAmountEur().setScale(2, RoundingMode.HALF_UP)));
        assertEquals(1.5d, claim.summary().surplusIncome().getUnpricedSurplusKWh(), TOLERANCE);
        assertEquals(6.5d, claim.summary().surplusKWh(), TOLERANCE);
    }

    @Test
    @DisplayName("SCL-007 AC12 without a month, the latest published month is claimed")
    void withoutAMonthTheLatestPublishedMonthIsClaimed() {
        PlantWithSupplies plant = plantSharedByTwo();
        data.zeroSurplusAllMonth(plant.first(), SEPTEMBER);

        PlantSurplusClaim claim = service.getSurplusClaim(plant.plant().getId(), null);

        assertEquals(SEPTEMBER, claim.month());
        assertEquals(SEPTEMBER, claim.availableMonths().latestPublishedMonth());
    }

    @Test
    @DisplayName("SCL-007 AC12 without a month and without a published month, the last closed month is claimed")
    void withoutAMonthOrAPublishedMonthTheLastClosedMonthIsClaimed() {
        PlantWithSupplies plant = plantSharedByTwo();

        PlantSurplusClaim claim = service.getSurplusClaim(plant.plant().getId(), null);

        assertEquals(YearMonth.of(2026, 10), claim.month());
        assertNull(claim.availableMonths().latestPublishedMonth());
    }

    @Test
    @DisplayName("SCL-007 AC13 the current month and a later one are refused, in the zone of the plant's community")
    void theCurrentMonthAndLaterOnesAreRefused() {
        PlantWithSupplies plant = plantSharedByTwo();
        UUID plantId = plant.plant().getId();

        // November has begun in Madrid, not yet in UTC.
        SurplusClaimMonthNotClosedException current = assertThrows(SurplusClaimMonthNotClosedException.class,
                () -> service.getSurplusClaim(plantId, YearMonth.of(2026, 11)));
        assertEquals(YearMonth.of(2026, 11), current.getCurrentMonth());
        assertThrows(SurplusClaimMonthNotClosedException.class,
                () -> service.getSurplusClaim(plantId, YearMonth.of(2027, 1)));
        assertEquals(YearMonth.of(2026, 10), service.getSurplusClaim(plantId, YearMonth.of(2026, 10)).month());
    }

    @Test
    @DisplayName("SCL-007 a closed month outside the selectable range is answered, its warnings saying why it is empty")
    void aClosedMonthOutsideTheRangeIsAnswered() {
        PlantWithSupplies plant = plantSharedByTwo();

        PlantSurplusClaim claim = service.getSurplusClaim(plant.plant().getId(), YearMonth.of(2024, 3));

        assertEquals(YearMonth.of(2024, 3), claim.month());
        assertTrue(claim.warnings().noAppliedCoefficients());
        assertNull(claim.summary().surplusKWh());
    }

    @Test
    @DisplayName("SCL-008 the selectable range runs from the month of the earliest applied coefficient to the latest published month")
    void theSelectableRangeRunsFromTheEarliestAppliedCoefficient() {
        PlantWithSupplies plant = data.plantWithSupplies(2);
        SharingAgreement agreement = data.agreement(plant.plant());
        // The plant supply's coefficient is the plant's earliest applied one.
        data.coefficient(plant.plant().getSupply(), plant.plant(), agreement, "0.1", LocalDate.of(2025, 2, 10),
                null);
        data.coefficient(plant.first(), plant.plant(), agreement, "0.5", LocalDate.of(2025, 3, 1), null);
        // Ended before the claimed month: still a supply that held one, so its publication counts.
        data.coefficient(plant.second(), plant.plant(), agreement, "0.4", LocalDate.of(2025, 3, 1),
                LocalDate.of(2026, 6, 1));
        data.zeroSurplusAllMonth(plant.second(), YearMonth.of(2026, 8));

        PlantSurplusClaim claim = service.getSurplusClaim(plant.plant().getId(), SEPTEMBER);

        assertEquals(YearMonth.of(2025, 2), claim.availableMonths().firstMonth());
        assertEquals(YearMonth.of(2026, 8), claim.availableMonths().latestPublishedMonth());
    }

    @Test
    @DisplayName("SCL-008 a plant without an applied coefficient has neither bound, a pending one included")
    void aPlantWithoutAnAppliedCoefficientHasNeitherBound() {
        PlantWithSupplies plant = data.plantWithSupplies(2);
        SharingAgreement agreement = data.agreement(plant.plant());
        data.coefficient(plant.first(), plant.plant(), agreement, "1", null, null);
        data.zeroSurplusAllMonth(plant.first(), SEPTEMBER);

        PlantSurplusClaim claim = service.getSurplusClaim(plant.plant().getId(), null);

        assertNull(claim.availableMonths().firstMonth());
        assertNull(claim.availableMonths().latestPublishedMonth());
        assertEquals(YearMonth.of(2026, 10), claim.month());
    }

    @Test
    @DisplayName("SCL-013 an unknown plant is not found")
    void anUnknownPlantIsNotFound() {
        assertThrows(PlantNotFoundException.class, () -> service.getSurplusClaim(UUID.randomUUID(), SEPTEMBER));
    }

    /**
     * A plant whose first two supplies hold applied coefficients of 0.6 and 0.4 since 2025-01-01.
     */
    private PlantWithSupplies plantSharedByTwo() {
        PlantWithSupplies plant = data.plantWithSupplies(2);
        SharingAgreement agreement = data.agreement(plant.plant());
        data.coefficient(plant.first(), plant.plant(), agreement, "0.6", LocalDate.of(2025, 1, 1), null);
        data.coefficient(plant.second(), plant.plant(), agreement, "0.4", LocalDate.of(2025, 1, 1), null);
        return plant;
    }

    private static SurplusClaimHour hour(PlantSurplusClaim claim, LocalDateTime localHour) {
        Instant startsAt = localHour.atZone(MADRID).toInstant();
        SurplusClaimDay day = claim.days().stream()
                .filter(candidate -> candidate.date().equals(localHour.toLocalDate()))
                .findFirst().orElseThrow();
        return day.hours().stream().filter(hour -> hour.startsAt().equals(startsAt)).findFirst().orElseThrow();
    }
}

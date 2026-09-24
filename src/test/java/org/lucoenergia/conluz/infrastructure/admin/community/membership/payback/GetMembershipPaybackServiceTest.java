package org.lucoenergia.conluz.infrastructure.admin.community.membership.payback;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.MembershipNotFoundException;
import org.lucoenergia.conluz.domain.admin.community.membership.GetMembershipsRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.payback.GetMembershipPaybackService;
import org.lucoenergia.conluz.domain.admin.community.membership.payback.MembershipPayback;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.GetSupplyPartitionCoefficientRepository;
import org.lucoenergia.conluz.domain.admin.supply.tariff.TariffSource;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.consumption.SupplySavings;
import org.lucoenergia.conluz.domain.consumption.savings.SupplySavingsCalculator;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.domain.shared.time.ClockProvider;
import org.lucoenergia.conluz.domain.shared.time.ZoneResolver;
import org.mockito.ArgumentMatcher;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Covers what the service itself decides: the period to price, the summation across the member's
 * supplies, and the provenance of the total. The arithmetic on top of those figures belongs to
 * {@code MembershipPaybackTest}.
 *
 * <p>The clock is fixed, so the elapsed days -- and therefore every figure derived from them --
 * are the same on every run.
 */
class GetMembershipPaybackServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Madrid");
    private static final UUID COMMUNITY_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    /** 2025-01-01 local in Madrid; the community's first activation. */
    private static final Instant START = Instant.parse("2024-12-31T23:00:00Z");
    /** 100 civil days later, 2025-04-11 local. */
    private static final Instant NOW = Instant.parse("2025-04-11T10:00:00Z");

    private final GetMembershipsRepository getMembershipsRepository = mock(GetMembershipsRepository.class);
    private final GetSupplyPartitionCoefficientRepository coefficientRepository =
            mock(GetSupplyPartitionCoefficientRepository.class);
    private final GetSupplyRepository getSupplyRepository = mock(GetSupplyRepository.class);
    private final SupplySavingsCalculator savingsCalculator = mock(SupplySavingsCalculator.class);
    private final ZoneResolver zoneResolver = mock(ZoneResolver.class);
    private final ClockProvider clockProvider = mock(ClockProvider.class);

    private final GetMembershipPaybackService service = new GetMembershipPaybackServiceImpl(
            getMembershipsRepository, coefficientRepository, getSupplyRepository, savingsCalculator,
            zoneResolver, clockProvider);

    @BeforeEach
    void givenAFixedClockAndZone() {
        when(clockProvider.now()).thenReturn(NOW);
        when(zoneResolver.resolveZoneIdForCommunity(COMMUNITY_ID)).thenReturn(ZONE);
    }

    @Test
    void aMembershipThatDoesNotExistIsReportedAsNotFoundWithoutPricingAnything() {
        when(getMembershipsRepository.findByUserIdAndCommunityId(USER_ID, COMMUNITY_ID))
                .thenReturn(Optional.empty());

        assertThrows(MembershipNotFoundException.class, () -> service.getPayback(COMMUNITY_ID, USER_ID));

        verifyNoInteractions(coefficientRepository);
        verifyNoInteractions(savingsCalculator);
    }

    // --- rule 3: savedEur sums the member's supplies over [startDate, now) ---

    /**
     * Two supplies with different amounts: neither the sum nor the selection could be satisfied by
     * reading one of them.
     */
    @Test
    void savingsAreSummedAcrossEverySupplyTheMemberOwnsInTheCommunity() {
        givenMembershipWithInvestment("1000.00");
        givenCommunityStartedSharing();
        Supply first = SupplyMother.random().build();
        Supply second = SupplyMother.random().build();
        givenSupplies(first, second);
        givenSavings(first, "120.50", TariffSource.ESTIMATE);
        givenSavings(second, "80.25", TariffSource.ESTIMATE);

        MembershipPayback payback = service.getPayback(COMMUNITY_ID, USER_ID);

        assertEquals(0, new BigDecimal("200.75").compareTo(payback.getSavedEur()));
    }

    /**
     * Each supply is priced over the community's whole sharing period, from the activation instant
     * up to but not including now.
     */
    @Test
    void everySupplyIsPricedOverTheCommunitySharingPeriod() {
        givenMembershipWithInvestment("1000.00");
        givenCommunityStartedSharing();
        Supply first = SupplyMother.random().build();
        Supply second = SupplyMother.random().build();
        givenSupplies(first, second);
        givenSavings(first, "10.00", TariffSource.ESTIMATE);
        givenSavings(second, "10.00", TariffSource.ESTIMATE);

        service.getPayback(COMMUNITY_ID, USER_ID);

        verify(savingsCalculator).estimate(first, START, NOW);
        verify(savingsCalculator).estimate(second, START, NOW);
    }

    /**
     * Zero, not absent: the period exists and the member took nothing from it, which is a
     * different statement from the community never having shared at all.
     */
    @Test
    void aMemberWithNoSuppliesHasSavedZero() {
        givenMembershipWithInvestment("1000.00");
        givenCommunityStartedSharing();
        givenSupplies();

        MembershipPayback payback = service.getPayback(COMMUNITY_ID, USER_ID);

        assertEquals(0, BigDecimal.ZERO.compareTo(payback.getSavedEur()));
        assertEquals(0, new BigDecimal("1000.00").compareTo(payback.getRemainingEur()));
        // No rate, so no estimate.
        assertNull(payback.getEstimatedRemainingMonths());
    }

    /**
     * Without an activated coefficient there is no period to price, so the amount is absent rather
     * than zero, and no supply is even looked up.
     */
    @Test
    void aCommunityThatNeverSharedHasNoPeriodToPrice() {
        givenMembershipWithInvestment("1000.00");
        when(coefficientRepository.findEarliestValidFromByCommunityId(COMMUNITY_ID))
                .thenReturn(Optional.empty());

        MembershipPayback payback = service.getPayback(COMMUNITY_ID, USER_ID);

        assertNull(payback.getStartDate());
        assertNull(payback.getSavedEur());
        assertNull(payback.getRemainingEur());
        assertNull(payback.getProgressRatio());
        assertNull(payback.getEstimatedRemainingMonths());
        // Still reported, because it is recorded independently of any sharing.
        assertEquals(0, new BigDecimal("1000.00").compareTo(payback.getInvestmentEur()));
        verifyNoInteractions(savingsCalculator);
        verify(getSupplyRepository, never()).findAllByOwnerAndCommunityId(any(UserId.class), any(UUID.class));
    }

    /**
     * A first activation that has not happened yet leaves an interval with no instants in it. The
     * calculator must not be asked to price it, and zero is the honest total.
     */
    @Test
    void aFirstActivationInTheFutureYieldsZeroWithoutPricingAnything() {
        givenMembershipWithInvestment("1000.00");
        when(coefficientRepository.findEarliestValidFromByCommunityId(COMMUNITY_ID))
                .thenReturn(Optional.of(NOW.plusSeconds(86_400)));
        givenSupplies(SupplyMother.random().build());

        MembershipPayback payback = service.getPayback(COMMUNITY_ID, USER_ID);

        assertEquals(0, BigDecimal.ZERO.compareTo(payback.getSavedEur()));
        verifyNoInteractions(savingsCalculator);
    }

    // --- rule 2: startDate is the activation instant as a civil date in the community's zone ---

    /**
     * The activation instant is 23:00 UTC, which is already the next civil day in Madrid. Read
     * through the community's zone it is 2025-01-01, and reading it in UTC would report 2024-12-31
     * and shift every elapsed-day count by one.
     */
    @Test
    void theStartDateIsTheActivationInstantInTheCommunityZone() {
        givenMembershipWithInvestment("1000.00");
        givenCommunityStartedSharing();
        givenSupplies();

        MembershipPayback payback = service.getPayback(COMMUNITY_ID, USER_ID);

        assertEquals(LocalDate.parse("2025-01-01"), payback.getStartDate());
    }

    /**
     * 100 elapsed days between 2025-01-01 and 2025-04-11, 250 saved, so 2.50/day; 750 left is 300
     * days, and 300 / 30.4375 = 9.856... months, rounded up to 10. Asserted here as well as in the
     * value object's own test because it is the service that decides which two dates those are.
     */
    @Test
    void theElapsedDaysComeFromTheCivilDatesOfTheActivationAndOfNow() {
        givenMembershipWithInvestment("1000.00");
        givenCommunityStartedSharing();
        Supply first = SupplyMother.random().build();
        Supply second = SupplyMother.random().build();
        givenSupplies(first, second);
        givenSavings(first, "150.00", TariffSource.ESTIMATE);
        givenSavings(second, "100.00", TariffSource.ESTIMATE);

        MembershipPayback payback = service.getPayback(COMMUNITY_ID, USER_ID);

        assertEquals(10, payback.getEstimatedRemainingMonths());
    }

    // --- rule 4: tariffSource aggregates across supplies ---

    @Test
    void aTotalFromOnlyRealTariffsReportsARealTariffSource() {
        givenMembershipWithInvestment("1000.00");
        givenCommunityStartedSharing();
        Supply first = SupplyMother.random().build();
        Supply second = SupplyMother.random().build();
        givenSupplies(first, second);
        givenSavings(first, "10.00", TariffSource.REAL_TARIFF);
        givenSavings(second, "20.00", TariffSource.REAL_TARIFF);

        assertEquals(TariffSource.REAL_TARIFF, service.getPayback(COMMUNITY_ID, USER_ID).getTariffSource());
    }

    /**
     * A total is only as trustworthy as its least trustworthy part. The estimated supply is second
     * so the answer cannot come from reading the first one alone.
     */
    @Test
    void oneEstimatedSupplyMakesTheWholeTotalAnEstimate() {
        givenMembershipWithInvestment("1000.00");
        givenCommunityStartedSharing();
        Supply first = SupplyMother.random().build();
        Supply second = SupplyMother.random().build();
        givenSupplies(first, second);
        givenSavings(first, "10.00", TariffSource.REAL_TARIFF);
        givenSavings(second, "20.00", TariffSource.ESTIMATE);

        assertEquals(TariffSource.ESTIMATE, service.getPayback(COMMUNITY_ID, USER_ID).getTariffSource());
    }

    @Test
    void anEstimatedFirstSupplyAlsoMakesTheWholeTotalAnEstimate() {
        givenMembershipWithInvestment("1000.00");
        givenCommunityStartedSharing();
        Supply first = SupplyMother.random().build();
        Supply second = SupplyMother.random().build();
        givenSupplies(first, second);
        givenSavings(first, "10.00", TariffSource.ESTIMATE);
        givenSavings(second, "20.00", TariffSource.REAL_TARIFF);

        assertEquals(TariffSource.ESTIMATE, service.getPayback(COMMUNITY_ID, USER_ID).getTariffSource());
    }

    /**
     * With no supply to ask, no tariff was consulted, so the conservative source is reported.
     * Claiming a real tariff for a figure no tariff backed is the one direction that misleads.
     */
    @Test
    void aMemberWithNoSuppliesReportsAnEstimatedSource() {
        givenMembershipWithInvestment("1000.00");
        givenCommunityStartedSharing();
        givenSupplies();

        assertEquals(TariffSource.ESTIMATE, service.getPayback(COMMUNITY_ID, USER_ID).getTariffSource());
    }

    @Test
    void aCommunityThatNeverSharedReportsAnEstimatedSource() {
        givenMembershipWithInvestment("1000.00");
        when(coefficientRepository.findEarliestValidFromByCommunityId(COMMUNITY_ID))
                .thenReturn(Optional.empty());

        assertEquals(TariffSource.ESTIMATE, service.getPayback(COMMUNITY_ID, USER_ID).getTariffSource());
    }

    // --- the investment is read from the membership ---

    @Test
    void aMembershipWithoutAnInvestmentStillReportsItsSavings() {
        givenMembership(null);
        givenCommunityStartedSharing();
        Supply first = SupplyMother.random().build();
        Supply second = SupplyMother.random().build();
        givenSupplies(first, second);
        givenSavings(first, "120.50", TariffSource.ESTIMATE);
        givenSavings(second, "80.25", TariffSource.ESTIMATE);

        MembershipPayback payback = service.getPayback(COMMUNITY_ID, USER_ID);

        assertNull(payback.getInvestmentEur());
        assertEquals(0, new BigDecimal("200.75").compareTo(payback.getSavedEur()));
        assertNull(payback.getRemainingEur());
        assertNull(payback.getProgressRatio());
        assertNull(payback.getEstimatedRemainingMonths());
    }

    /**
     * Only supplies in the requested community are priced, which the service gets by scoping the
     * lookup rather than by filtering afterwards.
     */
    @Test
    void onlySuppliesOfTheRequestedCommunityAreLookedUp() {
        givenMembershipWithInvestment("1000.00");
        givenCommunityStartedSharing();
        givenSupplies();

        service.getPayback(COMMUNITY_ID, USER_ID);

        verify(getSupplyRepository).findAllByOwnerAndCommunityId(argThat(ownerId(USER_ID)), eq(COMMUNITY_ID));
    }

    // --- helpers ---

    private void givenMembershipWithInvestment(String investmentEur) {
        givenMembership(new BigDecimal(investmentEur));
    }

    private void givenMembership(BigDecimal investmentEur) {
        CommunityMembership membership = new CommunityMembership.Builder()
                .withId(UUID.randomUUID())
                .withUser(UserMother.randomUser())
                .withCommunity(CommunityMother.random().build())
                .withRole(CommunityRole.COMMUNITY_MEMBER)
                .withEnabled(true)
                .withInvestmentEur(investmentEur)
                .build();
        when(getMembershipsRepository.findByUserIdAndCommunityId(USER_ID, COMMUNITY_ID))
                .thenReturn(Optional.of(membership));
    }

    private void givenCommunityStartedSharing() {
        when(coefficientRepository.findEarliestValidFromByCommunityId(COMMUNITY_ID))
                .thenReturn(Optional.of(START));
    }

    /**
     * Matched on the wrapped id rather than on the {@link UserId} instance: unlike
     * {@code SupplyId}, {@code UserId} has no value equality, so two wrappers of the same uuid are
     * different arguments to Mockito.
     */
    private void givenSupplies(Supply... supplies) {
        when(getSupplyRepository.findAllByOwnerAndCommunityId(argThat(ownerId(USER_ID)), eq(COMMUNITY_ID)))
                .thenReturn(List.of(supplies));
    }

    private static ArgumentMatcher<UserId> ownerId(UUID expected) {
        return actual -> actual != null && expected.equals(actual.getId());
    }

    private void givenSavings(Supply supply, String amountEur, TariffSource source) {
        when(savingsCalculator.estimate(eq(supply), any(Instant.class), any(Instant.class)))
                .thenReturn(SupplySavings.of(new BigDecimal(amountEur), source));
    }
}

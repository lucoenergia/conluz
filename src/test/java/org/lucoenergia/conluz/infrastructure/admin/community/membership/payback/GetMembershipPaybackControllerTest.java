package org.lucoenergia.conluz.infrastructure.admin.community.membership.payback;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.membership.CreateMembershipService;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SaveSupplyPartitionCoefficientRepository;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyPartitionCoefficient;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.consumption.datadis.DatadisConsumption;
import org.lucoenergia.conluz.domain.production.plant.PlantMother;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementStatus;
import org.lucoenergia.conluz.domain.shared.time.ClockProvider;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityEntity;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityJpaRepository;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityMembershipJpaRepository;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyEntity;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyEntityMother;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyRepository;
import org.lucoenergia.conluz.infrastructure.admin.user.UserRepository;
import org.lucoenergia.conluz.infrastructure.consumption.datadis.DatadisConsumptionInfluxFixture;
import org.lucoenergia.conluz.infrastructure.production.plant.PlantEntity;
import org.lucoenergia.conluz.infrastructure.production.plant.PlantRepository;
import org.lucoenergia.conluz.infrastructure.production.sharingagreement.SharingAgreementEntity;
import org.lucoenergia.conluz.infrastructure.production.sharingagreement.SharingAgreementRepository;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The clock is fixed so that every figure derived from elapsed time is the same on every run.
 *
 * <p>The tariff in the test profile is 0.15 EUR/kWh with no VAT, so an amount is always
 * {@code kWh * 0.15} and the expected values below are arithmetic rather than golden numbers. The
 * community's activation is 2025-01-01 and "now" is 2025-04-11 local, which is 100 elapsed days.
 */
@Transactional
class GetMembershipPaybackControllerTest extends BaseControllerTest {

    private static final String PAYBACK_PATH = "/api/v1/communities/{communityId}/memberships/{userId}/payback";
    private static final String INVESTMENT_PATH = "/api/v1/communities/{communityId}/memberships/{userId}/investment";

    /** 2025-01-01T00:00 in Madrid. */
    private static final Instant ACTIVATED_AT = Instant.parse("2024-12-31T23:00:00Z");
    /** 2025-04-11T10:00 in Madrid, 100 civil days after the activation. */
    private static final Instant NOW = Instant.parse("2025-04-11T08:00:00Z");

    @MockitoBean
    private ClockProvider clockProvider;

    @Autowired
    private CommunityJpaRepository communityJpaRepository;
    @Autowired
    private CommunityMembershipJpaRepository membershipJpaRepository;
    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateMembershipService createMembershipService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private SupplyRepository supplyRepository;
    @Autowired
    private PlantRepository plantRepository;
    @Autowired
    private SharingAgreementRepository sharingAgreementRepository;
    @Autowired
    private SaveSupplyPartitionCoefficientRepository saveCoefficientRepository;
    @Autowired
    private DatadisConsumptionInfluxFixture influxFixture;

    private final List<String> writtenCups = new ArrayList<>();

    @BeforeEach
    void givenAFixedClock() {
        when(clockProvider.now()).thenReturn(NOW);
    }

    @AfterEach
    void clearWrittenSeries() {
        writtenCups.forEach(influxFixture::clear);
    }

    // --- AC1 end to end: what the write endpoint stores is what this one reports ---

    @Test
    void anInvestmentRecordedByAnAdminIsReportedBackHere() throws Exception {
        CommunityEntity community = persistSharingCommunity();
        User member = persistMember(community.getId());
        String adminToken = loginAsCommunityAdmin(community.getId());
        givenSupplyWithSelfConsumption(member, community, 200f);

        mockMvc.perform(put(INVESTMENT_PATH, community.getId(), member.getId())
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"investmentEur\": 1000.00}"))
                .andExpect(status().isNoContent());

        // 200 kWh * 0.15 = 30.00; 1000 - 30 = 970.00; 30 / 1000 = 0.0300
        mockMvc.perform(get(PAYBACK_PATH, community.getId(), member.getId())
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.investmentEur").value(1000.00))
                .andExpect(jsonPath("$.savedEur").value(30.00))
                .andExpect(jsonPath("$.remainingEur").value(970.00))
                .andExpect(jsonPath("$.progressRatio").value(0.0300))
                .andExpect(jsonPath("$.startDate").value("2025-01-01"))
                .andExpect(jsonPath("$.tariffSource").value("ESTIMATE"));
    }

    // --- AC4: no investment recorded, savings still computed ---

    @Test
    void aMembershipWithoutAnInvestmentStillReportsItsSavings() throws Exception {
        CommunityEntity community = persistSharingCommunity();
        User member = persistMember(community.getId());
        givenSupplyWithSelfConsumption(member, community, 200f);

        mockMvc.perform(get(PAYBACK_PATH, community.getId(), member.getId())
                        .header(HttpHeaders.AUTHORIZATION, loginUser(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.investmentEur").isEmpty())
                .andExpect(jsonPath("$.remainingEur").isEmpty())
                .andExpect(jsonPath("$.progressRatio").isEmpty())
                .andExpect(jsonPath("$.estimatedRemainingMonths").isEmpty())
                // Still computed: it does not depend on the investment.
                .andExpect(jsonPath("$.savedEur").value(30.00))
                .andExpect(jsonPath("$.startDate").value("2025-01-01"))
                .andExpect(jsonPath("$.tariffSource").value("ESTIMATE"));
    }

    // --- AC5: the community has never activated a coefficient ---

    @Test
    void aCommunityThatHasNeverSharedReportsNoPeriodAtAll() throws Exception {
        CommunityEntity community = persistCommunity();
        User member = persistMember(community.getId());
        setInvestmentDirectly(community.getId(), member.getId(), "1000.00");
        givenSupplyWithSelfConsumption(member, community, 200f);

        mockMvc.perform(get(PAYBACK_PATH, community.getId(), member.getId())
                        .header(HttpHeaders.AUTHORIZATION, loginUser(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startDate").isEmpty())
                .andExpect(jsonPath("$.savedEur").isEmpty())
                .andExpect(jsonPath("$.remainingEur").isEmpty())
                .andExpect(jsonPath("$.progressRatio").isEmpty())
                .andExpect(jsonPath("$.estimatedRemainingMonths").isEmpty())
                // The recorded investment is reported regardless: it exists independently of sharing.
                .andExpect(jsonPath("$.investmentEur").value(1000.00))
                // Never null, even with nothing priced.
                .andExpect(jsonPath("$.tariffSource").value("ESTIMATE"));
    }

    // --- AC6: two supplies in the community are summed ---

    @Test
    void savingsAreSummedAcrossTheMembersSuppliesInTheCommunity() throws Exception {
        CommunityEntity community = persistSharingCommunity();
        User member = persistMember(community.getId());
        givenSupplyWithSelfConsumption(member, community, 200f);
        givenSupplyWithSelfConsumption(member, community, 50f);

        // (200 + 50) kWh * 0.15 = 37.50
        mockMvc.perform(get(PAYBACK_PATH, community.getId(), member.getId())
                        .header(HttpHeaders.AUTHORIZATION, loginUser(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.savedEur").value(37.50));
    }

    // --- AC7: only the requested community's supplies count ---

    @Test
    void suppliesInAnotherCommunityAreNotCounted() throws Exception {
        CommunityEntity community = persistSharingCommunity();
        CommunityEntity otherCommunity = persistSharingCommunity();
        User member = persistMember(community.getId());
        createMembershipService.create(otherCommunity.getId(), member.getId(), CommunityRole.COMMUNITY_MEMBER);
        givenSupplyWithSelfConsumption(member, community, 200f);
        // Larger, so counting it would be unmistakable.
        givenSupplyWithSelfConsumption(member, otherCommunity, 1000f);

        String memberToken = loginUser(member);

        // Only the 200 kWh in the requested community: 30.00, not 180.00.
        mockMvc.perform(get(PAYBACK_PATH, community.getId(), member.getId())
                        .header(HttpHeaders.AUTHORIZATION, memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.savedEur").value(30.00));

        // And the other community reports only its own.
        mockMvc.perform(get(PAYBACK_PATH, otherCommunity.getId(), member.getId())
                        .header(HttpHeaders.AUTHORIZATION, memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.savedEur").value(150.00));
    }

    // --- AC8: nothing saved yet gives no rate ---

    @Test
    void aMemberWhoHasSavedNothingGetsNoEstimate() throws Exception {
        CommunityEntity community = persistSharingCommunity();
        User member = persistMember(community.getId());
        setInvestmentDirectly(community.getId(), member.getId(), "1000.00");
        // A supply with no stored consumption at all.
        persistSupply(member, community);

        mockMvc.perform(get(PAYBACK_PATH, community.getId(), member.getId())
                        .header(HttpHeaders.AUTHORIZATION, loginUser(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.savedEur").value(0.00))
                .andExpect(jsonPath("$.remainingEur").value(1000.00))
                .andExpect(jsonPath("$.progressRatio").value(0.0000))
                // Not infinite, and not an error.
                .andExpect(jsonPath("$.estimatedRemainingMonths").isEmpty());
    }

    /**
     * A member with no supplies at all is the other way to save nothing: the period exists and they
     * took nothing from it, so the amount is zero rather than absent.
     */
    @Test
    void aMemberWithNoSuppliesHasSavedZero() throws Exception {
        CommunityEntity community = persistSharingCommunity();
        User member = persistMember(community.getId());

        mockMvc.perform(get(PAYBACK_PATH, community.getId(), member.getId())
                        .header(HttpHeaders.AUTHORIZATION, loginUser(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.savedEur").value(0.00))
                .andExpect(jsonPath("$.startDate").value("2025-01-01"));
    }

    // --- AC10: savings beyond the investment ---

    @Test
    void savingsBeyondTheInvestmentReportAnUncappedRatioAndNothingRemaining() throws Exception {
        CommunityEntity community = persistSharingCommunity();
        User member = persistMember(community.getId());
        setInvestmentDirectly(community.getId(), member.getId(), "20.00");
        givenSupplyWithSelfConsumption(member, community, 200f);

        // 30.00 saved against 20.00 invested: 30 / 20 = 1.5000
        mockMvc.perform(get(PAYBACK_PATH, community.getId(), member.getId())
                        .header(HttpHeaders.AUTHORIZATION, loginUser(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.savedEur").value(30.00))
                .andExpect(jsonPath("$.progressRatio").value(1.5000))
                .andExpect(jsonPath("$.remainingEur").value(0.00))
                .andExpect(jsonPath("$.estimatedRemainingMonths").value(0));
    }

    /**
     * The projection, end to end: 100 elapsed days and 30.00 saved is 0.30/day; 970.00 left is
     * 3233.33 days, and 3233.33 / 30.4375 = 106.23... months, rounded up to 107.
     */
    @Test
    void theRemainingMonthsAreProjectedFromTheRateSoFar() throws Exception {
        CommunityEntity community = persistSharingCommunity();
        User member = persistMember(community.getId());
        setInvestmentDirectly(community.getId(), member.getId(), "1000.00");
        givenSupplyWithSelfConsumption(member, community, 200f);

        mockMvc.perform(get(PAYBACK_PATH, community.getId(), member.getId())
                        .header(HttpHeaders.AUTHORIZATION, loginUser(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estimatedRemainingMonths").value(107));
    }

    // --- AC9: the authorization matrix, one test per row ---

    @Test
    void theMemberThemselfCanRead() throws Exception {
        CommunityEntity community = persistSharingCommunity();
        User member = persistMember(community.getId());

        mockMvc.perform(get(PAYBACK_PATH, community.getId(), member.getId())
                        .header(HttpHeaders.AUTHORIZATION, loginUser(member)))
                .andExpect(status().isOk());
    }

    @Test
    void aCommunityAdminOfThatCommunityCanRead() throws Exception {
        CommunityEntity community = persistSharingCommunity();
        User member = persistMember(community.getId());

        mockMvc.perform(get(PAYBACK_PATH, community.getId(), member.getId())
                        .header(HttpHeaders.AUTHORIZATION, loginAsCommunityAdmin(community.getId())))
                .andExpect(status().isOk());
    }

    @Test
    void anotherMemberOfTheSameCommunityIsAnsweredNotFound() throws Exception {
        CommunityEntity community = persistSharingCommunity();
        User member = persistMember(community.getId());

        mockMvc.perform(get(PAYBACK_PATH, community.getId(), member.getId())
                        .header(HttpHeaders.AUTHORIZATION, loginAsCommunityMember(community.getId())))
                .andExpect(status().isNotFound());
    }

    @Test
    void anAdminOfAnotherCommunityIsAnsweredNotFound() throws Exception {
        CommunityEntity community = persistSharingCommunity();
        CommunityEntity otherCommunity = persistCommunity();
        User member = persistMember(community.getId());

        mockMvc.perform(get(PAYBACK_PATH, community.getId(), member.getId())
                        .header(HttpHeaders.AUTHORIZATION, loginAsCommunityAdmin(otherCommunity.getId())))
                .andExpect(status().isNotFound());
    }

    /**
     * The row that rules out {@code canReadCommunity}, which would have let this caller through on
     * the strength of being a platform admin alone.
     */
    @Test
    void aPlatformAdminWhoIsNeitherTheMemberNorAnAdminThereIsAnsweredNotFound() throws Exception {
        String platformAdminToken = loginAsDefaultPlatformAdmin();
        CommunityEntity community = persistSharingCommunity();
        User member = persistMember(community.getId());

        mockMvc.perform(get(PAYBACK_PATH, community.getId(), member.getId())
                        .header(HttpHeaders.AUTHORIZATION, platformAdminToken))
                .andExpect(status().isNotFound());
    }

    /**
     * Being a platform admin does not remove access either: they reach their own payback through
     * the self branch, like any other member.
     */
    @Test
    void aPlatformAdminCanReadTheirOwnPayback() throws Exception {
        loginAsDefaultPlatformAdmin();
        CommunityEntity community = persistSharingCommunity();
        UUID platformAdminUserId = defaultPlatformAdminId();
        createMembershipService.create(community.getId(), platformAdminUserId, CommunityRole.COMMUNITY_MEMBER);
        // Re-login so the principal carries the membership just granted.
        String refreshedToken = loginAsDefaultPlatformAdmin();

        mockMvc.perform(get(PAYBACK_PATH, community.getId(), platformAdminUserId)
                        .header(HttpHeaders.AUTHORIZATION, refreshedToken))
                .andExpect(status().isOk());
    }

    @Test
    void anUnauthenticatedCallerIsRejected() throws Exception {
        CommunityEntity community = persistSharingCommunity();
        User member = persistMember(community.getId());

        mockMvc.perform(get(PAYBACK_PATH, community.getId(), member.getId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anAllowedCallerTargetingAMembershipThatDoesNotExistIsAnsweredNotFound() throws Exception {
        CommunityEntity community = persistSharingCommunity();
        // Exists, but is not a member of this community.
        User outsider = persistUser();

        mockMvc.perform(get(PAYBACK_PATH, community.getId(), outsider.getId())
                        .header(HttpHeaders.AUTHORIZATION, loginAsCommunityAdmin(community.getId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.traceId").exists());
    }

    // --- fixtures ---

    private CommunityEntity persistCommunity() {
        return communityJpaRepository.save(CommunityMother.randomEntity().build());
    }

    /**
     * A community with a plant whose coefficient was activated at {@link #ACTIVATED_AT}, which is
     * what gives its members a payback start date.
     */
    private CommunityEntity persistSharingCommunity() {
        CommunityEntity community = persistCommunity();
        SupplyEntity plantSupply = persistSupply(persistUser(), community);
        PlantEntity plant = plantRepository.save(
                PlantMother.randomPlantEntity().withSupply(plantSupply).build());
        SharingAgreementEntity agreement = persistPublishedAgreement(plant);

        saveCoefficientRepository.save(new SupplyPartitionCoefficient.Builder()
                .withId(UUID.randomUUID())
                .withSupplyId(plantSupply.getId())
                .withPlantId(plant.getId())
                .withSharingAgreementId(agreement.getId())
                .withCoefficient(BigDecimal.ONE)
                .withValidFrom(ACTIVATED_AT)
                .withValidTo(null)
                .withCreatedAt(Instant.now())
                .build());

        return community;
    }

    private SharingAgreementEntity persistPublishedAgreement(PlantEntity plant) {
        SharingAgreementEntity agreement = new SharingAgreementEntity();
        agreement.setId(UUID.randomUUID());
        agreement.setPlant(plant);
        agreement.setName("Payback test agreement " + UUID.randomUUID());
        agreement.setStatus(SharingAgreementStatus.PUBLISHED);
        agreement.setCreatedAt(Instant.now());
        agreement.setCreatedBy(null);
        return sharingAgreementRepository.save(agreement);
    }

    private User persistUser() {
        User user = UserMother.randomUser();
        user.enable();
        createUserRepository.create(user);
        return user;
    }

    private User persistMember(UUID communityId) {
        User member = persistUser();
        createMembershipService.create(communityId, member.getId(), CommunityRole.COMMUNITY_MEMBER);
        return member;
    }

    private SupplyEntity persistSupply(User owner, CommunityEntity community) {
        return supplyRepository.save(SupplyEntityMother.random(
                userRepository.getReferenceById(owner.getId()), community));
    }

    /**
     * A supply of the member with a single hourly record inside the community's sharing period, so
     * the expected amount is {@code kWh * 0.15}.
     */
    private SupplyEntity givenSupplyWithSelfConsumption(User owner, CommunityEntity community,
                                                        float selfConsumptionKWh) {
        SupplyEntity supply = persistSupply(owner, community);
        DatadisConsumption record = DatadisConsumptionInfluxFixture.hourlyRecord(
                supply.getCode(), "2025/02/01", "00:00", selfConsumptionKWh, selfConsumptionKWh, 0f);
        influxFixture.write(List.of(record));
        writtenCups.add(supply.getCode());
        return supply;
    }

    private void setInvestmentDirectly(UUID communityId, UUID userId, String amount) {
        membershipJpaRepository.findByUserIdAndCommunityId(userId, communityId)
                .map(membership -> {
                    membership.setInvestmentEur(new BigDecimal(amount));
                    return membershipJpaRepository.save(membership);
                })
                .orElseThrow();
    }

    private UUID defaultPlatformAdminId() throws Exception {
        String claims = loginAsDefaultPlatformAdmin().replace("Bearer ", "").split("\\.")[1];
        String payload = new String(java.util.Base64.getUrlDecoder().decode(claims),
                java.nio.charset.StandardCharsets.UTF_8);
        return UUID.fromString(objectMapper.readTree(payload).path("sub").asText());
    }
}

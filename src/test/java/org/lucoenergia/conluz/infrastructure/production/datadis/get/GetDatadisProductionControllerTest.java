package org.lucoenergia.conluz.infrastructure.production.datadis.get;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.create.CreateCommunityRepository;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.production.plant.PlantMother;
import org.lucoenergia.conluz.domain.production.plant.create.CreatePlantRepository;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityEntity;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityJpaRepository;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityMembershipEntity;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityMembershipJpaRepository;
import org.lucoenergia.conluz.infrastructure.admin.user.UserEntity;
import org.lucoenergia.conluz.infrastructure.admin.user.UserRepository;
import org.lucoenergia.conluz.infrastructure.production.datadis.DatadisProductionInfluxLoader;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security matrix and happy-path tests for the Datadis production read endpoints. Uses two
 * communities so the cross-community leak (IDOR) case can be exercised.
 */
@Transactional
class GetDatadisProductionControllerTest extends BaseControllerTest {

    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateCommunityRepository createCommunityRepository;
    @Autowired
    private CreateSupplyRepository createSupplyRepository;
    @Autowired
    private CreatePlantRepository createPlantRepository;
    @Autowired
    private CommunityMembershipJpaRepository communityMembershipJpaRepository;
    @Autowired
    private CommunityJpaRepository communityJpaRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private DatadisProductionInfluxLoader datadisProductionInfluxLoader;

    private static final String CUPS_A = DatadisProductionInfluxLoader.CUPS_CODE_A;
    private static final String CUPS_B = DatadisProductionInfluxLoader.CUPS_CODE_B;
    private static final String APRIL_START = "2023-04-01T00:00:00Z";
    private static final String APRIL_END = "2023-04-30T23:59:59Z";
    private static final String YEAR_START = "2023-01-01T00:00:00Z";
    private static final String YEAR_END = "2023-12-31T23:59:59Z";
    private static final List<String> GRANULARITIES = List.of("hourly", "daily", "monthly", "yearly");

    private Community communityA;
    private Community communityB;
    private User memberA;
    private Supply supplyA;
    private Supply supplyB;

    @BeforeEach
    void setUp() {
        datadisProductionInfluxLoader.loadData();

        communityA = createCommunityRepository.create(CommunityMother.random().build());
        communityB = createCommunityRepository.create(CommunityMother.random().build());

        memberA = createEnabledUser();
        User memberB = createEnabledUser();
        createMembership(memberA, communityA, CommunityRole.COMMUNITY_MEMBER);
        createMembership(memberB, communityB, CommunityRole.COMMUNITY_MEMBER);

        // A plant of community A is backed by a supply whose CUPS matches the seeded production series.
        supplyA = createSupplyRepository.create(
                SupplyMother.random(memberA).withCode(CUPS_A).withCommunity(communityA).withEnabled(true).build(),
                UserId.of(memberA.getId()), communityA.getId());
        createPlantRepository.create(PlantMother.random(supplyA).build(), SupplyId.of(supplyA.getId()));

        supplyB = createSupplyRepository.create(
                SupplyMother.random(memberB).withCode(CUPS_B).withCommunity(communityB).withEnabled(true).build(),
                UserId.of(memberB.getId()), communityB.getId());
        createPlantRepository.create(PlantMother.random(supplyB).build(), SupplyId.of(supplyB.getId()));
    }

    @AfterEach
    void tearDown() {
        datadisProductionInfluxLoader.clearData();
    }

    private String url(UUID communityId, String granularity) {
        return "/api/v1/communities/" + communityId + "/production/datadis/" + granularity;
    }

    private User createEnabledUser() {
        User user = UserMother.randomUser();
        user.enable();
        createUserRepository.create(user);
        return user;
    }

    private void createMembership(User user, Community community, CommunityRole role) {
        UserEntity userEntity = userRepository.findByPersonalId(user.getPersonalId())
                .orElseThrow(() -> new IllegalStateException("User not found: " + user.getPersonalId()));
        CommunityEntity communityEntity = communityJpaRepository.findById(community.getId())
                .orElseThrow(() -> new IllegalStateException("Community not found: " + community.getId()));
        CommunityMembershipEntity membership = new CommunityMembershipEntity.Builder()
                .withId(UUID.randomUUID())
                .withUser(userEntity)
                .withCommunity(communityEntity)
                .withRole(role)
                .withEnabled(true)
                .build();
        communityMembershipJpaRepository.save(membership);
    }

    @Test
    void returnsUnauthorizedWithoutToken() throws Exception {
        for (String granularity : GRANULARITIES) {
            mockMvc.perform(get(url(communityA.getId(), granularity))
                            .queryParam("startDate", APRIL_START)
                            .queryParam("endDate", APRIL_END))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    void returnsForbiddenForPlatformAdminWhoIsNotAMember() throws Exception {
        // Golden rule: a platform admin who is not a member of the community can *see* it exists
        // (so 404 would leak nothing) but is not entitled to its communal production data → 403.
        String platformAdminToken = loginAsDefaultPlatformAdmin();

        for (String granularity : GRANULARITIES) {
            mockMvc.perform(get(url(communityA.getId(), granularity))
                            .header("Authorization", platformAdminToken)
                            .queryParam("startDate", APRIL_START)
                            .queryParam("endDate", APRIL_END))
                    .andDo(print())
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void returnsNotFoundForAuthenticatedNonMember() throws Exception {
        // A regular authenticated user who is not a member cannot see the community → 404 (not 403).
        String partnerToken = loginAsPartner();

        for (String granularity : GRANULARITIES) {
            mockMvc.perform(get(url(communityA.getId(), granularity))
                            .header("Authorization", partnerToken)
                            .queryParam("startDate", APRIL_START)
                            .queryParam("endDate", APRIL_END))
                    .andExpect(status().isNotFound());
        }
    }

    @Test
    void returnsNotFoundWhenMemberTargetsAnotherCommunity() throws Exception {
        String tokenA = loginUser(memberA);

        for (String granularity : GRANULARITIES) {
            mockMvc.perform(get(url(communityB.getId(), granularity))
                            .header("Authorization", tokenA)
                            .queryParam("startDate", APRIL_START)
                            .queryParam("endDate", APRIL_END))
                    .andExpect(status().isNotFound());
        }
    }

    @Test
    void returnsNotFoundForCrossCommunitySupplyIdLeak() throws Exception {
        // IDOR: member of A passes A's communityId in the path but community B's plant supplyId.
        // The data-layer ownership check rejects it with 404 without exposing B's data.
        String tokenA = loginUser(memberA);

        for (String granularity : GRANULARITIES) {
            mockMvc.perform(get(url(communityA.getId(), granularity))
                            .header("Authorization", tokenA)
                            .queryParam("startDate", APRIL_START)
                            .queryParam("endDate", APRIL_END)
                            .queryParam("supplyId", supplyB.getId().toString()))
                    .andDo(print())
                    .andExpect(status().isNotFound());
        }
    }

    @Test
    void memberReadsHourlyProductionOfTheirCommunity() throws Exception {
        String tokenA = loginUser(memberA);

        mockMvc.perform(get(url(communityA.getId(), "hourly"))
                        .header("Authorization", tokenA)
                        .queryParam("startDate", APRIL_START)
                        .queryParam("endDate", APRIL_END))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].cups").value(CUPS_A));
    }

    @Test
    void memberReadsDailyProductionOfTheirCommunity() throws Exception {
        String tokenA = loginUser(memberA);

        mockMvc.perform(get(url(communityA.getId(), "daily"))
                        .header("Authorization", tokenA)
                        .queryParam("startDate", APRIL_START)
                        .queryParam("endDate", APRIL_END))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].cups").value(CUPS_A));
    }

    @Test
    void memberReadsMonthlyProductionOfTheirCommunity() throws Exception {
        String tokenA = loginUser(memberA);

        mockMvc.perform(get(url(communityA.getId(), "monthly"))
                        .header("Authorization", tokenA)
                        .queryParam("startDate", APRIL_START)
                        .queryParam("endDate", APRIL_END))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].cups").value(CUPS_A));
    }

    @Test
    void memberReadsYearlyProductionOfTheirCommunity() throws Exception {
        String tokenA = loginUser(memberA);

        mockMvc.perform(get(url(communityA.getId(), "yearly"))
                        .header("Authorization", tokenA)
                        .queryParam("startDate", YEAR_START)
                        .queryParam("endDate", YEAR_END))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].cups").value(CUPS_A));
    }

    @Test
    void memberReadsProductionWithValidInCommunitySupplyId() throws Exception {
        String tokenA = loginUser(memberA);

        mockMvc.perform(get(url(communityA.getId(), "monthly"))
                        .header("Authorization", tokenA)
                        .queryParam("startDate", APRIL_START)
                        .queryParam("endDate", APRIL_END)
                        .queryParam("supplyId", supplyA.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].cups").value(CUPS_A));
    }
}

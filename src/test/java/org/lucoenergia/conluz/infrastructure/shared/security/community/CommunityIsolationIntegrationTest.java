package org.lucoenergia.conluz.infrastructure.shared.security.community;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.create.CreateCommunityRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.CreateMembershipRepository;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.ManagePlatformAdminRepository;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityMembershipJpaRepository;
import org.lucoenergia.conluz.infrastructure.admin.community.MembershipStateTestSupport;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class CommunityIsolationIntegrationTest extends BaseControllerTest {

    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateMembershipRepository createMembershipRepository;
    @Autowired
    private CreateCommunityRepository createCommunityRepository;
    @Autowired
    private CreateSupplyRepository createSupplyRepository;
    @Autowired
    private CommunityMembershipJpaRepository communityMembershipJpaRepository;
    @Autowired
    private ManagePlatformAdminRepository managePlatformAdminRepository;

    private Community communityA;
    private Community communityB;
    private User memberA;
    private User memberB;
    private Supply supplyA;
    private Supply supplyB;

    @BeforeEach
    void setupCommunities() {
        // Create two communities
        communityA = createCommunityRepository.create(CommunityMother.random().build());
        communityB = createCommunityRepository.create(CommunityMother.random().build());

        // Create member users
        memberA = UserMother.randomUser();
        memberA.enable();
        createUserRepository.create(memberA);

        memberB = UserMother.randomUser();
        memberB.enable();
        createUserRepository.create(memberB);

        // Create memberships
        createMembership(memberA, communityA, CommunityRole.COMMUNITY_MEMBER);
        createMembership(memberB, communityB, CommunityRole.COMMUNITY_MEMBER);

        // Create supplies assigned to respective communities
        Supply supplyADomain = SupplyMother.random(memberA)
                .withCommunity(communityA)
                .withEnabled(true)
                .build();
        supplyA = createSupplyRepository.create(supplyADomain, UserId.of(memberA.getId()), communityA.getId());

        Supply supplyBDomain = SupplyMother.random(memberB)
                .withCommunity(communityB)
                .withEnabled(true)
                .build();
        supplyB = createSupplyRepository.create(supplyBDomain, UserId.of(memberB.getId()), communityB.getId());
    }

    @Test
    void memberACanAccessTheirOwnCommunitySupply() throws Exception {
        String tokenA = loginUser(memberA);

        mockMvc.perform(get("/api/v1/supplies/" + supplyA.getId())
                        .header("Authorization", tokenA)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk());
    }

    @Test
    void memberAWithCorrectCommunityHeaderCanAccessTheirSupply() throws Exception {
        String tokenA = loginUser(memberA);

        mockMvc.perform(get("/api/v1/supplies/" + supplyA.getId())
                        .header("Authorization", tokenA)
                        .header(CommunityContextFilter.COMMUNITY_ID_HEADER, communityA.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("MEM-032 X-Community-Id for a community the platform admin is not a member of is refused, even on an endpoint it may use")
    void platformAdminWithTheHeaderOfACommunityItIsNotAMemberOfGetsForbidden() throws Exception {
        String platformAdmin = loginAsDefaultPlatformAdmin();

        // Without the header the platform admin reads the community...
        mockMvc.perform(get("/api/v1/communities/" + communityA.getId())
                        .header("Authorization", platformAdmin))
                .andExpect(status().isOk());
        // ...and with it, naming a community it has no membership in, the request is refused.
        mockMvc.perform(get("/api/v1/communities/" + communityA.getId())
                        .header("Authorization", platformAdmin)
                        .header(CommunityContextFilter.COMMUNITY_ID_HEADER, communityA.getId().toString()))
                .andDo(print())
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("MEM-032 X-Community-Id for a community the platform admin is a member of is accepted")
    void platformAdminWithTheHeaderOfItsOwnCommunityIsAccepted() throws Exception {
        User platformAdmin = UserMother.randomUser();
        platformAdmin.enable();
        createUserRepository.create(platformAdmin);
        createMembership(platformAdmin, communityA, CommunityRole.COMMUNITY_MEMBER);
        managePlatformAdminRepository.grant(UserId.of(platformAdmin.getId()));

        mockMvc.perform(get("/api/v1/communities/" + communityA.getId())
                        .header("Authorization", loginUser(platformAdmin))
                        .header(CommunityContextFilter.COMMUNITY_ID_HEADER, communityA.getId().toString()))
                .andExpect(status().isOk());
    }

    @Test
    void memberAWithWrongCommunityHeaderGetsForbidden() throws Exception {
        String tokenA = loginUser(memberA);

        mockMvc.perform(get("/api/v1/supplies/" + supplyA.getId())
                        .header("Authorization", tokenA)
                        .header(CommunityContextFilter.COMMUNITY_ID_HEADER, communityB.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("MEM-004 X-Community-Id for a pending membership is refused, as for a non-member")
    void pendingMemberWithTheirCommunityHeaderGetsForbidden() throws Exception {
        MembershipStateTestSupport.markPending(communityMembershipJpaRepository, memberA.getId(), communityA.getId());
        String tokenA = loginUser(memberA);

        mockMvc.perform(get("/api/v1/supplies/" + supplyA.getId())
                        .header("Authorization", tokenA)
                        .header(CommunityContextFilter.COMMUNITY_ID_HEADER, communityA.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Access denied: user is not a member of the requested community"));
    }

    @Test
    @DisplayName("MEM-004 X-Community-Id for a disabled membership is refused, as for a non-member")
    void disabledMemberWithTheirCommunityHeaderGetsForbidden() throws Exception {
        MembershipStateTestSupport.markDisabled(communityMembershipJpaRepository, memberA.getId(), communityA.getId());
        String tokenA = loginUser(memberA);

        mockMvc.perform(get("/api/v1/supplies/" + supplyA.getId())
                        .header("Authorization", tokenA)
                        .header(CommunityContextFilter.COMMUNITY_ID_HEADER, communityA.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Access denied: user is not a member of the requested community"));
    }

    @Test
    void memberACannotAccessCommunitiyBSupply() throws Exception {
        String tokenA = loginUser(memberA);

        mockMvc.perform(get("/api/v1/supplies/" + supplyB.getId())
                        .header("Authorization", tokenA)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isNotFound());
    }

    @Test
    void memberBCannotAccessCommunityASupply() throws Exception {
        String tokenB = loginUser(memberB);

        mockMvc.perform(get("/api/v1/supplies/" + supplyA.getId())
                        .header("Authorization", tokenB)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidCommunityIdHeaderReturnsBadRequest() throws Exception {
        String tokenA = loginUser(memberA);

        mockMvc.perform(get("/api/v1/supplies/" + supplyA.getId())
                        .header("Authorization", tokenA)
                        .header(CommunityContextFilter.COMMUNITY_ID_HEADER, "not-a-uuid")
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    // --- Nested read endpoints scoped by community path ---

    @Test
    void memberACanListSuppliesOfTheirCommunity() throws Exception {
        String tokenA = loginUser(memberA);

        mockMvc.perform(get("/api/v1/communities/" + communityA.getId() + "/supplies")
                        .header("Authorization", tokenA))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(supplyA.getId().toString()));
    }

    @Test
    void memberACannotListSuppliesOfCommunityB() throws Exception {
        String tokenA = loginUser(memberA);

        mockMvc.perform(get("/api/v1/communities/" + communityB.getId() + "/supplies")
                        .header("Authorization", tokenA))
                .andDo(print())
                .andExpect(status().isNotFound());
    }

    @Test
    void memberACanReadProductionOfTheirCommunity() throws Exception {
        String tokenA = loginUser(memberA);

        mockMvc.perform(get("/api/v1/communities/" + communityA.getId() + "/production/hourly")
                        .header("Authorization", tokenA)
                        .queryParam("startDate", "2023-09-01T00:00:00.000+02:00")
                        .queryParam("endDate", "2023-09-01T23:00:00.000+02:00"))
                .andDo(print())
                .andExpect(status().isOk());
    }

    @Test
    void memberACannotReadProductionOfCommunityB() throws Exception {
        String tokenA = loginUser(memberA);

        mockMvc.perform(get("/api/v1/communities/" + communityB.getId() + "/production/hourly")
                        .header("Authorization", tokenA)
                        .queryParam("startDate", "2023-09-01T00:00:00.000+02:00")
                        .queryParam("endDate", "2023-09-01T23:00:00.000+02:00"))
                .andDo(print())
                .andExpect(status().isNotFound());
    }

    @Test
    void regularMemberCannotDownloadCommunityConsumptionCsv() throws Exception {
        String tokenA = loginUser(memberA);

        // The CSV report is admin-only; a regular member can see the community but is not an admin,
        // so the request is denied with a 403 (the community's existence is not leaked anyway).
        mockMvc.perform(get("/api/v1/communities/" + communityA.getId()
                        + "/consumption/datadis/report/hourly/csv")
                        .header("Authorization", tokenA)
                        .queryParam("startDate", "2023-04-01T00:00:00Z")
                        .queryParam("endDate", "2023-04-30T23:59:59Z"))
                .andDo(print())
                .andExpect(status().isForbidden());
    }

    @Test
    void adminOfCommunityACannotSyncCommunityB() throws Exception {
        // A community admin of A is rejected with 404 when targeting community B's sync job,
        // so a sync can never reach another community's supplies.
        User adminA = UserMother.randomUser();
        adminA.enable();
        createUserRepository.create(adminA);
        createMembership(adminA, communityA, CommunityRole.COMMUNITY_ADMIN);
        String tokenAdminA = loginUser(adminA);

        mockMvc.perform(post("/api/v1/communities/" + communityB.getId() + "/consumption/datadis/sync")
                        .header("Authorization", tokenAdminA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"year\": 2024}"))
                .andDo(print())
                .andExpect(status().isNotFound());
    }

    private void createMembership(User user, Community community, CommunityRole role) {
        createMembershipRepository.create(community.getId(), user.getId(), role);
    }
}

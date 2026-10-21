package org.lucoenergia.conluz.infrastructure.admin.community.access.platformadmin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.create.CreateCommunityRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.CreateMembershipService;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.ManagePlatformAdminRepository;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.plant.PlantMother;
import org.lucoenergia.conluz.domain.production.plant.create.CreatePlantRepository;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A platform admin who is also a community admin keeps exactly the access that membership gives (#446): the full
 * access to the community it administers, and nothing of a community it does not belong to.
 */
@Transactional
class PlatformAdminWhoIsCommunityAdminAccessControllerTest extends BaseControllerTest {

    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateCommunityRepository createCommunityRepository;
    @Autowired
    private CreateMembershipService createMembershipService;
    @Autowired
    private CreateSupplyRepository createSupplyRepository;
    @Autowired
    private CreatePlantRepository createPlantRepository;
    @Autowired
    private ManagePlatformAdminRepository managePlatformAdminRepository;

    private Side administered;
    private Side other;
    private String platformAdmin;

    private record Side(Community community, User member, Supply supply, Plant plant) {
    }

    @BeforeEach
    void createTwoCommunitiesAndAPlatformAdminWhoAdministersTheFirst() throws Exception {
        administered = side();
        other = side();
        User admin = enabledUser();
        createMembershipService.create(administered.community.getId(), admin.getId(), CommunityRole.COMMUNITY_ADMIN);
        managePlatformAdminRepository.grant(UserId.of(admin.getId()));
        platformAdmin = loginUser(admin);
    }

    @Test
    @DisplayName("ACC-020 MEM-031 a platform admin who is a community admin has that community's full access")
    void theAdministeredCommunityIsFullyReachable() throws Exception {
        expect(200, "/api/v1/communities/{id}/memberships", administered.community.getId());
        expect(200, "/api/v1/users/{id}", administered.member.getId());
        expect(200, "/api/v1/users/{id}/supplies", administered.member.getId());
        expect(200, "/api/v1/communities/{id}/supplies", administered.community.getId());
        expect(200, "/api/v1/supplies/{id}", administered.supply.getId());
        expect(200, "/api/v1/communities/{id}/plants", administered.community.getId());
        expect(200, "/api/v1/plants/{id}", administered.plant.getId());
        expect(200, "/api/v1/plants/{id}/sharing-agreements", administered.plant.getId());
        mockMvc.perform(get("/api/v1/users").header(HttpHeaders.AUTHORIZATION, platformAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    @DisplayName("ACC-020 MEM-031 a platform admin who is a community admin reaches nothing of another community")
    void anotherCommunityIsNotReachable() throws Exception {
        expect(403, "/api/v1/communities/{id}/memberships", other.community.getId());
        expect(404, "/api/v1/users/{id}", other.member.getId());
        expect(404, "/api/v1/users/{id}/supplies", other.member.getId());
        expect(403, "/api/v1/communities/{id}/supplies", other.community.getId());
        expect(404, "/api/v1/supplies/{id}", other.supply.getId());
        expect(403, "/api/v1/communities/{id}/plants", other.community.getId());
        expect(404, "/api/v1/plants/{id}", other.plant.getId());
        expect(404, "/api/v1/plants/{id}/sharing-agreements", other.plant.getId());
        expect(403, "/api/v1/communities/{id}/config/datadis", other.community.getId());
        // The community itself stays visible, as to any platform admin
        expect(200, "/api/v1/communities/{id}", other.community.getId());
    }

    private void expect(int status, String path, Object id) throws Exception {
        mockMvc.perform(get(path, id).header(HttpHeaders.AUTHORIZATION, platformAdmin))
                .andExpect(status().is(status));
    }

    private Side side() {
        Community community = createCommunityRepository.create(CommunityMother.random().build());
        User member = enabledUser();
        createMembershipService.create(community.getId(), member.getId(), CommunityRole.COMMUNITY_MEMBER);
        Supply supply = createSupplyRepository.create(SupplyMother.random(member).build(), UserId.of(member.getId()),
                community.getId());
        Plant plant = createPlantRepository.create(PlantMother.random(supply).build(), SupplyId.of(supply.getId()));
        return new Side(community, member, supply, plant);
    }

    private User enabledUser() {
        User user = UserMother.randomUser();
        user.enable();
        createUserRepository.create(user);
        return user;
    }
}

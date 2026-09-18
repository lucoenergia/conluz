package org.lucoenergia.conluz.infrastructure.admin.user;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.create.CreateCommunityRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.CreateMembershipService;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.transaction.annotation.Transactional;

import static org.lucoenergia.conluz.infrastructure.admin.supply.create.CreateSupplyRepositoryDatabase.DEFAULT_COMMUNITY_ID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The user capabilities as a client receives them, including the two that only
 * {@code GET /users/current} carries.
 */
@Transactional
class UserCapabilitiesEndpointTest extends BaseControllerTest {

    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateCommunityRepository createCommunityRepository;
    @Autowired
    private CreateMembershipService createMembershipService;
    @Autowired
    private CreateSupplyRepository createSupplyRepository;

    @Test
    void theCurrentUserCarriesBothItsOwnAndThePlatformCapabilities() throws Exception {
        mockMvc.perform(get("/api/v1/users/current")
                        .header(HttpHeaders.AUTHORIZATION, loginAsDefaultPlatformAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.platformCapabilities.canCreateCommunity").value(true))
                .andExpect(jsonPath("$.platformCapabilities.canListUsers").value(true))
                .andExpect(jsonPath("$.capabilities.canRead").value(true))
                // Nobody may delete, disable or de-admin themselves, platform admins included.
                .andExpect(jsonPath("$.capabilities.canDelete").value(false))
                .andExpect(jsonPath("$.capabilities.canDisable").value(false))
                .andExpect(jsonPath("$.capabilities.canRevokePlatformAdmin").value(false));
    }

    /**
     * Administering a community is enough to list users -- scoping that listing is the endpoint's
     * job -- but not to create a community.
     */
    @Test
    void aCommunityAdminMayListUsersButNotCreateCommunities() throws Exception {
        Community community = createCommunityRepository.create(CommunityMother.random().build());

        mockMvc.perform(get("/api/v1/users/current")
                        .header(HttpHeaders.AUTHORIZATION, loginAsCommunityAdmin(community.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.platformCapabilities.canListUsers").value(true))
                .andExpect(jsonPath("$.platformCapabilities.canCreateCommunity").value(false));
    }

    /**
     * A member reading their own record is offered neither. canEdit is false on purpose: name, DNI
     * and member number are an administrative change, and contact details go through
     * PUT /users/profile, which needs no capability because any authenticated caller may use it.
     */
    @Test
    void aPlainMemberIsOfferedNothingOnTheirOwnRecord() throws Exception {
        Community community = createCommunityRepository.create(CommunityMother.random().build());

        mockMvc.perform(get("/api/v1/users/current")
                        .header(HttpHeaders.AUTHORIZATION, loginAsCommunityMember(community.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capabilities.canRead").value(true))
                .andExpect(jsonPath("$.capabilities.canListSupplies").value(true))
                .andExpect(jsonPath("$.capabilities.canEdit").value(false))
                .andExpect(jsonPath("$.capabilities.canDelete").value(false))
                .andExpect(jsonPath("$.platformCapabilities.canListUsers").value(false))
                .andExpect(jsonPath("$.platformCapabilities.canCreateCommunity").value(false));
    }

    @Test
    void theDetailEndpointReportsWhatAnAdminMayDoToAMember() throws Exception {
        Community community = createCommunityRepository.create(CommunityMother.random().build());
        User member = persistMemberOf(community);

        mockMvc.perform(get("/api/v1/users/{userId}", member.getId())
                        .header(HttpHeaders.AUTHORIZATION, loginAsCommunityAdmin(community.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capabilities.canRead").value(true))
                .andExpect(jsonPath("$.capabilities.canEdit").value(true))
                .andExpect(jsonPath("$.capabilities.canDelete").value(true))
                .andExpect(jsonPath("$.capabilities.canListSupplies").value(true))
                // Granting platform admin is not a community admin's to give.
                .andExpect(jsonPath("$.capabilities.canGrantPlatformAdmin").value(false));
    }

    @Test
    void theListingCarriesCapabilitiesOnEveryItem() throws Exception {
        Community community = createCommunityRepository.create(CommunityMother.random().build());
        persistMemberOf(community);
        persistMemberOf(community);

        mockMvc.perform(get("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, loginAsCommunityAdmin(community.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].capabilities.canRead").value(true))
                .andExpect(jsonPath("$.items[1].capabilities.canRead").value(true));
    }

    /**
     * The embedded owner gets capabilities like any other user — and its memberships map stays
     * empty. The batch that decides those capabilities must not be written back onto the user:
     * a caller who can see somebody only as a supply owner must not learn which communities they
     * belong to.
     */
    @Test
    void theOwnerEmbeddedInASupplyCarriesCapabilitiesButStillNoMemberships() throws Exception {
        User owner = persistMemberOf(DEFAULT_COMMUNITY_ID);
        var supply = createSupplyRepository.create(SupplyMother.random().build(), UserId.of(owner.getId()));

        mockMvc.perform(get("/api/v1/supplies/{supplyId}", supply.getId())
                        .header(HttpHeaders.AUTHORIZATION, loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.capabilities.canRead").value(true))
                .andExpect(jsonPath("$.user.capabilities.canEdit").value(true))
                .andExpect(jsonPath("$.user.memberships").isEmpty());
    }

    private User persistMemberOf(Community community) {
        return persistMemberOf(community.getId());
    }

    private User persistMemberOf(java.util.UUID communityId) {
        User user = UserMother.randomUser();
        user.enable();
        createUserRepository.create(user);
        createMembershipService.create(communityId, user.getId(), CommunityRole.COMMUNITY_MEMBER);
        return user;
    }
}

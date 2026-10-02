package org.lucoenergia.conluz.infrastructure.admin.supply;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.membership.CreateMembershipService;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
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
 * The supply capabilities as a client receives them. The rules are the assembler's unit tests'
 * business; what matters here is that the assembler is wired in, and that the owner case comes out
 * right — the owner may read their supply and its coefficients but may not edit it, which is the
 * one shape a client cannot guess from a role.
 */
@Transactional
class SupplyCapabilitiesEndpointTest extends BaseControllerTest {

    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateSupplyRepository createSupplyRepository;
    @Autowired
    private CreateMembershipService createMembershipService;

    @Test
    void theDetailEndpointReportsFullCapabilitiesToACommunityAdmin() throws Exception {
        Supply supply = persistSupplyOwnedBy(persistMember());
        String adminToken = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);

        mockMvc.perform(get("/api/v1/supplies/{supplyId}", supply.getId())
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capabilities.canRead").value(true))
                .andExpect(jsonPath("$.capabilities.canEdit").value(true))
                .andExpect(jsonPath("$.capabilities.canReadPartitionCoefficients").value(true))
                .andExpect(jsonPath("$.capabilities.canCreatePlant").value(true));
    }

    /**
     * The owner can see their supply and the coefficients describing their own share, but changing
     * the supply and hanging a plant off it are administrative.
     */
    @Test
    void theDetailEndpointNarrowsTheCapabilitiesForTheOwner() throws Exception {
        User owner = persistMember();
        Supply supply = persistSupplyOwnedBy(owner);

        mockMvc.perform(get("/api/v1/supplies/{supplyId}", supply.getId())
                        .header(HttpHeaders.AUTHORIZATION, loginUser(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capabilities.canRead").value(true))
                .andExpect(jsonPath("$.capabilities.canReadPartitionCoefficients").value(true))
                .andExpect(jsonPath("$.capabilities.canEdit").value(false))
                .andExpect(jsonPath("$.capabilities.canCreatePlant").value(false));
    }

    @Test
    void theListingCarriesCapabilitiesOnEveryItem() throws Exception {
        persistSupplyOwnedBy(persistMember());
        persistSupplyOwnedBy(persistMember());
        String adminToken = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);

        mockMvc.perform(get("/api/v1/communities/{communityId}/supplies", DEFAULT_COMMUNITY_ID)
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].capabilities.canEdit").value(true))
                .andExpect(jsonPath("$.items[1].capabilities.canEdit").value(true));
    }

    /**
     * A non-admin member's listing is already narrowed to their own supplies, so the capabilities
     * they get back are the owner's, not an admin's.
     */
    @Test
    void theListingReportsTheOwnerCapabilitiesToANonAdminMember() throws Exception {
        User owner = persistMember();
        persistSupplyOwnedBy(owner);

        mockMvc.perform(get("/api/v1/communities/{communityId}/supplies", DEFAULT_COMMUNITY_ID)
                        .header(HttpHeaders.AUTHORIZATION, loginUser(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].capabilities.canRead").value(true))
                .andExpect(jsonPath("$.items[0].capabilities.canEdit").value(false));
    }

    private Supply persistSupplyOwnedBy(User owner) {
        return createSupplyRepository.create(SupplyMother.random().build(), UserId.of(owner.getId()));
    }

    private User persistMember() {
        User user = UserMother.randomUser();
        user.enable();
        createUserRepository.create(user);
        createMembershipService.create(DEFAULT_COMMUNITY_ID, user.getId(), CommunityRole.COMMUNITY_MEMBER);
        return user;
    }
}

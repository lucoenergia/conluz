package org.lucoenergia.conluz.infrastructure.production.plant;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.membership.CreateMembershipService;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.plant.PlantMother;
import org.lucoenergia.conluz.domain.production.plant.create.CreatePlantRepository;
import org.lucoenergia.conluz.domain.shared.SupplyId;
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
 * The plant capabilities as a client receives them, and in particular {@code canReadSupply}.
 *
 * <p>That one exists because {@code PlantResponse.supply} was narrowed to a reference: listing
 * plants is open to any member, but the supply behind one is not, so the reference carries no owner
 * and the plant says instead whether following it would succeed. Its whole point is that it differs
 * between callers who see the same plant, which is what these tests assert.</p>
 */
@Transactional
class PlantCapabilitiesEndpointTest extends BaseControllerTest {

    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateSupplyRepository createSupplyRepository;
    @Autowired
    private CreatePlantRepository createPlantRepository;
    @Autowired
    private CreateMembershipService createMembershipService;

    @Test
    void theDetailEndpointReportsFullCapabilitiesToACommunityAdmin() throws Exception {
        Plant plant = persistPlantOwnedBy(persistMember());
        String adminToken = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);

        mockMvc.perform(get("/api/v1/plants/{plantId}", plant.getId())
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capabilities.canRead").value(true))
                .andExpect(jsonPath("$.capabilities.canManage").value(true))
                .andExpect(jsonPath("$.capabilities.canListSharingAgreements").value(true))
                .andExpect(jsonPath("$.capabilities.canManageSharingAgreements").value(true))
                .andExpect(jsonPath("$.capabilities.canReadSupply").value(true));
    }

    /**
     * A member may read the plant and sees the supply reference on it, but must not be able to open
     * that supply -- which is exactly the leak the narrowing closed, now stated positively.
     */
    @Test
    void aPlainMemberIsToldTheyMayNotOpenTheReferencedSupply() throws Exception {
        Plant plant = persistPlantOwnedBy(persistMember());
        String memberToken = loginAsCommunityMember(DEFAULT_COMMUNITY_ID);

        mockMvc.perform(get("/api/v1/plants/{plantId}", plant.getId())
                        .header(HttpHeaders.AUTHORIZATION, memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.supply.id").exists())
                .andExpect(jsonPath("$.capabilities.canRead").value(true))
                .andExpect(jsonPath("$.capabilities.canReadSupply").value(false))
                .andExpect(jsonPath("$.capabilities.canManage").value(false))
                .andExpect(jsonPath("$.capabilities.canListSharingAgreements").value(false));
    }

    /**
     * The owner of the supply is an ordinary member of the community: they may not manage the plant,
     * but following its supply reference does work for them. No role conveys that combination.
     */
    @Test
    void theSupplyOwnerIsToldTheyMayOpenTheReferencedSupply() throws Exception {
        User owner = persistMember();
        Plant plant = persistPlantOwnedBy(owner);

        mockMvc.perform(get("/api/v1/plants/{plantId}", plant.getId())
                        .header(HttpHeaders.AUTHORIZATION, loginUser(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capabilities.canRead").value(true))
                .andExpect(jsonPath("$.capabilities.canReadSupply").value(true))
                .andExpect(jsonPath("$.capabilities.canManage").value(false));
    }

    @Test
    void theListingCarriesCapabilitiesOnEveryItem() throws Exception {
        persistPlantOwnedBy(persistMember());
        persistPlantOwnedBy(persistMember());
        String memberToken = loginAsCommunityMember(DEFAULT_COMMUNITY_ID);

        mockMvc.perform(get("/api/v1/communities/{communityId}/plants", DEFAULT_COMMUNITY_ID)
                        .header(HttpHeaders.AUTHORIZATION, memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].capabilities.canRead").value(true))
                .andExpect(jsonPath("$.items[0].capabilities.canReadSupply").value(false))
                .andExpect(jsonPath("$.items[1].capabilities.canRead").value(true))
                .andExpect(jsonPath("$.items[1].capabilities.canReadSupply").value(false));
    }

    private Plant persistPlantOwnedBy(User owner) {
        Supply supply = createSupplyRepository.create(SupplyMother.random().build(), UserId.of(owner.getId()));
        return createPlantRepository.create(PlantMother.random(supply).build(), SupplyId.of(supply.getId()));
    }

    private User persistMember() {
        User user = UserMother.randomUser();
        user.enable();
        createUserRepository.create(user);
        createMembershipService.create(DEFAULT_COMMUNITY_ID, user.getId(), CommunityRole.COMMUNITY_MEMBER);
        return user;
    }
}

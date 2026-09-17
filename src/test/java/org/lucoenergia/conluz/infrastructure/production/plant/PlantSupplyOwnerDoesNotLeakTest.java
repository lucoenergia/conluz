package org.lucoenergia.conluz.infrastructure.production.plant;

import org.junit.jupiter.api.Test;
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lucoenergia.conluz.infrastructure.admin.supply.create.CreateSupplyRepositoryDatabase.DEFAULT_COMMUNITY_ID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Listing a community's plants is open to any member, and reading one plant to any member of its
 * community. The supply behind a plant is not: {@code GET /supplies/{supplyId}} answers 404 to
 * anyone who neither administers the community nor owns the supply, and {@code GET /users/{userId}}
 * does the same for its owner.
 *
 * <p>So a plant response must not carry the supply's owner. It used to: {@code supply} was a full
 * {@code SupplyResponse}, which embeds a full {@code UserResponse}, and a plain member listing
 * plants received every other owner's DNI, email, phone number and address. These tests assert on
 * the raw body, so they fail whatever the fields end up being called or nested under.</p>
 */
@Transactional
class PlantSupplyOwnerDoesNotLeakTest extends BaseControllerTest {

    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateSupplyRepository createSupplyRepository;
    @Autowired
    private CreatePlantRepository createPlantRepository;

    @Test
    void thePlantListingOfACommunityNeverCarriesTheSupplyOwner() throws Exception {
        Plant plant = persistPlantOwnedBySomebodyElse();
        String memberToken = loginAsCommunityMember(DEFAULT_COMMUNITY_ID);

        String body = mockMvc.perform(get("/api/v1/communities/{communityId}/plants", DEFAULT_COMMUNITY_ID)
                        .header(HttpHeaders.AUTHORIZATION, memberToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertTrue(body.contains(plant.getSupply().getCode()), "the supply reference itself must stay: " + body);
        assertNoOwnerIn(body);
    }

    @Test
    void thePlantDetailNeverCarriesTheSupplyOwner() throws Exception {
        Plant plant = persistPlantOwnedBySomebodyElse();
        String memberToken = loginAsCommunityMember(DEFAULT_COMMUNITY_ID);

        String body = mockMvc.perform(get("/api/v1/plants/{plantId}", plant.getId())
                        .header(HttpHeaders.AUTHORIZATION, memberToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertTrue(body.contains(plant.getSupply().getCode()), "the supply reference itself must stay: " + body);
        assertNoOwnerIn(body);
    }

    /**
     * The owner's own fields, not merely the {@code user} key: a response that dropped the wrapper
     * but inlined {@code personalId} would pass a check for the key alone.
     */
    private void assertNoOwnerIn(String body) {
        assertFalse(body.contains("\"user\""), "the supply owner must not be embedded: " + body);
        assertFalse(body.contains("personalId"), "the owner's DNI must not be embedded: " + body);
        assertFalse(body.contains("phoneNumber"), "the owner's phone must not be embedded: " + body);
        assertFalse(body.contains("memberships"), "the owner's memberships must not be embedded: " + body);
    }

    private Plant persistPlantOwnedBySomebodyElse() {
        User owner = UserMother.randomUser();
        createUserRepository.create(owner);
        Supply supply = createSupplyRepository.create(SupplyMother.random().build(), UserId.of(owner.getId()));
        Plant plant = PlantMother.random(supply).build();
        return createPlantRepository.create(plant, SupplyId.of(supply.getId()));
    }
}

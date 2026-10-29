package org.lucoenergia.conluz.infrastructure.admin.supply;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.membership.CreateMembershipRepository;
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
import org.lucoenergia.conluz.infrastructure.admin.supply.update.UpdateSupplyBody;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lucoenergia.conluz.domain.admin.community.CommunityMother.DEFAULT_COMMUNITY_ID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SUP-004 on every operation that answers a supply: the plant whose own supply it is, or null.
 */
@Transactional
class SupplyResponsePlantControllerTest extends BaseControllerTest {

    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateMembershipRepository createMembershipRepository;
    @Autowired
    private CreateSupplyRepository createSupplyRepository;
    @Autowired
    private CreatePlantRepository createPlantRepository;

    @Test
    @DisplayName("SUP-004 getSupply references the plant of a plant's own supply")
    void getSupplyReferencesThePlantOfAPlantsOwnSupply() throws Exception {
        Supply supply = createSupply(createMember());
        Plant plant = createPlantOn(supply);

        assertReferencesPlant(perform(get("/api/v1/supplies/" + supply.getId())), plant);
    }

    @Test
    @DisplayName("SUP-004 getSupply answers a null plant for a supply that is no plant's own supply")
    void getSupplyAnswersANullPlantForARegularSupply() throws Exception {
        Supply supply = createSupply(createMember());

        perform(get("/api/v1/supplies/" + supply.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plant").isEmpty());
    }

    @Test
    @DisplayName("SUP-004 getAllSupplies references each supply's own plant, and null for the others")
    void getAllSuppliesReferencesEachSuppliesOwnPlant() throws Exception {
        Supply plantSupply = createSupply(createMember());
        Plant plant = createPlantOn(plantSupply);
        Supply regularSupply = createSupply(createMember());

        Map<String, JsonNode> plants = plantsById(perform(get("/api/v1/communities/" + DEFAULT_COMMUNITY_ID
                + "/supplies?size=1000")), "$.items");

        assertEquals(plant.getId().toString(), plants.get(plantSupply.getId().toString()).get("id").asText());
        assertEquals(plant.getName(), plants.get(plantSupply.getId().toString()).get("name").asText());
        assertTrue(plants.get(regularSupply.getId().toString()).isNull());
    }

    @Test
    @DisplayName("SUP-004 getSuppliesByUserId references each supply's own plant, and null for the others")
    void getSuppliesByUserIdReferencesEachSuppliesOwnPlant() throws Exception {
        User owner = createMember();
        Supply plantSupply = createSupply(owner);
        Plant plant = createPlantOn(plantSupply);
        Supply regularSupply = createSupply(owner);

        Map<String, JsonNode> plants = plantsById(perform(get("/api/v1/users/" + owner.getId() + "/supplies")), "$");

        assertEquals(2, plants.size());
        assertEquals(plant.getId().toString(), plants.get(plantSupply.getId().toString()).get("id").asText());
        assertEquals(plant.getName(), plants.get(plantSupply.getId().toString()).get("name").asText());
        assertTrue(plants.get(regularSupply.getId().toString()).isNull());
    }

    @Test
    @DisplayName("SUP-004 updateSupply references the plant of a plant's own supply")
    void updateSupplyReferencesThePlantOfAPlantsOwnSupply() throws Exception {
        Supply supply = createSupply(createMember());
        Plant plant = createPlantOn(supply);
        UpdateSupplyBody body = new UpdateSupplyBody();
        body.setCode(supply.getCode());
        body.setName("Renamed");
        body.setAddress(supply.getAddress());

        assertReferencesPlant(perform(put("/api/v1/supplies/" + supply.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))), plant);
    }

    @Test
    @DisplayName("SUP-004 enableSupply and disableSupply reference the plant of a plant's own supply")
    void enableAndDisableSupplyReferenceThePlantOfAPlantsOwnSupply() throws Exception {
        Supply supply = createSupply(createMember());
        Plant plant = createPlantOn(supply);

        assertReferencesPlant(perform(post("/api/v1/supplies/" + supply.getId() + "/disable")), plant);
        assertReferencesPlant(perform(post("/api/v1/supplies/" + supply.getId() + "/enable")), plant);
    }

    @Test
    @DisplayName("SUP-004 createSupply answers a null plant, since a new supply is no plant's own supply yet")
    void createSupplyAnswersANullPlant() throws Exception {
        User owner = createMember();
        String body = String.format("""
                {
                  "code": "ES0077777777777777AA0A",
                  "communityId": "%s",
                  "personalId": "%s",
                  "address": "Fake Street 123"
                }
                """, DEFAULT_COMMUNITY_ID, owner.getPersonalId());

        perform(post("/api/v1/supplies").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plant").isEmpty());
    }

    private ResultActions perform(MockHttpServletRequestBuilder request)
            throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID)));
    }

    private static void assertReferencesPlant(ResultActions result, Plant plant) throws Exception {
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.plant.id").value(plant.getId().toString()))
                .andExpect(jsonPath("$.plant.name").value(plant.getName()));
    }

    private Map<String, JsonNode> plantsById(ResultActions result, String path) throws Exception {
        JsonNode root = objectMapper.readTree(result.andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        JsonNode items = "$".equals(path) ? root : root.get("items");
        Map<String, JsonNode> plants = new HashMap<>();
        for (JsonNode supply : items) {
            plants.put(supply.get("id").asText(), supply.get("plant"));
        }
        return plants;
    }

    private User createMember() {
        User user = UserMother.randomUser();
        createUserRepository.create(user);
        createMembershipRepository.create(DEFAULT_COMMUNITY_ID, user.getId(), CommunityRole.COMMUNITY_MEMBER);
        return user;
    }

    private Supply createSupply(User owner) {
        return createSupplyRepository.create(SupplyMother.random().build(), UserId.of(owner.getId()),
                DEFAULT_COMMUNITY_ID);
    }

    private Plant createPlantOn(Supply supply) {
        return createPlantRepository.create(PlantMother.random(supply).build(), SupplyId.of(supply.getId()));
    }
}

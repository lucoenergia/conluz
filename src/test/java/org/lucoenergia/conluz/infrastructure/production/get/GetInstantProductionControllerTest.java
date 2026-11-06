package org.lucoenergia.conluz.infrastructure.production.get;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import org.lucoenergia.conluz.infrastructure.production.EnergyProductionInfluxLoader;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.lucoenergia.conluz.domain.admin.community.CommunityMother.DEFAULT_COMMUNITY_ID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class GetInstantProductionControllerTest extends BaseControllerTest {

    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateSupplyRepository createSupplyRepository;
    @Autowired
    private CreatePlantRepository createPlantRepository;
    @Autowired
    private EnergyProductionInfluxLoader energyProductionInfluxLoader;

    @BeforeEach
    void beforeEach() {
        energyProductionInfluxLoader.loadData();
    }

    @BeforeEach
    void afterEach() {
        energyProductionInfluxLoader.clearData();
    }

    @Test
    void testGetInstantProduction() throws Exception {
        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);

        mockMvc.perform(get("/api/v1/communities/" + DEFAULT_COMMUNITY_ID + "/production")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("power")));
    }

    @Test
    void testGetInstantProduction_whenPlatformAdminNotMember_thenForbidden() throws Exception {
        String authHeader = loginAsDefaultPlatformAdmin();

        mockMvc.perform(get("/api/v1/communities/" + DEFAULT_COMMUNITY_ID + "/production")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isForbidden());
    }

    @Test
    void testGetInstantProductionBySupply() throws Exception {

        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);
        User user = createUserRepository.create(UserMother.randomUser());

        // Only the backing supply of a plant of the community is accepted as supplyId (CPR-001).
        Supply plantSupply = createSupplyRepository.create(SupplyMother.random().build(), UserId.of(user.getId()), DEFAULT_COMMUNITY_ID);
        createPlantRepository.create(PlantMother.random(plantSupply).build(), SupplyId.of(plantSupply.getId()));
        UUID supplyId = plantSupply.getId();

        // Create some other supplies
        createSupplyRepository.create(SupplyMother.random().build(), UserId.of(user.getId()), DEFAULT_COMMUNITY_ID);
        createSupplyRepository.create(SupplyMother.random().build(), UserId.of(user.getId()), DEFAULT_COMMUNITY_ID);

        mockMvc.perform(get("/api/v1/communities/" + DEFAULT_COMMUNITY_ID + "/production")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .param("supplyId", supplyId.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("power")));
    }

    @Test
    void testGetInstantProductionByUnknownSupply() throws Exception {

        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);
        UUID supplyId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/communities/" + DEFAULT_COMMUNITY_ID + "/production")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .param("supplyId", supplyId.toString()))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("\"traceId\":")))
                .andExpect(content().string(containsString("\"timestamp\":")))
                .andExpect(content().string(containsString("\"status\":404")))
                .andExpect(content().string(containsString(String.format("\"message\":\"El punto de suministro con identificador '%s' no ha sido encontrado. Revise que el identificador sea correcto.\"", supplyId))));
    }

    @Test
    void testGetInstantProductionWithWrongParameter() throws Exception {

        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);
        String supplyId = "1";

        mockMvc.perform(get("/api/v1/communities/" + DEFAULT_COMMUNITY_ID + "/production")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .param("supply", supplyId))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("power")));
    }
    @Test
    void testGetInstantProduction_whenNoToken_thenUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/communities/" + DEFAULT_COMMUNITY_ID + "/production"))
                .andExpect(status().isUnauthorized());
    }
}

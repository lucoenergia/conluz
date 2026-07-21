package org.lucoenergia.conluz.infrastructure.production.plant.create;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.create.CreateCommunityRepository;
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
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class CreateSharingAgreementControllerTest extends BaseControllerTest {

    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateCommunityRepository createCommunityRepository;
    @Autowired
    private CreateSupplyRepository createSupplyRepository;
    @Autowired
    private CreatePlantRepository createPlantRepository;

    private Community communityA;
    private Community communityB;
    private Plant plantA;

    @BeforeEach
    void setUp() {
        communityA = createCommunityRepository.create(CommunityMother.random().build());
        communityB = createCommunityRepository.create(CommunityMother.random().build());
        plantA = createPlant(communityA);
    }

    @Test
    void returnsUnauthorizedWithoutToken() throws Exception {
        mockMvc.perform(post(url(plantA.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"2024 winter distribution\"}"))
                .andDo(print())
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returnsForbiddenForCommunityMember() throws Exception {
        String authHeader = loginAsCommunityMember(communityA.getId());

        mockMvc.perform(post(url(plantA.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"2024 winter distribution\"}"))
                .andDo(print())
                .andExpect(status().isForbidden());
    }

    @Test
    void returnsNotFoundForCrossCommunityAdmin() throws Exception {
        // An admin of a different community cannot see plantA -> 404, not 403.
        String authHeader = loginAsCommunityAdmin(communityB.getId());

        mockMvc.perform(post(url(plantA.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"2024 winter distribution\"}"))
                .andDo(print())
                .andExpect(status().isNotFound());
    }

    @Test
    void returnsBadRequestWhenNameIsBlank() throws Exception {
        String authHeader = loginAsCommunityAdmin(communityA.getId());

        mockMvc.perform(post(url(plantA.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    @Test
    void createsDraftAgreementWithSnapshottedPowerAndCreator() throws Exception {
        String authHeader = loginAsCommunityAdmin(communityA.getId());

        mockMvc.perform(post(url(plantA.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"2024 winter distribution\",\"notes\":\"initial\"}"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plantId").value(plantA.getId().toString()))
                .andExpect(jsonPath("$.name").value("2024 winter distribution"))
                .andExpect(jsonPath("$.notes").value("initial"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.installedPowerKw").value(BigDecimal.valueOf(plantA.getTotalPower()).doubleValue()))
                .andExpect(jsonPath("$.createdBy").isNotEmpty());
    }

    private Plant createPlant(Community community) {
        User owner = UserMother.randomUser();
        createUserRepository.create(owner);
        Supply supply = SupplyMother.random(owner).build();
        supply = createSupplyRepository.create(supply, UserId.of(owner.getId()), community.getId());
        Plant plant = PlantMother.random(supply).build();
        return createPlantRepository.create(plant, SupplyId.of(supply.getId()));
    }

    private String url(java.util.UUID plantId) {
        return "/api/v1/plants/" + plantId + "/sharing-agreements";
    }
}

package org.lucoenergia.conluz.infrastructure.production.plant.delete;

import org.junit.jupiter.api.Assertions;
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
import org.lucoenergia.conluz.domain.production.plant.get.GetPlantRepository;
import org.lucoenergia.conluz.domain.shared.PlantId;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class DeletePlantControllerTest extends BaseControllerTest {

    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateSupplyRepository createSupplyRepository;
    @Autowired
    private CreatePlantRepository createPlantRepository;
    @Autowired
    private GetPlantRepository getPlantRepository;

    @Test
    void testDelete() throws Exception {

        // Create two users
        User userOne = UserMother.randomUser();
        createUserRepository.create(userOne);
        Supply supplyOne = SupplyMother.random().build();
        supplyOne = createSupplyRepository.create(supplyOne, UserId.of(userOne.getId()));
        User userTwo = UserMother.randomUser();
        createUserRepository.create(userTwo);
        Supply supplyTwo = SupplyMother.random().build();
        supplyTwo = createSupplyRepository.create(supplyTwo, UserId.of(userTwo.getId()));

        // Create three supplies
        Plant plantOne = PlantMother.random(supplyOne).withCode("TS-456789").build();
        createPlantRepository.create(plantOne, SupplyId.of(supplyOne.getId()));
        Plant plantTwo = PlantMother.random(supplyOne).withCode("TS-123456").build();
        plantTwo = createPlantRepository.create(plantTwo, SupplyId.of(supplyOne.getId()));
        Plant plantThree = PlantMother.random(supplyTwo).withCode("TS-789456").build();
        createPlantRepository.create(plantThree, SupplyId.of(supplyTwo.getId()));

        // Login as default admin
        String authHeader = loginAsDefaultAdmin();

        mockMvc.perform(delete(String.format("/api/v1/plants/%s", plantTwo.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
        Assertions.assertTrue(getPlantRepository.findById(PlantId.of(plantTwo.getId())).isEmpty());
    }

    @Test
    void testWithUnknown() throws Exception {

        String authHeader = loginAsDefaultAdmin();

        final String plantId = UUID.randomUUID().toString();

        mockMvc.perform(delete("/api/v1/plants/" + plantId)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void testWithoutIdInPath() throws Exception {
        final String authHeader = loginAsDefaultAdmin();

        mockMvc.perform(delete("/api/v1/plants")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void testWithoutToken() throws Exception {

        mockMvc.perform(delete("/api/v1/plants")
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(HttpStatus.UNAUTHORIZED.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }
}

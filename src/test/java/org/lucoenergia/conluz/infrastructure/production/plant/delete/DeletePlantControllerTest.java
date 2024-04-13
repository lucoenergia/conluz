package org.lucoenergia.conluz.infrastructure.production.plant.delete;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.plant.PlantMother;
import org.lucoenergia.conluz.domain.production.plant.create.CreatePlantRepository;
import org.lucoenergia.conluz.domain.production.plant.get.GetPlantRepository;
import org.lucoenergia.conluz.domain.shared.PlantId;
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
    private CreatePlantRepository createPlantRepository;
    @Autowired
    private GetPlantRepository getPlantRepository;

    @Test
    void testDelete() throws Exception {

        // Create two users
        User userOne = UserMother.randomUser();
        createUserRepository.create(userOne);
        User userTwo = UserMother.randomUser();
        createUserRepository.create(userTwo);

        // Create three supplies
        Plant plantOne = PlantMother.random(userOne).withCode("TS-456789").build();
        createPlantRepository.create(plantOne, UserId.of(userOne.getId()));
        Plant plantTwo = PlantMother.random(userOne).withCode("TS-123456").build();
        plantTwo = createPlantRepository.create(plantTwo, UserId.of(userOne.getId()));
        Plant plantThree = PlantMother.random(userTwo).withCode("TS-789456").build();
        createPlantRepository.create(plantThree, UserId.of(userTwo.getId()));

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

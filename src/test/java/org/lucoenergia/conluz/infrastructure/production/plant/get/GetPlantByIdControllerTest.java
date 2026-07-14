package org.lucoenergia.conluz.infrastructure.production.plant.get;

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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.UUID;

import static org.lucoenergia.conluz.infrastructure.admin.supply.create.CreateSupplyRepositoryDatabase.DEFAULT_COMMUNITY_ID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class GetPlantByIdControllerTest extends BaseControllerTest {

    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateSupplyRepository createSupplyRepository;
    @Autowired
    private CreatePlantRepository createPlantRepository;

    @Test
    void testGetPlantById_shouldReturnPlant() throws Exception {

        // Create user, supply, and plant
        User user = UserMother.randomUser();
        createUserRepository.create(user);
        Supply supply = SupplyMother.random().build();
        supply = createSupplyRepository.create(supply, UserId.of(user.getId()));
        Plant plant = PlantMother.random(supply).build();
        plant = createPlantRepository.create(plant, SupplyId.of(supply.getId()));

        // Login as an admin of the plant's community
        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);

        mockMvc.perform(get(String.format("/api/v1/plants/%s", plant.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(plant.getId().toString()))
                .andExpect(jsonPath("$.providerCode").value(plant.getProviderCode()))
                .andExpect(jsonPath("$.name").value(plant.getName()))
                .andExpect(jsonPath("$.address").value(plant.getAddress()))
                .andExpect(jsonPath("$.description").value(plant.getDescription()))
                .andExpect(jsonPath("$.inverterProvider").value(plant.getInverterProvider().name()))
                .andExpect(jsonPath("$.totalPower").value(plant.getTotalPower()))
                .andExpect(jsonPath("$.connectionDate").value(plant.getConnectionDate().format(DateTimeFormatter.ISO_DATE)))
                .andExpect(jsonPath("$.supply.id").value(supply.getId().toString()))
                .andExpect(jsonPath("$.supply.code").value(supply.getCode()));
    }

    @Test
    void testGetPlantById_shouldReturnNotFoundWhenPlantDoesNotExist() throws Exception {
        // A non-existent plant returns 404 (not 403) so callers cannot probe plant existence by ID.
        String authHeader = loginAsPartner();

        final String plantId = UUID.randomUUID().toString();

        mockMvc.perform(get("/api/v1/plants/" + plantId)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(HttpStatus.NOT_FOUND.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void testGetPlantById_shouldReturnBadRequestWhenIdIsInvalid() throws Exception {
        String authHeader = loginAsDefaultPlatformAdmin();

        mockMvc.perform(get("/api/v1/plants/invalid-uuid")
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
    void testGetPlantById_shouldReturnUnauthorizedWhenNoToken() throws Exception {

        mockMvc.perform(get("/api/v1/plants/" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(HttpStatus.UNAUTHORIZED.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void testGetPlantById_shouldReturnPlantForRegularCommunityMember() throws Exception {

        // Create user, supply, and plant
        User user = UserMother.randomUser();
        createUserRepository.create(user);
        Supply supply = SupplyMother.random().build();
        supply = createSupplyRepository.create(supply, UserId.of(user.getId()));
        Plant plant = PlantMother.random(supply).build();
        plant = createPlantRepository.create(plant, SupplyId.of(supply.getId()));

        // A regular (non-admin) member of the plant's community can read it
        String authHeader = loginAsCommunityMember(DEFAULT_COMMUNITY_ID);

        mockMvc.perform(get(String.format("/api/v1/plants/%s", plant.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(plant.getId().toString()))
                .andExpect(jsonPath("$.providerCode").value(plant.getProviderCode()));
    }

    @Test
    void testGetPlantById_shouldReturnNotFoundWhenUserIsNotMember() throws Exception {

        // Create user, supply, and plant
        User user = UserMother.randomUser();
        createUserRepository.create(user);
        Supply supply = SupplyMother.random().build();
        supply = createSupplyRepository.create(supply, UserId.of(user.getId()));
        Plant plant = PlantMother.random(supply).build();
        plant = createPlantRepository.create(plant, SupplyId.of(supply.getId()));

        // A user who is not a member of the plant's community gets 404, not 403, so the existence
        // of the plant is not leaked.
        String authHeader = loginAsPartner();

        mockMvc.perform(get(String.format("/api/v1/plants/%s", plant.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(HttpStatus.NOT_FOUND.value()));
    }
}

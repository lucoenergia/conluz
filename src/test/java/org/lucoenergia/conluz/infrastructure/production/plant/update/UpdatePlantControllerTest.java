package org.lucoenergia.conluz.infrastructure.production.plant.update;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.production.InverterProvider;
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

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import static org.lucoenergia.conluz.infrastructure.admin.supply.create.CreateSupplyRepositoryDatabase.DEFAULT_COMMUNITY_ID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class UpdatePlantControllerTest extends BaseControllerTest {

    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateSupplyRepository createSupplyRepository;
    @Autowired
    private CreatePlantRepository createPlantRepository;

    @Test
    void testUpdateAllFields() throws Exception {

        User userOne = UserMother.randomUser();
        createUserRepository.create(userOne);
        Supply supplyOne = SupplyMother.random().build();
        supplyOne = createSupplyRepository.create(supplyOne, UserId.of(userOne.getId()));
        User userTwo = UserMother.randomUser();
        createUserRepository.create(userTwo);
        Supply supplyTwo = SupplyMother.random().build();
        supplyTwo = createSupplyRepository.create(supplyTwo, UserId.of(userTwo.getId()));

        Plant plantOne = PlantMother.random(supplyOne).withProviderCode("TS-456789").build();
        plantOne = createPlantRepository.create(plantOne, SupplyId.of(supplyOne.getId()));

        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);

        // Modify data of the plant
        UpdatePlantBody plantModified = new UpdatePlantBody();
        plantModified.setProviderCode("TS-234123");
        plantModified.setRegulatoryCode("ES0021000000000001JN0F");
        plantModified.setSupplyCode(supplyTwo.getCode());
        plantModified.setName("Main plant");
        plantModified.setAddress("Fake Street 666");
        plantModified.setDescription("The main plant");
        plantModified.setConnectionDate(LocalDate.of(2023, 5, 23));
        plantModified.setInverterProvider(InverterProvider.HUAWEI);
        plantModified.setTotalPower(25.6D);

        String bodyAsString = objectMapper.writeValueAsString(plantModified);

        mockMvc.perform(put(String.format("/api/v1/plants/%s", plantOne.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyAsString))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(plantOne.getId().toString()))
                .andExpect(jsonPath("$.providerCode").value(plantModified.getProviderCode()))
                .andExpect(jsonPath("$.regulatoryCode").value(plantModified.getRegulatoryCode()))
                .andExpect(jsonPath("$.supply.code").value(plantModified.getSupplyCode()))
                .andExpect(jsonPath("$.name").value(plantModified.getName()))
                .andExpect(jsonPath("$.description").value(plantModified.getDescription()))
                .andExpect(jsonPath("$.connectionDate").value(plantModified.getConnectionDate().format(DateTimeFormatter.ISO_DATE)))
                .andExpect(jsonPath("$.inverterProvider").value(plantModified.getInverterProvider().name()))
                .andExpect(jsonPath("$.totalPower").value(plantModified.getTotalPower()));
    }

    @Test
    void testOmittingRegulatoryCodeNullsItOut() throws Exception {

        // PUT is a full replace: omitting an optional field in the body clears it, same as the
        // existing behavior for description/connectionDate.
        User user = UserMother.randomUser();
        createUserRepository.create(user);
        Supply supply = SupplyMother.random().build();
        supply = createSupplyRepository.create(supply, UserId.of(user.getId()));

        Plant plant = PlantMother.random(supply)
                .withProviderCode("TS-987654")
                .withRegulatoryCode("ES0021000000000001JN0F")
                .build();
        plant = createPlantRepository.create(plant, SupplyId.of(supply.getId()));

        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);

        UpdatePlantBody plantModified = new UpdatePlantBody();
        plantModified.setProviderCode("TS-987654");
        plantModified.setSupplyCode(supply.getCode());
        plantModified.setName("Main plant");
        plantModified.setAddress("Fake Street 666");
        plantModified.setTotalPower(25.6D);
        plantModified.setInverterProvider(InverterProvider.HUAWEI);

        String bodyAsString = objectMapper.writeValueAsString(plantModified);

        mockMvc.perform(put(String.format("/api/v1/plants/%s", plant.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyAsString))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.regulatoryCode").isEmpty());
    }

    @Test
    void testWithMissingNotRequiredFields() throws Exception {

        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);

        User userOne = UserMother.randomUser();
        createUserRepository.create(userOne);
        Supply supplyOne = SupplyMother.random().build();
        supplyOne = createSupplyRepository.create(supplyOne, UserId.of(userOne.getId()));
        User userTwo = UserMother.randomUser();
        createUserRepository.create(userTwo);
        Supply supplyTwo = SupplyMother.random().build();
        supplyTwo = createSupplyRepository.create(supplyTwo, UserId.of(userTwo.getId()));

        Plant plantOne = PlantMother.random(supplyOne).withProviderCode("TS-456789").build();
        plantOne = createPlantRepository.create(plantOne, SupplyId.of(supplyOne.getId()));
        Plant plantTwo = PlantMother.random(supplyOne).withProviderCode("TS-123456").build();
        createPlantRepository.create(plantTwo, SupplyId.of(supplyOne.getId()));
        Plant plantThree = PlantMother.random(supplyTwo).withProviderCode("TS-789456").build();
        createPlantRepository.create(plantThree, SupplyId.of(supplyTwo.getId()));

        UpdatePlantBody plantModified = new UpdatePlantBody();
        plantModified.setProviderCode("TS-234123");
        plantModified.setName("Main plant");
        plantModified.setAddress("Fake Street 666");
        plantModified.setTotalPower(25.6D);
        plantModified.setSupplyCode(supplyTwo.getCode());
        plantModified.setInverterProvider(InverterProvider.HUAWEI);
        String bodyAsString = objectMapper.writeValueAsString(plantModified);

        mockMvc.perform(put(String.format("/api/v1/plants/%s", plantOne.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyAsString))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(plantOne.getId().toString()))
                .andExpect(jsonPath("$.providerCode").value(plantModified.getProviderCode()))
                .andExpect(jsonPath("$.supply.code").value(plantModified.getSupplyCode()))
                .andExpect(jsonPath("$.name").value(plantModified.getName()))
                .andExpect(jsonPath("$.description").isEmpty())
                .andExpect(jsonPath("$.connectionDate").isEmpty())
                .andExpect(jsonPath("$.inverterProvider").value(plantOne.getInverterProvider().name()))
                .andExpect(jsonPath("$.totalPower").value(plantModified.getTotalPower()));
    }

    @Test
    void testWithUnknownPlant() throws Exception {

        String authHeader = loginAsDefaultPlatformAdmin();

        final String plantId = UUID.randomUUID().toString();

        UpdatePlantBody plantModified = new UpdatePlantBody();
        plantModified.setProviderCode("TS-234123");
        plantModified.setName("Main plant");
        plantModified.setAddress("Fake Street 666");
        plantModified.setTotalPower(25.6D);
        String bodyAsString = objectMapper.writeValueAsString(plantModified);

        mockMvc.perform(put("/api/v1/plants/" + plantId)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyAsString))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void testWithUnknownUser() throws Exception {

        User userOne = UserMother.randomUser();
        createUserRepository.create(userOne);
        Supply supplyOne = SupplyMother.random().build();
        supplyOne = createSupplyRepository.create(supplyOne, UserId.of(userOne.getId()));

        // Create three supplies
        Plant plantOne = PlantMother.random(supplyOne).withProviderCode("TS-456789").build();
        plantOne = createPlantRepository.create(plantOne, SupplyId.of(supplyOne.getId()));

        String authHeader = loginAsDefaultPlatformAdmin();

        UpdatePlantBody plantModified = new UpdatePlantBody();
        plantModified.setProviderCode("TS-234123");
        plantModified.setName("Main plant");
        plantModified.setAddress("Fake Street 666");
        plantModified.setTotalPower(25.6D);
        plantModified.setSupplyCode("unknown");
        String bodyAsString = objectMapper.writeValueAsString(plantModified);

        mockMvc.perform(put("/api/v1/plants/" + plantOne.getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyAsString))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void testWithUnknownFields() throws Exception {

        final String body = """
                        {
                          "unknown": 1,
                          "providerCode": "TS-234123",
                          "name": "Main plant",
                          "address": "Fake Street 666",
                          "totalPower": "25.6"
                        }
                """;

        final String authHeader = loginAsDefaultPlatformAdmin();

        final String plantId = UUID.randomUUID().toString();

        mockMvc.perform(put("/api/v1/plants/" + plantId)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @ParameterizedTest
    @MethodSource("getBodyWithMissingRequiredFields")
    void testMissingRequiredFields(String body) throws Exception {

        final String authHeader = loginAsDefaultPlatformAdmin();

        final String plantId = UUID.randomUUID().toString();

        mockMvc.perform(put("/api/v1/plants/" + plantId)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    static List<String> getBodyWithMissingRequiredFields() {
        return List.of(
                """
                                {
                                  "name": "Main plant",
                                  "address": "Fake Street 666",
                                  "totalPower": "25.6"
                                }
                        """,
                """
                                {
                                  "providerCode": "TS-234123",
                                  "address": "Fake Street 666",
                                  "totalPower": "25.6"
                                }
                        """,
                """
                                {
                                  "providerCode": "TS-234123",
                                  "name": "Main plant",
                                  "totalPower": "25.6"
                                }
                        """,
                """
                                {
                                  "providerCode": "TS-234123",
                                  "name": "Main plant",
                                  "address": "Fake Street 666",
                                }
                        """);
    }

    @ParameterizedTest
    @MethodSource("getBodyWithInvalidFormatValues")
    void testWithInvalidFormatValues(String body) throws Exception {

        final String authHeader = loginAsDefaultPlatformAdmin();

        final String plantId = UUID.randomUUID().toString();

        mockMvc.perform(put("/api/v1/plants/" + plantId)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    static List<String> getBodyWithInvalidFormatValues() {
        return List.of("""
                            {
                              "providerCode": "TS-1234124",
                              "name": "Plant one",
                              "personalId": "12345678Z",
                              "address": "Fake Street 123",
                              "totalPower": "foo",
                              "inverterProvider": "HUAWEI"
                            }
                        """,
                """
                            {
                              "providerCode": "TS-1234124",
                              "name": "Plant one",
                              "personalId": "12345678Z",
                              "address": "Fake Street 123",
                              "totalPower": "60.00",
                              "inverterProvider": "foo"
                            }
                        """,
                """
                            {
                              "providerCode": "TS-1234124",
                              "name": "Plant one",
                              "personalId": "12345678Z",
                              "address": "Fake Street 123",
                              "totalPower": "60.00",
                              "connectionDate": "2024/05/23",
                              "inverterProvider": "HUAWEI"
                            }
                        """);
    }

    @Test
    void testWithoutBody() throws Exception {
        final String authHeader = loginAsDefaultPlatformAdmin();

        final String plantId = UUID.randomUUID().toString();

        mockMvc.perform(put("/api/v1/plants/" + plantId)
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
        final String authHeader = loginAsDefaultPlatformAdmin();

        mockMvc.perform(put("/api/v1/plants")
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

        mockMvc.perform(put("/api/v1/plants")
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(HttpStatus.UNAUTHORIZED.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void testAuthenticatedUserWithoutAdminRoleCannotAccess() throws Exception {

        String authHeader = loginAsPartner();

        Supply supplyTwo = SupplyMother.random().build();

        UpdatePlantBody plantModified = new UpdatePlantBody();
        plantModified.setProviderCode("TS-234123");
        plantModified.setSupplyCode(supplyTwo.getCode());
        plantModified.setName("Main plant");
        plantModified.setAddress("Fake Street 666");
        plantModified.setDescription("The main plant");
        plantModified.setConnectionDate(LocalDate.of(2023, 5, 23));
        plantModified.setInverterProvider(InverterProvider.HUAWEI);
        plantModified.setTotalPower(25.6D);

        String bodyAsString = objectMapper.writeValueAsString(plantModified);

        final String plantId = UUID.randomUUID().toString();

        mockMvc.perform(put(String.format("/api/v1/plants/%s", plantId))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyAsString))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(HttpStatus.NOT_FOUND.value()));
    }
}

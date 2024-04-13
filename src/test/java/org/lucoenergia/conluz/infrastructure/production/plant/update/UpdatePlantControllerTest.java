package org.lucoenergia.conluz.infrastructure.production.plant.update;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.production.InverterProvider;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.plant.PlantMother;
import org.lucoenergia.conluz.domain.production.plant.create.CreatePlantRepository;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class UpdatePlantControllerTest extends BaseControllerTest {

    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreatePlantRepository createPlantRepository;

    @Test
    void testUpdateAllFields() throws Exception {

        User userOne = UserMother.randomUser();
        createUserRepository.create(userOne);
        User userTwo = UserMother.randomUser();
        createUserRepository.create(userTwo);

        Plant plantOne = PlantMother.random(userOne).withCode("TS-456789").build();
        plantOne = createPlantRepository.create(plantOne, UserId.of(userOne.getId()));

        String authHeader = loginAsDefaultAdmin();

        // Modify data of the plant
        UpdatePlantBody plantModified = new UpdatePlantBody();
        plantModified.setCode("TS-234123");
        plantModified.setPersonalId(userTwo.getPersonalId());
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
                .andExpect(jsonPath("$.code").value(plantModified.getCode()))
                .andExpect(jsonPath("$.user.personalId").value(plantModified.getPersonalId()))
                .andExpect(jsonPath("$.name").value(plantModified.getName()))
                .andExpect(jsonPath("$.description").value(plantModified.getDescription()))
                .andExpect(jsonPath("$.connectionDate").value(plantModified.getConnectionDate().format(DateTimeFormatter.ISO_DATE)))
                .andExpect(jsonPath("$.inverterProvider").value(plantModified.getInverterProvider().name()))
                .andExpect(jsonPath("$.totalPower").value(plantModified.getTotalPower()));
    }

    @Test
    void testWithMissingNotRequiredFields() throws Exception {

        String authHeader = loginAsDefaultAdmin();

        // Create two users
        User userOne = UserMother.randomUser();
        createUserRepository.create(userOne);
        User userTwo = UserMother.randomUser();
        createUserRepository.create(userTwo);

        // Create three supplies
        Plant plantOne = PlantMother.random(userOne).withCode("TS-456789").build();
        plantOne = createPlantRepository.create(plantOne, UserId.of(userOne.getId()));
        Plant plantTwo = PlantMother.random(userOne).withCode("TS-123456").build();
        createPlantRepository.create(plantTwo, UserId.of(userOne.getId()));
        Plant plantThree = PlantMother.random(userTwo).withCode("TS-789456").build();
        createPlantRepository.create(plantThree, UserId.of(userTwo.getId()));

        UpdatePlantBody plantModified = new UpdatePlantBody();
        plantModified.setCode("TS-234123");
        plantModified.setName("Main plant");
        plantModified.setAddress("Fake Street 666");
        plantModified.setTotalPower(25.6D);
        plantModified.setPersonalId(userTwo.getPersonalId());
        plantModified.setInverterProvider(InverterProvider.HUAWEI);
        String bodyAsString = objectMapper.writeValueAsString(plantModified);

        mockMvc.perform(put(String.format("/api/v1/plants/%s", plantOne.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyAsString))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(plantOne.getId().toString()))
                .andExpect(jsonPath("$.code").value(plantModified.getCode()))
                .andExpect(jsonPath("$.user.personalId").value(plantModified.getPersonalId()))
                .andExpect(jsonPath("$.name").value(plantModified.getName()))
                .andExpect(jsonPath("$.description").isEmpty())
                .andExpect(jsonPath("$.connectionDate").isEmpty())
                .andExpect(jsonPath("$.inverterProvider").value(plantOne.getInverterProvider().name()))
                .andExpect(jsonPath("$.totalPower").value(plantModified.getTotalPower()));
    }

    @Test
    void testWithUnknownPlant() throws Exception {

        String authHeader = loginAsDefaultAdmin();

        final String plantId = UUID.randomUUID().toString();

        UpdatePlantBody plantModified = new UpdatePlantBody();
        plantModified.setCode("TS-234123");
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

        // Create three supplies
        Plant plantOne = PlantMother.random(userOne).withCode("TS-456789").build();
        plantOne = createPlantRepository.create(plantOne, UserId.of(userOne.getId()));

        String authHeader = loginAsDefaultAdmin();

        UpdatePlantBody plantModified = new UpdatePlantBody();
        plantModified.setCode("TS-234123");
        plantModified.setName("Main plant");
        plantModified.setAddress("Fake Street 666");
        plantModified.setTotalPower(25.6D);
        plantModified.setPersonalId("unknown");
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
                          "code": "TS-234123",
                          "name": "Main plant",
                          "address": "Fake Street 666",
                          "totalPower": "25.6"
                        }
                """;

        final String authHeader = loginAsDefaultAdmin();

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

        final String authHeader = loginAsDefaultAdmin();

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
                                  "code": "TS-234123",
                                  "address": "Fake Street 666",
                                  "totalPower": "25.6"
                                }
                        """,
                """
                                {
                                  "code": "TS-234123",
                                  "name": "Main plant",
                                  "totalPower": "25.6"
                                }
                        """,
                """
                                {
                                  "code": "TS-234123",
                                  "name": "Main plant",
                                  "address": "Fake Street 666",
                                }
                        """);
    }

    @ParameterizedTest
    @MethodSource("getBodyWithInvalidFormatValues")
    void testWithInvalidFormatValues(String body) throws Exception {

        final String authHeader = loginAsDefaultAdmin();

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
                              "code": "TS-1234124",
                              "name": "Plant one",
                              "personalId": "12345678Z",
                              "address": "Fake Street 123",
                              "totalPower": "foo",
                              "inverterProvider": "HUAWEI"
                            }
                        """,
                """
                            {
                              "code": "TS-1234124",
                              "name": "Plant one",
                              "personalId": "12345678Z",
                              "address": "Fake Street 123",
                              "totalPower": "60.00",
                              "inverterProvider": "foo"
                            }
                        """,
                """
                            {
                              "code": "TS-1234124",
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
        final String authHeader = loginAsDefaultAdmin();

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
        final String authHeader = loginAsDefaultAdmin();

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
}

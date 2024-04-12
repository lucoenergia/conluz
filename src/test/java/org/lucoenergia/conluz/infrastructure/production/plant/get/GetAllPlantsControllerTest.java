package org.lucoenergia.conluz.infrastructure.production.plant.get;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.user.DefaultUserAdminMother;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.plant.PlantMother;
import org.lucoenergia.conluz.domain.production.plant.create.CreatePlantRepository;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.lucoenergia.conluz.infrastructure.shared.security.auth.InvalidTokenException;
import org.lucoenergia.conluz.infrastructure.shared.security.auth.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class GetAllPlantsControllerTest extends BaseControllerTest {

    private static final String URL = "/api/v1/plants";

    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreatePlantRepository createPlantRepository;

    @Test
    void testWithDefaultPaginationAndSorting() throws Exception {

        // Create two users
        User userOne = UserMother.randomUser();
        createUserRepository.create(userOne);
        User userTwo = UserMother.randomUser();
        createUserRepository.create(userTwo);

        // Create three supplies
        Plant plantOne = PlantMother.random(userOne).withCode("TS-456789").build();
        createPlantRepository.create(plantOne, UserId.of(userOne.getId()));
        Plant plantTwo = PlantMother.random(userOne).withCode("TS-123456").build();
        createPlantRepository.create(plantTwo, UserId.of(userOne.getId()));
        Plant plantThree = PlantMother.random(userTwo).withCode("TS-789456").build();
        createPlantRepository.create(plantThree, UserId.of(userTwo.getId()));

        String authHeader = loginAsDefaultAdmin();

        mockMvc.perform(get(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value("20"))
                .andExpect(jsonPath("$.totalElements").value("3"))
                .andExpect(jsonPath("$.totalPages").value("1"))
                .andExpect(jsonPath("$.number").value("0"))
                .andExpect(jsonPath("$.items.size()").value(3))

                .andExpect(jsonPath("$.items[0].id").isNotEmpty())
                .andExpect(jsonPath("$.items[0].code").value(plantTwo.getCode()))
                .andExpect(jsonPath("$.items[0].name").value(plantTwo.getName()))
                .andExpect(jsonPath("$.items[0].address").value(plantTwo.getAddress()))
                .andExpect(jsonPath("$.items[0].description").value(plantTwo.getDescription()))
                .andExpect(jsonPath("$.items[0].inverterProvider").value(plantTwo.getInverterProvider().name()))
                .andExpect(jsonPath("$.items[0].totalPower").value(plantTwo.getTotalPower()))
                .andExpect(jsonPath("$.items[0].connectionDate").value(plantTwo.getConnectionDate().format(DateTimeFormatter.ISO_DATE)))
                .andExpect(jsonPath("$.items[0].user.personalId").value(plantTwo.getUser().getPersonalId()))

                .andExpect(jsonPath("$.items[1].id").isNotEmpty())
                .andExpect(jsonPath("$.items[1].code").value(plantOne.getCode()))
                .andExpect(jsonPath("$.items[1].name").value(plantOne.getName()))
                .andExpect(jsonPath("$.items[1].address").value(plantOne.getAddress()))
                .andExpect(jsonPath("$.items[1].description").value(plantOne.getDescription()))
                .andExpect(jsonPath("$.items[1].inverterProvider").value(plantOne.getInverterProvider().name()))
                .andExpect(jsonPath("$.items[1].totalPower").value(plantOne.getTotalPower()))
                .andExpect(jsonPath("$.items[1].connectionDate").value(plantOne.getConnectionDate().format(DateTimeFormatter.ISO_DATE)))
                .andExpect(jsonPath("$.items[1].user.personalId").value(plantOne.getUser().getPersonalId()))

                .andExpect(jsonPath("$.items[2].id").isNotEmpty())
                .andExpect(jsonPath("$.items[2].code").value(plantThree.getCode()))
                .andExpect(jsonPath("$.items[2].name").value(plantThree.getName()))
                .andExpect(jsonPath("$.items[2].address").value(plantThree.getAddress()))
                .andExpect(jsonPath("$.items[2].description").value(plantThree.getDescription()))
                .andExpect(jsonPath("$.items[2].inverterProvider").value(plantThree.getInverterProvider().name()))
                .andExpect(jsonPath("$.items[2].totalPower").value(plantThree.getTotalPower()))
                .andExpect(jsonPath("$.items[2].connectionDate").value(plantThree.getConnectionDate().format(DateTimeFormatter.ISO_DATE)))
                .andExpect(jsonPath("$.items[2].user.personalId").value(plantThree.getUser().getPersonalId()));
    }

    @Test
    void testWithNoResults() throws Exception {

        String authHeader = loginAsDefaultAdmin();

        mockMvc.perform(get(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value("20"))
                .andExpect(jsonPath("$.totalElements").value("0"))
                .andExpect(jsonPath("$.totalPages").value("0"))
                .andExpect(jsonPath("$.number").value("0"))
                .andExpect(jsonPath("$.items.size()").value(0));
    }

    @Test
    void testWithCustomPaginationAndDefaultSorting() throws Exception {

        // Create two users
        User userOne = UserMother.randomUser();
        createUserRepository.create(userOne);
        User userTwo = UserMother.randomUser();
        createUserRepository.create(userTwo);

        // Create three supplies
        Plant plantOne = PlantMother.random(userOne).withCode("TS-456789").build();
        createPlantRepository.create(plantOne, UserId.of(userOne.getId()));
        Plant plantTwo = PlantMother.random(userOne).withCode("TS-123456").build();
        createPlantRepository.create(plantTwo, UserId.of(userOne.getId()));
        Plant plantThree = PlantMother.random(userTwo).withCode("TS-789456").build();
        createPlantRepository.create(plantThree, UserId.of(userTwo.getId()));

        String authHeader = loginAsDefaultAdmin();

        mockMvc.perform(get(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .queryParam("page", "1")
                        .queryParam("size", "1"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value("1"))
                .andExpect(jsonPath("$.totalElements").value("3"))
                .andExpect(jsonPath("$.totalPages").value("3"))
                .andExpect(jsonPath("$.number").value("1"))
                .andExpect(jsonPath("$.items.size()").value(1))
                .andExpect(jsonPath("$.items[0].code").value(plantOne.getCode()));
    }

    @Test
    void testWithUnknownParameter() throws Exception {

        // Create two users
        User userOne = UserMother.randomUser();
        createUserRepository.create(userOne);

        // Create three supplies
        Plant plantOne = PlantMother.random(userOne).withCode("TS-456789").build();
        createPlantRepository.create(plantOne, UserId.of(userOne.getId()));

        String authHeader = loginAsDefaultAdmin();

        mockMvc.perform(get(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .queryParam("unkwnon", "foo"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value("20"))
                .andExpect(jsonPath("$.totalElements").value("1"))
                .andExpect(jsonPath("$.totalPages").value("1"))
                .andExpect(jsonPath("$.number").value("0"))
                .andExpect(jsonPath("$.items.size()").value(1))
                .andExpect(jsonPath("$.items[0].code").value(plantOne.getCode()));
    }

    @Test
    void testWithWrongContentType() throws Exception {

        String authHeader = loginAsDefaultAdmin();

        mockMvc.perform(get(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.TEXT_PLAIN))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void testWithCustomSortingByUnknownField() throws Exception {

        // Create two users
        User userOne = UserMother.randomUser();
        createUserRepository.create(userOne);
        User userTwo = UserMother.randomUser();
        createUserRepository.create(userTwo);

        // Create three supplies
        Plant plantOne = PlantMother.random(userOne).withCode("TS-456789").build();
        createPlantRepository.create(plantOne, UserId.of(userOne.getId()));
        Plant plantTwo = PlantMother.random(userOne).withCode("TS-123456").build();
        createPlantRepository.create(plantTwo, UserId.of(userOne.getId()));
        Plant plantThree = PlantMother.random(userTwo).withCode("TS-789456").build();
        createPlantRepository.create(plantThree, UserId.of(userTwo.getId()));

        String authHeader = loginAsDefaultAdmin();

        mockMvc.perform(get(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .queryParam("sort", "unknown,asc"))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void testWithCustomSortingByUnknownDirection() throws Exception {

        // Create two users
        User userOne = UserMother.randomUser();
        createUserRepository.create(userOne);
        User userTwo = UserMother.randomUser();
        createUserRepository.create(userTwo);

        // Create three supplies
        Plant plantOne = PlantMother.random(userOne).withCode("TS-456789").build();
        createPlantRepository.create(plantOne, UserId.of(userOne.getId()));
        Plant plantTwo = PlantMother.random(userOne).withCode("TS-123456").build();
        createPlantRepository.create(plantTwo, UserId.of(userOne.getId()));
        Plant plantThree = PlantMother.random(userTwo).withCode("TS-789456").build();
        createPlantRepository.create(plantThree, UserId.of(userTwo.getId()));

        String authHeader = loginAsDefaultAdmin();

        mockMvc.perform(get(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .queryParam("sort", "fullName,unknown"))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void testWithCustomSorting() throws Exception {

        // Create two users
        User userOne = UserMother.randomUser();
        createUserRepository.create(userOne);
        User userTwo = UserMother.randomUser();
        createUserRepository.create(userTwo);

        // Create three supplies
        Plant plantOne = PlantMother.random(userOne)
                .withCode("TS-456789")
                .withName("Plant One")
                .build();
        createPlantRepository.create(plantOne, UserId.of(userOne.getId()));
        Plant plantTwo = PlantMother.random(userOne)
                .withCode("TS-123456")
                .withName("Plant Two")
                .build();
        createPlantRepository.create(plantTwo, UserId.of(userOne.getId()));
        Plant plantThree = PlantMother.random(userTwo)
                .withCode("TS-789456")
                .withName("Plant Three")
                .build();
        createPlantRepository.create(plantThree, UserId.of(userTwo.getId()));

        String authHeader = loginAsDefaultAdmin();

        mockMvc.perform(get(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .queryParam("sort", "name,asc"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value("20"))
                .andExpect(jsonPath("$.totalElements").value("3"))
                .andExpect(jsonPath("$.totalPages").value("1"))
                .andExpect(jsonPath("$.number").value("0"))
                .andExpect(jsonPath("$.items.size()").value(3))
                .andExpect(jsonPath("$.items[0].code").value(plantOne.getCode()))
                .andExpect(jsonPath("$.items[1].code").value(plantThree.getCode()))
                .andExpect(jsonPath("$.items[2].code").value(plantTwo.getCode()));
    }

    @Test
    void testWithCustomSortingByMultipleFields() throws Exception {

        // Create two users
        User userOne = UserMother.randomUser();
        createUserRepository.create(userOne);
        User userTwo = UserMother.randomUser();
        createUserRepository.create(userTwo);

        // Create three supplies
        Plant plantOne = PlantMother.random(userOne)
                .withCode("TS-456789")
                .withName("Plant One")
                .withTotalPower(60D)
                .build();
        createPlantRepository.create(plantOne, UserId.of(userOne.getId()));
        Plant plantTwo = PlantMother.random(userOne)
                .withCode("TS-123456")
                .withName("Plant Two")
                .withTotalPower(60D)
                .build();
        createPlantRepository.create(plantTwo, UserId.of(userOne.getId()));
        Plant plantThree = PlantMother.random(userTwo)
                .withCode("TS-789456")
                .withName("Plant Three")
                .withTotalPower(30D)
                .build();
        createPlantRepository.create(plantThree, UserId.of(userTwo.getId()));

        String authHeader = loginAsDefaultAdmin();

        mockMvc.perform(get(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .queryParam("sort", "totalPower,asc")
                        .queryParam("sort", "code,asc"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value("20"))
                .andExpect(jsonPath("$.totalElements").value("3"))
                .andExpect(jsonPath("$.totalPages").value("1"))
                .andExpect(jsonPath("$.number").value("0"))
                .andExpect(jsonPath("$.items.size()").value(3))
                .andExpect(jsonPath("$.items[0].code").value(plantThree.getCode()))
                .andExpect(jsonPath("$.items[1].code").value(plantTwo.getCode()))
                .andExpect(jsonPath("$.items[2].code").value(plantOne.getCode()));
    }

    @Test
    void testWithCustomSortingAndCustomPagination() throws Exception {

        // Create two users
        User userOne = UserMother.randomUser();
        createUserRepository.create(userOne);
        User userTwo = UserMother.randomUser();
        createUserRepository.create(userTwo);

        // Create three supplies
        Plant plantOne = PlantMother.random(userOne)
                .withCode("TS-456789")
                .withName("Plant One")
                .withTotalPower(60D)
                .build();
        createPlantRepository.create(plantOne, UserId.of(userOne.getId()));
        Plant plantTwo = PlantMother.random(userOne)
                .withCode("TS-123456")
                .withName("Plant Two")
                .withTotalPower(60D)
                .build();
        createPlantRepository.create(plantTwo, UserId.of(userOne.getId()));
        Plant plantThree = PlantMother.random(userTwo)
                .withCode("TS-789456")
                .withName("Plant Three")
                .withTotalPower(30D)
                .build();
        createPlantRepository.create(plantThree, UserId.of(userTwo.getId()));

        String authHeader = loginAsDefaultAdmin();

        mockMvc.perform(get(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .queryParam("sort", "name,desc")
                        .queryParam("page", "1")
                        .queryParam("size", "1"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value("1"))
                .andExpect(jsonPath("$.totalElements").value("3"))
                .andExpect(jsonPath("$.totalPages").value("3"))
                .andExpect(jsonPath("$.number").value("1"))
                .andExpect(jsonPath("$.items.size()").value(1))
                .andExpect(jsonPath("$.items[0].code").value(plantThree.getCode()));
    }

    @Test
    void testWithMissingToken() throws Exception {

        mockMvc.perform(get(URL)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(HttpStatus.UNAUTHORIZED.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void testWithWrongToken() {

        final String wrongToken = JwtAuthenticationFilter.AUTHORIZATION_HEADER_PREFIX +
                "wrong";

        Assertions.assertThrows(InvalidTokenException.class,
                () -> mockMvc.perform(get(URL)
                        .header(HttpHeaders.AUTHORIZATION, wrongToken)
                        .contentType(MediaType.APPLICATION_JSON)));
    }

    @Test
    void testWithExpiredToken() {

        final String expiredToken = JwtAuthenticationFilter.AUTHORIZATION_HEADER_PREFIX +
                "eyJhbGciOiJIUzI1NiJ9.eyJyb2xlIjoiQURNSU4iLCJzdWIiOiJiMTFlMTgxNS1mNzE0LTRmNGEtOGZjMS0yNjQxM2FmM2YzYmIiLCJpYXQiOjE3MDQyNzkzNzIsImV4cCI6MTcwNDI4MTE3Mn0.jO3pgdDj4mg9TnRzL7f8RUL1ytJS7057jAg6zaCcwn0";

        Assertions.assertThrows(InvalidTokenException.class,
                () -> mockMvc.perform(get(URL)
                        .header(HttpHeaders.AUTHORIZATION, expiredToken)
                        .contentType(MediaType.APPLICATION_JSON)));
    }
}

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
import org.lucoenergia.conluz.infrastructure.shared.security.auth.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;

import static org.lucoenergia.conluz.infrastructure.admin.supply.create.CreateSupplyRepositoryDatabase.DEFAULT_COMMUNITY_ID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class GetAllPlantsControllerTest extends BaseControllerTest {

    private static final String URL = "/api/v1/communities/" + DEFAULT_COMMUNITY_ID + "/plants";

    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateSupplyRepository createSupplyRepository;
    @Autowired
    private CreatePlantRepository createPlantRepository;

    @Test
    void testWithDefaultPaginationAndSorting() throws Exception {

        User userOne = UserMother.randomUser();
        createUserRepository.create(userOne);
        Supply supplyOne = SupplyMother.random().build();
        supplyOne = createSupplyRepository.create(supplyOne, UserId.of(userOne.getId()));
        User userTwo = UserMother.randomUser();
        createUserRepository.create(userTwo);
        Supply supplyTwo = SupplyMother.random().build();
        supplyTwo = createSupplyRepository.create(supplyTwo, UserId.of(userTwo.getId()));

        // Create three supplies
        Plant plantOne = PlantMother.random(supplyOne).withProviderCode("TS-456789").build();
        createPlantRepository.create(plantOne, SupplyId.of(supplyOne.getId()));
        Plant plantTwo = PlantMother.random(supplyOne).withProviderCode("TS-123456").build();
        createPlantRepository.create(plantTwo, SupplyId.of(supplyOne.getId()));
        Plant plantThree = PlantMother.random(supplyTwo).withProviderCode("TS-789456").build();
        createPlantRepository.create(plantThree, SupplyId.of(supplyTwo.getId()));

        String authHeader = loginAsCommunityMember(DEFAULT_COMMUNITY_ID);

        mockMvc.perform(get(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value("20"))
                .andExpect(jsonPath("$.totalElements").value("3"))
                .andExpect(jsonPath("$.totalPages").value("1"))
                .andExpect(jsonPath("$.number").value("0"))
                .andExpect(jsonPath("$.items.size()").value(3))

                .andExpect(jsonPath("$.items[0].id").isNotEmpty())
                .andExpect(jsonPath("$.items[0].providerCode").value(plantTwo.getProviderCode()))
                .andExpect(jsonPath("$.items[0].name").value(plantTwo.getName()))
                .andExpect(jsonPath("$.items[0].address").value(plantTwo.getAddress()))
                .andExpect(jsonPath("$.items[0].description").value(plantTwo.getDescription()))
                .andExpect(jsonPath("$.items[0].inverterProvider").value(plantTwo.getInverterProvider().name()))
                .andExpect(jsonPath("$.items[0].totalPower").value(plantTwo.getTotalPower()))
                .andExpect(jsonPath("$.items[0].connectionDate").value(plantTwo.getConnectionDate().format(DateTimeFormatter.ISO_DATE)))
                .andExpect(jsonPath("$.items[0].supply.code").value(plantTwo.getSupply().getCode()))

                .andExpect(jsonPath("$.items[1].id").isNotEmpty())
                .andExpect(jsonPath("$.items[1].providerCode").value(plantOne.getProviderCode()))
                .andExpect(jsonPath("$.items[1].name").value(plantOne.getName()))
                .andExpect(jsonPath("$.items[1].address").value(plantOne.getAddress()))
                .andExpect(jsonPath("$.items[1].description").value(plantOne.getDescription()))
                .andExpect(jsonPath("$.items[1].inverterProvider").value(plantOne.getInverterProvider().name()))
                .andExpect(jsonPath("$.items[1].totalPower").value(plantOne.getTotalPower()))
                .andExpect(jsonPath("$.items[1].connectionDate").value(plantOne.getConnectionDate().format(DateTimeFormatter.ISO_DATE)))
                .andExpect(jsonPath("$.items[1].supply.code").value(plantOne.getSupply().getCode()))

                .andExpect(jsonPath("$.items[2].id").isNotEmpty())
                .andExpect(jsonPath("$.items[2].providerCode").value(plantThree.getProviderCode()))
                .andExpect(jsonPath("$.items[2].name").value(plantThree.getName()))
                .andExpect(jsonPath("$.items[2].address").value(plantThree.getAddress()))
                .andExpect(jsonPath("$.items[2].description").value(plantThree.getDescription()))
                .andExpect(jsonPath("$.items[2].inverterProvider").value(plantThree.getInverterProvider().name()))
                .andExpect(jsonPath("$.items[2].totalPower").value(plantThree.getTotalPower()))
                .andExpect(jsonPath("$.items[2].connectionDate").value(plantThree.getConnectionDate().format(DateTimeFormatter.ISO_DATE)))
                .andExpect(jsonPath("$.items[2].supply.code").value(plantThree.getSupply().getCode()));
    }

    @Test
    void testWithNoResults() throws Exception {

        String authHeader = loginAsCommunityMember(DEFAULT_COMMUNITY_ID);

        mockMvc.perform(get(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
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

        User userOne = UserMother.randomUser();
        createUserRepository.create(userOne);
        Supply supplyOne = SupplyMother.random().build();
        supplyOne = createSupplyRepository.create(supplyOne, UserId.of(userOne.getId()));
        User userTwo = UserMother.randomUser();
        createUserRepository.create(userTwo);
        Supply supplyTwo = SupplyMother.random().build();
        supplyTwo = createSupplyRepository.create(supplyTwo, UserId.of(userTwo.getId()));

        Plant plantOne = PlantMother.random(supplyOne).withProviderCode("TS-456789").build();
        createPlantRepository.create(plantOne, SupplyId.of(supplyOne.getId()));
        Plant plantTwo = PlantMother.random(supplyOne).withProviderCode("TS-123456").build();
        createPlantRepository.create(plantTwo, SupplyId.of(supplyOne.getId()));
        Plant plantThree = PlantMother.random(supplyTwo).withProviderCode("TS-789456").build();
        createPlantRepository.create(plantThree, SupplyId.of(supplyTwo.getId()));

        String authHeader = loginAsCommunityMember(DEFAULT_COMMUNITY_ID);

        mockMvc.perform(get(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .queryParam("page", "1")
                        .queryParam("size", "1"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value("1"))
                .andExpect(jsonPath("$.totalElements").value("3"))
                .andExpect(jsonPath("$.totalPages").value("3"))
                .andExpect(jsonPath("$.number").value("1"))
                .andExpect(jsonPath("$.items.size()").value(1))
                .andExpect(jsonPath("$.items[0].providerCode").value(plantOne.getProviderCode()));
    }

    @Test
    void testWithUnknownParameter() throws Exception {

        User userOne = UserMother.randomUser();
        createUserRepository.create(userOne);
        Supply supplyOne = SupplyMother.random().build();
        supplyOne = createSupplyRepository.create(supplyOne, UserId.of(userOne.getId()));

        Plant plantOne = PlantMother.random(supplyOne).withProviderCode("TS-456789").build();
        createPlantRepository.create(plantOne, SupplyId.of(supplyOne.getId()));

        String authHeader = loginAsCommunityMember(DEFAULT_COMMUNITY_ID);

        mockMvc.perform(get(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .queryParam("unkwnon", "foo"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value("20"))
                .andExpect(jsonPath("$.totalElements").value("1"))
                .andExpect(jsonPath("$.totalPages").value("1"))
                .andExpect(jsonPath("$.number").value("0"))
                .andExpect(jsonPath("$.items.size()").value(1))
                .andExpect(jsonPath("$.items[0].providerCode").value(plantOne.getProviderCode()));
    }

    @Test
    void testWithWrongContentType() throws Exception {

        String authHeader = loginAsCommunityMember(DEFAULT_COMMUNITY_ID);

        mockMvc.perform(get(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.TEXT_PLAIN))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value("20"))
                .andExpect(jsonPath("$.totalElements").value("0"))
                .andExpect(jsonPath("$.totalPages").value("0"))
                .andExpect(jsonPath("$.number").value("0"));
    }

    @Test
    void testWithCustomSortingByUnknownField() throws Exception {

        User userOne = UserMother.randomUser();
        createUserRepository.create(userOne);
        Supply supplyOne = SupplyMother.random().build();
        supplyOne = createSupplyRepository.create(supplyOne, UserId.of(userOne.getId()));
        User userTwo = UserMother.randomUser();
        createUserRepository.create(userTwo);
        Supply supplyTwo = SupplyMother.random().build();
        supplyTwo = createSupplyRepository.create(supplyTwo, UserId.of(userTwo.getId()));

        Plant plantOne = PlantMother.random(supplyOne).withProviderCode("TS-456789").build();
        createPlantRepository.create(plantOne, SupplyId.of(supplyOne.getId()));
        Plant plantTwo = PlantMother.random(supplyOne).withProviderCode("TS-123456").build();
        createPlantRepository.create(plantTwo, SupplyId.of(supplyOne.getId()));
        Plant plantThree = PlantMother.random(supplyTwo).withProviderCode("TS-789456").build();
        createPlantRepository.create(plantThree, SupplyId.of(supplyTwo.getId()));

        String authHeader = loginAsCommunityMember(DEFAULT_COMMUNITY_ID);

        mockMvc.perform(get(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
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

        User userOne = UserMother.randomUser();
        createUserRepository.create(userOne);
        Supply supplyOne = SupplyMother.random().build();
        supplyOne = createSupplyRepository.create(supplyOne, UserId.of(userOne.getId()));
        User userTwo = UserMother.randomUser();
        createUserRepository.create(userTwo);
        Supply supplyTwo = SupplyMother.random().build();
        supplyTwo = createSupplyRepository.create(supplyTwo, UserId.of(userTwo.getId()));

        Plant plantOne = PlantMother.random(supplyOne).withProviderCode("TS-456789").build();
        createPlantRepository.create(plantOne, SupplyId.of(supplyOne.getId()));
        Plant plantTwo = PlantMother.random(supplyOne).withProviderCode("TS-123456").build();
        createPlantRepository.create(plantTwo, SupplyId.of(supplyOne.getId()));
        Plant plantThree = PlantMother.random(supplyTwo).withProviderCode("TS-789456").build();
        createPlantRepository.create(plantThree, SupplyId.of(supplyTwo.getId()));

        String authHeader = loginAsCommunityMember(DEFAULT_COMMUNITY_ID);

        mockMvc.perform(get(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
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

        User userOne = UserMother.randomUser();
        createUserRepository.create(userOne);
        Supply supplyOne = SupplyMother.random().build();
        supplyOne = createSupplyRepository.create(supplyOne, UserId.of(userOne.getId()));
        User userTwo = UserMother.randomUser();
        createUserRepository.create(userTwo);
        Supply supplyTwo = SupplyMother.random().build();
        supplyTwo = createSupplyRepository.create(supplyTwo, UserId.of(userTwo.getId()));

        Plant plantOne = PlantMother.random(supplyOne)
                .withProviderCode("TS-456789")
                .withName("Plant One")
                .build();
        createPlantRepository.create(plantOne, SupplyId.of(supplyOne.getId()));
        Plant plantTwo = PlantMother.random(supplyOne)
                .withProviderCode("TS-123456")
                .withName("Plant Two")
                .build();
        createPlantRepository.create(plantTwo, SupplyId.of(supplyOne.getId()));
        Plant plantThree = PlantMother.random(supplyTwo)
                .withProviderCode("TS-789456")
                .withName("Plant Three")
                .build();
        createPlantRepository.create(plantThree, SupplyId.of(supplyTwo.getId()));

        String authHeader = loginAsCommunityMember(DEFAULT_COMMUNITY_ID);

        mockMvc.perform(get(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .queryParam("sort", "name,asc"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value("20"))
                .andExpect(jsonPath("$.totalElements").value("3"))
                .andExpect(jsonPath("$.totalPages").value("1"))
                .andExpect(jsonPath("$.number").value("0"))
                .andExpect(jsonPath("$.items.size()").value(3))
                .andExpect(jsonPath("$.items[0].providerCode").value(plantOne.getProviderCode()))
                .andExpect(jsonPath("$.items[1].providerCode").value(plantThree.getProviderCode()))
                .andExpect(jsonPath("$.items[2].providerCode").value(plantTwo.getProviderCode()));
    }

    @Test
    void testWithCustomSortingByMultipleFields() throws Exception {

        User userOne = UserMother.randomUser();
        createUserRepository.create(userOne);
        Supply supplyOne = SupplyMother.random().build();
        supplyOne = createSupplyRepository.create(supplyOne, UserId.of(userOne.getId()));
        User userTwo = UserMother.randomUser();
        createUserRepository.create(userTwo);
        Supply supplyTwo = SupplyMother.random().build();
        supplyTwo = createSupplyRepository.create(supplyTwo, UserId.of(userTwo.getId()));

        Plant plantOne = PlantMother.random(supplyOne)
                .withProviderCode("TS-456789")
                .withName("Plant One")
                .withTotalPower(60D)
                .build();
        createPlantRepository.create(plantOne, SupplyId.of(supplyOne.getId()));
        Plant plantTwo = PlantMother.random(supplyOne)
                .withProviderCode("TS-123456")
                .withName("Plant Two")
                .withTotalPower(60D)
                .build();
        createPlantRepository.create(plantTwo, SupplyId.of(supplyOne.getId()));
        Plant plantThree = PlantMother.random(supplyTwo)
                .withProviderCode("TS-789456")
                .withName("Plant Three")
                .withTotalPower(30D)
                .build();
        createPlantRepository.create(plantThree, SupplyId.of(supplyTwo.getId()));

        String authHeader = loginAsCommunityMember(DEFAULT_COMMUNITY_ID);

        mockMvc.perform(get(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .queryParam("sort", "totalPower,asc")
                        .queryParam("sort", "providerCode,asc"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value("20"))
                .andExpect(jsonPath("$.totalElements").value("3"))
                .andExpect(jsonPath("$.totalPages").value("1"))
                .andExpect(jsonPath("$.number").value("0"))
                .andExpect(jsonPath("$.items.size()").value(3))
                .andExpect(jsonPath("$.items[0].providerCode").value(plantThree.getProviderCode()))
                .andExpect(jsonPath("$.items[1].providerCode").value(plantTwo.getProviderCode()))
                .andExpect(jsonPath("$.items[2].providerCode").value(plantOne.getProviderCode()));
    }

    @Test
    void testWithCustomSortingAndCustomPagination() throws Exception {

        User userOne = UserMother.randomUser();
        createUserRepository.create(userOne);
        Supply supplyOne = SupplyMother.random().build();
        supplyOne = createSupplyRepository.create(supplyOne, UserId.of(userOne.getId()));
        User userTwo = UserMother.randomUser();
        createUserRepository.create(userTwo);
        Supply supplyTwo = SupplyMother.random().build();
        supplyTwo = createSupplyRepository.create(supplyTwo, UserId.of(userTwo.getId()));

        Plant plantOne = PlantMother.random(supplyOne)
                .withProviderCode("TS-456789")
                .withName("Plant One")
                .withTotalPower(60D)
                .build();
        createPlantRepository.create(plantOne, SupplyId.of(supplyOne.getId()));
        Plant plantTwo = PlantMother.random(supplyOne)
                .withProviderCode("TS-123456")
                .withName("Plant Two")
                .withTotalPower(60D)
                .build();
        createPlantRepository.create(plantTwo, SupplyId.of(supplyOne.getId()));
        Plant plantThree = PlantMother.random(supplyTwo)
                .withProviderCode("TS-789456")
                .withName("Plant Three")
                .withTotalPower(30D)
                .build();
        createPlantRepository.create(plantThree, SupplyId.of(supplyTwo.getId()));

        String authHeader = loginAsCommunityMember(DEFAULT_COMMUNITY_ID);

        mockMvc.perform(get(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
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
                .andExpect(jsonPath("$.items[0].providerCode").value(plantThree.getProviderCode()));
    }

    @Test
    void testRegularMemberOfTheCommunityCanListItsPlants() throws Exception {

        User user = UserMother.randomUser();
        createUserRepository.create(user);
        Supply supply = SupplyMother.random().build();
        supply = createSupplyRepository.create(supply, UserId.of(user.getId()));
        Plant plant = PlantMother.random(supply).withProviderCode("TS-456789").build();
        createPlantRepository.create(plant, SupplyId.of(supply.getId()));

        // A regular (non-admin) member of the plant's community can list its plants
        String authHeader = loginAsCommunityMember(DEFAULT_COMMUNITY_ID);

        mockMvc.perform(get(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value("1"))
                .andExpect(jsonPath("$.items.size()").value(1))
                .andExpect(jsonPath("$.items[0].providerCode").value(plant.getProviderCode()));
    }

    @Test
    void testNonMemberOfTheCommunityCannotListItsPlants() throws Exception {

        // A user with no membership in the community is forbidden
        String authHeader = loginAsPartner();

        mockMvc.perform(get(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(HttpStatus.NOT_FOUND.value()));
    }

    @Test
    void testWithMissingToken() throws Exception {

        mockMvc.perform(get(URL))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(HttpStatus.UNAUTHORIZED.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void testWithWrongToken() throws Exception {

        final String wrongToken = JwtAuthenticationFilter.AUTHORIZATION_HEADER_PREFIX +
                "wrong";

        mockMvc.perform(get(URL)
                        .header(HttpHeaders.AUTHORIZATION, wrongToken))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(HttpStatus.UNAUTHORIZED.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void testWithExpiredToken() throws Exception {

        final String expiredToken = JwtAuthenticationFilter.AUTHORIZATION_HEADER_PREFIX +
                "eyJhbGciOiJIUzI1NiJ9.eyJyb2xlIjoiQURNSU4iLCJzdWIiOiJiMTFlMTgxNS1mNzE0LTRmNGEtOGZjMS0yNjQxM2FmM2YzYmIiLCJpYXQiOjE3MDQyNzkzNzIsImV4cCI6MTcwNDI4MTE3Mn0.jO3pgdDj4mg9TnRzL7f8RUL1ytJS7057jAg6zaCcwn0";

        mockMvc.perform(get(URL)
                        .header(HttpHeaders.AUTHORIZATION, expiredToken))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(HttpStatus.UNAUTHORIZED.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }
}

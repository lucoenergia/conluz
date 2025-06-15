package org.lucoenergia.conluz.infrastructure.admin.supply.get;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.user.DefaultUserAdminMother;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.lucoenergia.conluz.infrastructure.shared.security.auth.InvalidTokenException;
import org.lucoenergia.conluz.infrastructure.shared.security.auth.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class GetAllSuppliesControllerTest extends BaseControllerTest {

    private static final String URL = "/api/v1/supplies";

    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateSupplyRepository createSupplyRepository;

    @Test
    void testWithDefaultPagination() throws Exception {

        // Create two users
        User userOne = UserMother.randomUser();
        createUserRepository.create(userOne);
        User userTwo = UserMother.randomUser();
        createUserRepository.create(userTwo);

        // Create three supplies
        Supply supplyOne = SupplyMother.random(userOne).build();
        createSupplyRepository.create(supplyOne, UserId.of(userOne.getId()));
        Supply supplyTwo = SupplyMother.random(userOne).build();
        createSupplyRepository.create(supplyTwo, UserId.of(userOne.getId()));
        Supply supplyThree = SupplyMother.random(userTwo).build();
        createSupplyRepository.create(supplyThree, UserId.of(userTwo.getId()));

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
                .andExpect(jsonPath("$.items.size()").value(3));
    }

    @Test
    void testWithCustomPagination() throws Exception {

        // Create two users
        User userOne = UserMother.randomUser();
        createUserRepository.create(userOne);
        User userTwo = UserMother.randomUser();
        createUserRepository.create(userTwo);

        // Create three supplies
        Supply supplyOne = SupplyMother.random(userOne).build();
        createSupplyRepository.create(supplyOne, UserId.of(userOne.getId()));
        Supply supplyTwo = SupplyMother.random(userOne).build();
        createSupplyRepository.create(supplyTwo, UserId.of(userOne.getId()));
        Supply supplyThree = SupplyMother.random(userTwo).build();
        createSupplyRepository.create(supplyThree, UserId.of(userTwo.getId()));

        String authHeader = loginAsDefaultAdmin();

        mockMvc.perform(get(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .param("page", "1")
                        .param("size", "1"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value("1"))
                .andExpect(jsonPath("$.totalElements").value("3"))
                .andExpect(jsonPath("$.totalPages").value("3"))
                .andExpect(jsonPath("$.number").value("1"))
                .andExpect(jsonPath("$.items.size()").value(1));
    }

    @Test
    void testWithUnknownParameter() throws Exception {

        // Create two users
        User userOne = UserMother.randomUser();
        createUserRepository.create(userOne);
        User userTwo = UserMother.randomUser();
        createUserRepository.create(userTwo);

        // Create three supplies
        Supply supplyOne = SupplyMother.random(userOne).build();
        supplyOne = createSupplyRepository.create(supplyOne, UserId.of(userOne.getId()));
        Supply supplyTwo = SupplyMother.random(userOne).build();
        supplyTwo = createSupplyRepository.create(supplyTwo, UserId.of(userOne.getId()));
        Supply supplyThree = SupplyMother.random(userTwo).build();
        supplyThree = createSupplyRepository.create(supplyThree, UserId.of(userTwo.getId()));

        String authHeader = loginAsDefaultAdmin();

        mockMvc.perform(get(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .queryParam("unkwnon", "foo"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value("20"))
                .andExpect(jsonPath("$.totalElements").value("3"))
                .andExpect(jsonPath("$.totalPages").value("1"))
                .andExpect(jsonPath("$.number").value("0"))
                .andExpect(jsonPath("$.items.size()").value(3))
                .andExpect(jsonPath("$.items[0].id").value(supplyOne.getId().toString()));
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
        Supply supplyOne = SupplyMother.random(userOne).build();
        createSupplyRepository.create(supplyOne, UserId.of(userOne.getId()));
        Supply supplyTwo = SupplyMother.random(userOne).build();
        createSupplyRepository.create(supplyTwo, UserId.of(userOne.getId()));
        Supply supplyThree = SupplyMother.random(userTwo).build();
        createSupplyRepository.create(supplyThree, UserId.of(userTwo.getId()));

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
        Supply supplyOne = SupplyMother.random(userOne).build();
        createSupplyRepository.create(supplyOne, UserId.of(userOne.getId()));
        Supply supplyTwo = SupplyMother.random(userOne).build();
        createSupplyRepository.create(supplyTwo, UserId.of(userOne.getId()));
        Supply supplyThree = SupplyMother.random(userTwo).build();
        createSupplyRepository.create(supplyThree, UserId.of(userTwo.getId()));

        String authHeader = loginAsDefaultAdmin();

        mockMvc.perform(get(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .queryParam("sort", "name,unknown"))
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
        Supply supplyOne = SupplyMother.random(userOne).withName("First").build();
        supplyOne = createSupplyRepository.create(supplyOne, UserId.of(userOne.getId()));
        Supply supplyTwo = SupplyMother.random(userOne).withName("Second").build();
        supplyTwo = createSupplyRepository.create(supplyTwo, UserId.of(userOne.getId()));
        Supply supplyThree = SupplyMother.random(userTwo).withName("Third").build();
        supplyThree = createSupplyRepository.create(supplyThree, UserId.of(userTwo.getId()));

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
                .andExpect(jsonPath("$.items[0].id").value(supplyOne.getId().toString()))
                .andExpect(jsonPath("$.items[1].id").value(supplyTwo.getId().toString()))
                .andExpect(jsonPath("$.items[2].id").value(supplyThree.getId().toString()));
    }

    @Test
    void testWithCustomSortingByMultipleFields() throws Exception {

        // Create two users
        User userOne = UserMother.randomUser();
        createUserRepository.create(userOne);
        User userTwo = UserMother.randomUser();
        createUserRepository.create(userTwo);

        // Create three supplies
        Supply supplyOne = SupplyMother.random(userOne).withName("First").withCode("B").build();
        supplyOne = createSupplyRepository.create(supplyOne, UserId.of(userOne.getId()));
        Supply supplyTwo = SupplyMother.random(userOne).withName("First").withCode("A").build();
        supplyTwo = createSupplyRepository.create(supplyTwo, UserId.of(userOne.getId()));
        Supply supplyThree = SupplyMother.random(userTwo).withName("Third").withCode("C").build();
        supplyThree = createSupplyRepository.create(supplyThree, UserId.of(userTwo.getId()));

        String authHeader = loginAsDefaultAdmin();

        mockMvc.perform(get(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .queryParam("sort", "name,asc")
                        .queryParam("sort", "code,asc"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value("20"))
                .andExpect(jsonPath("$.totalElements").value("3"))
                .andExpect(jsonPath("$.totalPages").value("1"))
                .andExpect(jsonPath("$.number").value("0"))
                .andExpect(jsonPath("$.items.size()").value(3))
                .andExpect(jsonPath("$.items[0].id").value(supplyTwo.getId().toString()))
                .andExpect(jsonPath("$.items[1].id").value(supplyOne.getId().toString()))
                .andExpect(jsonPath("$.items[2].id").value(supplyThree.getId().toString()));
    }

    @Test
    void testWithCustomSortingAndCustomPagination() throws Exception {

        // Create two users
        User userOne = UserMother.randomUser();
        createUserRepository.create(userOne);
        User userTwo = UserMother.randomUser();
        createUserRepository.create(userTwo);

        // Create three supplies
        Supply supplyOne = SupplyMother.random(userOne).withName("First").build();
        supplyOne = createSupplyRepository.create(supplyOne, UserId.of(userOne.getId()));
        Supply supplyTwo = SupplyMother.random(userOne).withName("Second").build();
        supplyTwo = createSupplyRepository.create(supplyTwo, UserId.of(userOne.getId()));
        Supply supplyThree = SupplyMother.random(userTwo).withName("Third").build();
        supplyThree = createSupplyRepository.create(supplyThree, UserId.of(userTwo.getId()));

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
                .andExpect(jsonPath("$.items[0].id").value(supplyTwo.getId().toString()));
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
    void testWithWrongToken() throws Exception {

        final String wrongToken = JwtAuthenticationFilter.AUTHORIZATION_HEADER_PREFIX +
                "wrong";

        mockMvc.perform(get(URL)
                        .header(HttpHeaders.AUTHORIZATION, wrongToken)
                        .contentType(MediaType.APPLICATION_JSON))
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
                        .header(HttpHeaders.AUTHORIZATION, expiredToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(HttpStatus.UNAUTHORIZED.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }
}

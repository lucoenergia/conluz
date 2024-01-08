package org.lucoenergia.conluz.infrastructure.admin.user.get;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.user.DefaultUserAdminMother;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
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
class GetAllUsersControllerTest extends BaseControllerTest {

    @Autowired
    private CreateUserRepository createUserRepository;

    @Test
    void testGetAllUsersWithDefaultPagination() throws Exception {

        // Create a user
        User userOne = UserMother.randomUser();
        createUserRepository.create(userOne, UserMother.randomPassword());

        String authHeader = loginAsDefaultAdmin();

        mockMvc.perform(get("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value("20"))
                .andExpect(jsonPath("$.totalElements").value("2"))
                .andExpect(jsonPath("$.totalPages").value("1"))
                .andExpect(jsonPath("$.number").value("0"))
                .andExpect(jsonPath("$.items.size()").value(2))
                .andExpect(jsonPath("$.items[1].id").value(userOne.getId().toString()))
                .andExpect(jsonPath("$.items[1].personalId").value(userOne.getPersonalId()))
                .andExpect(jsonPath("$.items[1].number").value(userOne.getNumber()))
                .andExpect(jsonPath("$.items[1].fullName").value(userOne.getFullName()))
                .andExpect(jsonPath("$.items[1].address").value(userOne.getAddress()))
                .andExpect(jsonPath("$.items[1].email").value(userOne.getEmail()))
                .andExpect(jsonPath("$.items[1].phoneNumber").value(userOne.getPhoneNumber()))
                .andExpect(jsonPath("$.items[1].role").value(userOne.getRole().name()))
                .andExpect(jsonPath("$.items[1].enabled").value(userOne.isEnabled()))
                .andExpect(jsonPath("$.items[1].password").doesNotExist());
    }

    @Test
    void testGetAllUsersWithCustomPagination() throws Exception {

        // Create a user
        User userOne = UserMother.randomUser();
        createUserRepository.create(userOne, UserMother.randomPassword());
        User userTwo = UserMother.randomUser();
        createUserRepository.create(userTwo, UserMother.randomPassword());
        User userThree = UserMother.randomUser();
        createUserRepository.create(userThree, UserMother.randomPassword());

        String authHeader = loginAsDefaultAdmin();

        mockMvc.perform(get("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .queryParam("page", "1")
                        .queryParam("size", "1"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value("1"))
                .andExpect(jsonPath("$.totalElements").value("4"))
                .andExpect(jsonPath("$.totalPages").value("4"))
                .andExpect(jsonPath("$.number").value("1"))
                .andExpect(jsonPath("$.items.size()").value(1));
    }

    @Test
    void testGetAllUsersWithUnknownParameter() throws Exception {

        String authHeader = loginAsDefaultAdmin();

        mockMvc.perform(get("/api/v1/users")
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
                .andExpect(jsonPath("$.items[0].personalId").value(DefaultUserAdminMother.PERSONAL_ID));
    }

    @Test
    void testGetAllUsersWithWrongContentType() throws Exception {

        String authHeader = loginAsDefaultAdmin();

        mockMvc.perform(get("/api/v1/users")
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
    void testGetAllUsersWithCustomSortingByUnknownField() throws Exception {

        String authHeader = loginAsDefaultAdmin();

        mockMvc.perform(get("/api/v1/users")
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
    void testGetAllUsersWithCustomSortingByUnknownDirection() throws Exception {

        String authHeader = loginAsDefaultAdmin();

        mockMvc.perform(get("/api/v1/users")
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
    void testGetAllUsersWithCustomSorting() throws Exception {

        // Create a user
        User userOne = UserMother.randomUser();
        userOne.setFullName("Bod Dylan");
        createUserRepository.create(userOne, UserMother.randomPassword());
        User userTwo = UserMother.randomUser();
        userTwo.setFullName("Bruce Dickinson");
        createUserRepository.create(userTwo, UserMother.randomPassword());
        User userThree = UserMother.randomUser();
        userThree.setFullName("Rob Halford");
        createUserRepository.create(userThree, UserMother.randomPassword());

        String authHeader = loginAsDefaultAdmin();

        mockMvc.perform(get("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .queryParam("sort", "fullName,asc"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value("20"))
                .andExpect(jsonPath("$.totalElements").value("4"))
                .andExpect(jsonPath("$.totalPages").value("1"))
                .andExpect(jsonPath("$.number").value("0"))
                .andExpect(jsonPath("$.items.size()").value(4))
                .andExpect(jsonPath("$.items[0].id").value(userOne.getId().toString()))
                .andExpect(jsonPath("$.items[1].id").value(userTwo.getId().toString()))
                .andExpect(jsonPath("$.items[2].personalId").value(DefaultUserAdminMother.PERSONAL_ID))
                .andExpect(jsonPath("$.items[3].id").value(userThree.getId().toString()));
    }

    @Test
    void testGetAllUsersWithCustomSortingByMultipleFields() throws Exception {

        // Create a user
        User userOne = UserMother.randomUser();
        userOne.setFullName("Bod Dylan");
        userOne.setPersonalId("aaa");
        createUserRepository.create(userOne, UserMother.randomPassword());
        User userOneB = UserMother.randomUser();
        userOneB.setFullName("Bod Dylan");
        userOne.setPersonalId("bbb");
        createUserRepository.create(userOneB, UserMother.randomPassword());
        User userTwo = UserMother.randomUser();
        userTwo.setFullName("Bruce Dickinson");
        createUserRepository.create(userTwo, UserMother.randomPassword());
        User userThree = UserMother.randomUser();
        userThree.setFullName("Rob Halford");
        createUserRepository.create(userThree, UserMother.randomPassword());

        String authHeader = loginAsDefaultAdmin();

        mockMvc.perform(get("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .queryParam("sort", "fullName,asc")
                        .queryParam("sort", "personalId,asc"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value("20"))
                .andExpect(jsonPath("$.totalElements").value("5"))
                .andExpect(jsonPath("$.totalPages").value("1"))
                .andExpect(jsonPath("$.number").value("0"))
                .andExpect(jsonPath("$.items.size()").value(5))
                .andExpect(jsonPath("$.items[0].id").value(userOne.getId().toString()))
                .andExpect(jsonPath("$.items[1].id").value(userOneB.getId().toString()))
                .andExpect(jsonPath("$.items[2].id").value(userTwo.getId().toString()))
                .andExpect(jsonPath("$.items[3].personalId").value(DefaultUserAdminMother.PERSONAL_ID))
                .andExpect(jsonPath("$.items[4].id").value(userThree.getId().toString()));
    }

    @Test
    void testGetAllUsersWithCustomSortingAndCustomPagination() throws Exception {

        // Create a user
        User userOne = UserMother.randomUser();
        userOne.setFullName("Bod Dylan");
        createUserRepository.create(userOne, UserMother.randomPassword());
        User userTwo = UserMother.randomUser();
        userTwo.setFullName("Bruce Dickinson");
        createUserRepository.create(userTwo, UserMother.randomPassword());
        User userThree = UserMother.randomUser();
        userThree.setFullName("Rob Halford");
        createUserRepository.create(userThree, UserMother.randomPassword());

        String authHeader = loginAsDefaultAdmin();

        mockMvc.perform(get("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .queryParam("sort", "fullName,desc")
                        .queryParam("page", "1")
                        .queryParam("size", "1"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value("1"))
                .andExpect(jsonPath("$.totalElements").value("4"))
                .andExpect(jsonPath("$.totalPages").value("4"))
                .andExpect(jsonPath("$.number").value("1"))
                .andExpect(jsonPath("$.items.size()").value(1))
                .andExpect(jsonPath("$.items[0].personalId").value(DefaultUserAdminMother.PERSONAL_ID));
    }

    @Test
    void testGetAllUsersWithMissingToken() throws Exception {

        mockMvc.perform(get("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(HttpStatus.UNAUTHORIZED.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void testGetAllUsersWithWrongToken() {

        final String wrongToken = JwtAuthenticationFilter.AUTHORIZATION_HEADER_PREFIX +
                "wrong";

        Assertions.assertThrows(MalformedJwtException.class,
                () -> mockMvc.perform(get("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, wrongToken)
                        .contentType(MediaType.APPLICATION_JSON)));
    }

    @Test
    void testGetAllUsersWithExpiredToken() {

        final String expiredToken = JwtAuthenticationFilter.AUTHORIZATION_HEADER_PREFIX +
                "eyJhbGciOiJIUzI1NiJ9.eyJyb2xlIjoiQURNSU4iLCJzdWIiOiJiMTFlMTgxNS1mNzE0LTRmNGEtOGZjMS0yNjQxM2FmM2YzYmIiLCJpYXQiOjE3MDQyNzkzNzIsImV4cCI6MTcwNDI4MTE3Mn0.jO3pgdDj4mg9TnRzL7f8RUL1ytJS7057jAg6zaCcwn0";

        Assertions.assertThrows(ExpiredJwtException.class,
                () -> mockMvc.perform(get("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, expiredToken)
                        .contentType(MediaType.APPLICATION_JSON)));
    }
}

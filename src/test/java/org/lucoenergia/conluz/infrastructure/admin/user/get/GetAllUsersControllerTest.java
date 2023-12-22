package org.lucoenergia.conluz.infrastructure.admin.user.get;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
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
                .andExpect(jsonPath("$.size").value("10"))
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
                        .param("page", "1")
                        .param("size", "1"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value("1"))
                .andExpect(jsonPath("$.totalElements").value("4"))
                .andExpect(jsonPath("$.totalPages").value("4"))
                .andExpect(jsonPath("$.number").value("1"))
                .andExpect(jsonPath("$.items.size()").value(1));
    }
}

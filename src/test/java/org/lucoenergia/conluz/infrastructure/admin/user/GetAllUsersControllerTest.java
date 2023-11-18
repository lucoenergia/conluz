package org.lucoenergia.conluz.infrastructure.admin.user;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.user.CreateUserRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.lucoenergia.conluz.infrastructure.shared.security.BasicAuthHeaderGenerator;
import org.lucoenergia.conluz.infrastructure.shared.security.MockUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class GetAllUsersControllerTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private CreateUserRepository createUserRepository;

    @Test
    @WithMockUser(username = MockUser.USERNAME, authorities = {MockUser.ROLE})
    void testGetAllUsersWithDefaultPagination() throws Exception {

        // Create a user
        User userOne = UserMother.randomUser();
        createUserRepository.create(userOne, UserMother.randomPassword());

        String authHeader = BasicAuthHeaderGenerator.generate();

        mockMvc.perform(get("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value("10"))
                .andExpect(jsonPath("$.totalElements").value("1"))
                .andExpect(jsonPath("$.totalPages").value("1"))
                .andExpect(jsonPath("$.number").value("0"))
                .andExpect(jsonPath("$.items.size()").value(1))
                .andExpect(jsonPath("$.items[:1].id").value(userOne.getId()))
                .andExpect(jsonPath("$.items[:1].number").value(userOne.getNumber()))
                .andExpect(jsonPath("$.items[:1].firstName").value(userOne.getFirstName()))
                .andExpect(jsonPath("$.items[:1].lastName").value(userOne.getLastName()))
                .andExpect(jsonPath("$.items[:1].address").value(userOne.getAddress()))
                .andExpect(jsonPath("$.items[:1].email").value(userOne.getEmail()))
                .andExpect(jsonPath("$.items[:1].phoneNumber").value(userOne.getPhoneNumber()))
                .andExpect(jsonPath("$.items[:1].enabled").value(userOne.getEnabled()));
    }

    @Test
    @WithMockUser(username = MockUser.USERNAME, authorities = {MockUser.ROLE})
    void testGetAllUsersWithCustomPagination() throws Exception {

        // Create a user
        User userOne = UserMother.randomUser();
        createUserRepository.create(userOne, UserMother.randomPassword());
        User userTwo = UserMother.randomUser();
        createUserRepository.create(userTwo, UserMother.randomPassword());
        User userThree = UserMother.randomUser();
        createUserRepository.create(userThree, UserMother.randomPassword());

        String authHeader = BasicAuthHeaderGenerator.generate();

        mockMvc.perform(get("/api/v1/users")
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
}

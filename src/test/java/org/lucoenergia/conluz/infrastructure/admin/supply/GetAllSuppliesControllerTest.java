package org.lucoenergia.conluz.infrastructure.admin.supply;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.CreateSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.user.CreateUserRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.admin.user.UserMother;
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
public class GetAllSuppliesControllerTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateSupplyRepository createSupplyRepository;

    @Test
    @WithMockUser(username = MockUser.USERNAME, authorities = {MockUser.ROLE})
    void testGetAllSuppliesWithDefaultPagination() throws Exception {

        // Create two users
        User userOne = UserMother.randomUser();
        createUserRepository.create(userOne, UserMother.randomPassword());
        User userTwo = UserMother.randomUser();
        createUserRepository.create(userTwo, UserMother.randomPassword());

        // Create three supplies
        Supply supplyOne = SupplyMother.random(userOne);
        createSupplyRepository.create(supplyOne, new UserId(userOne.getId()));
        Supply supplyTwo = SupplyMother.random(userOne);
        createSupplyRepository.create(supplyTwo, new UserId(userOne.getId()));
        Supply supplyThree = SupplyMother.random(userTwo);
        createSupplyRepository.create(supplyThree, new UserId(userTwo.getId()));

        String authHeader = BasicAuthHeaderGenerator.generate();

        mockMvc.perform(get("/api/v1/supplies")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value("10"))
                .andExpect(jsonPath("$.totalElements").value("3"))
                .andExpect(jsonPath("$.totalPages").value("1"))
                .andExpect(jsonPath("$.number").value("0"))
                .andExpect(jsonPath("$.items.size()").value(3));
    }

    @Test
    @WithMockUser(username = MockUser.USERNAME, authorities = {MockUser.ROLE})
    void testGetAllUsersWithCustomPagination() throws Exception {

        // Create two users
        User userOne = UserMother.randomUser();
        createUserRepository.create(userOne, UserMother.randomPassword());
        User userTwo = UserMother.randomUser();
        createUserRepository.create(userTwo, UserMother.randomPassword());

        // Create three supplies
        Supply supplyOne = SupplyMother.random(userOne);
        createSupplyRepository.create(supplyOne, new UserId(userOne.getId()));
        Supply supplyTwo = SupplyMother.random(userOne);
        createSupplyRepository.create(supplyTwo, new UserId(userOne.getId()));
        Supply supplyThree = SupplyMother.random(userTwo);
        createSupplyRepository.create(supplyThree, new UserId(userTwo.getId()));

        String authHeader = BasicAuthHeaderGenerator.generate();

        mockMvc.perform(get("/api/v1/supplies")
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

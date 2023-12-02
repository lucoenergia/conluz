package org.lucoenergia.conluz.infrastructure.admin.supply;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.GetSupplyRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.infrastructure.admin.user.UserMother;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class CreateSupplyControllerTest extends BaseControllerTest {

    @Autowired
    private GetSupplyRepository getSupplyRepository;
    @Autowired
    private CreateUserRepository createUserRepository;

    @Test
    void testCreateSupply() throws Exception {

        String authHeader = loginAsDefaultAdmin();

        String body = """
                {
                  "id": "ES0033333333333333AA0A",
                  "userId": "12345678Z",
                  "address": "Fake Street 123",
                  "partitionCoefficient": "3.0763",
                  "registerReference": "13077A018000390000FP"
                }
        """;

        User user = UserMother.randomUserWithId("12345678Z");
        createUserRepository.create(user, UserMother.randomPassword());

        mockMvc.perform(post("/api/v1/supplies")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("ES0033333333333333AA0A"))
                .andExpect(jsonPath("$.address").value("Fake Street 123"))
                .andExpect(jsonPath("$.partitionCoefficient").value("3.0763"))
                .andExpect(jsonPath("$.name").isEmpty())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.user.id").value(user.getId()))
                .andExpect(jsonPath("$.user.number").value(user.getNumber()))
                .andExpect(jsonPath("$.user.fullName").value(user.getFullName()))
                .andExpect(jsonPath("$.user.address").value(user.getAddress()))
                .andExpect(jsonPath("$.user.email").value(user.getEmail()))
                .andExpect(jsonPath("$.user.phoneNumber").value(user.getPhoneNumber()))
                .andExpect(jsonPath("$.user.enabled").value(user.isEnabled()));

        Assertions.assertTrue(getSupplyRepository.existsById(new SupplyId("ES0033333333333333AA0A")));
    }
}

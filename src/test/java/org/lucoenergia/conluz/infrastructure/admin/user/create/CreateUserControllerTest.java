package org.lucoenergia.conluz.infrastructure.admin.user.create;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.user.Role;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.get.GetUserRepository;
import org.lucoenergia.conluz.domain.shared.UserPersonalId;
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
class CreateUserControllerTest extends BaseControllerTest {

    @Autowired
    private GetUserRepository getUserRepository;

    @Test
    void testCreateUser() throws Exception {

        String authHeader = loginAsDefaultAdmin();

        String body = """
                {
                  "personalId": "12345678Z",
                  "fullName": "John Doe",
                  "number": 1,
                  "address": "Fake Street 123",
                  "email": "johndoe@email.com",
                  "phoneNumber": "+34666555444",
                  "password": "a secure password1!",
                  "role": "PARTNER"
                }
        """;

        User expectedUser = new User();
        expectedUser.setPersonalId("12345678Z");
        expectedUser.setNumber(1);
        expectedUser.setFullName("John Doe");
        expectedUser.setAddress("Fake Street 123");
        expectedUser.setEmail("johndoe@email.com");
        expectedUser.setPhoneNumber("+34666555444");
        expectedUser.setEnabled(true);
        expectedUser.setRole(Role.PARTNER);

        mockMvc.perform(post("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.personalId").value(expectedUser.getPersonalId()))
                .andExpect(jsonPath("$.number").value(expectedUser.getNumber()))
                .andExpect(jsonPath("$.fullName").value(expectedUser.getFullName()))
                .andExpect(jsonPath("$.address").value(expectedUser.getAddress()))
                .andExpect(jsonPath("$.email").value(expectedUser.getEmail()))
                .andExpect(jsonPath("$.phoneNumber").value(expectedUser.getPhoneNumber()))
                .andExpect(jsonPath("$.enabled").value(expectedUser.isEnabled()))
                .andExpect(jsonPath("$.role").value(expectedUser.getRole().name()))
                .andExpect(jsonPath("$.password").doesNotExist());

        Assertions.assertTrue(getUserRepository.existsByPersonalId(UserPersonalId.of(expectedUser.getPersonalId())));
    }
}

package org.lucoenergia.conluz.infrastructure.admin.user.create;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.user.Role;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.get.GetUserRepository;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
                  "id": "12345678Z",
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
        expectedUser.setId("12345678Z");
        expectedUser.setNumber(1);
        expectedUser.setFullName("John Doe");
        expectedUser.setAddress("Fake Street 123");
        expectedUser.setEmail("johndoe@email.com");
        expectedUser.setPhoneNumber("+34666555444");
        expectedUser.setEnabled(true);
        expectedUser.setRole(Role.PARTNER);

        // Convert the User object to JSON
        String expectedUserAsJson = objectMapper.writeValueAsString(new UserResponse(expectedUser));

        mockMvc.perform(post("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(content().string(expectedUserAsJson));

        Assertions.assertTrue(getUserRepository.existsById(UserId.of(expectedUser.getId())));
    }
}

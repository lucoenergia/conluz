package org.lucoenergia.conluz.infrastructure.admin.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.user.DeleteUserService;
import org.lucoenergia.conluz.domain.admin.user.GetUserRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.shared.UserId;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class CreateUserControllerTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper; // ObjectMapper to convert objects to JSON
    @Autowired
    private GetUserRepository getUserRepository;
    @Autowired
    private DeleteUserService deleteUserService;

    @Test
    @WithMockUser(username = MockUser.USERNAME, authorities = {MockUser.ROLE})
    void testCreateUser() throws Exception {

        String authHeader = BasicAuthHeaderGenerator.generate();

        String body = """
                {
                  "id": "12345678Z",
                  "firstName": "John",
                  "lastName": "Doe",
                  "number": 1,
                  "address": "Fake Street 123",
                  "email": "johndoe@email.com",
                  "phoneNumber": "+34666555444",
                  "password": "a secure password1!"
                }
                                """;

        User expectedUser = new User("12345678Z", 1, "John", "Doe", "Fake Street 123",
                "johndoe@email.com", "+34666555444", true);
        // Convert the User object to JSON
        String expectedUserAsJson = objectMapper.writeValueAsString(expectedUser);

        mockMvc.perform(post("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(content().string(expectedUserAsJson));

        Assertions.assertTrue(getUserRepository.existsById(new UserId(expectedUser.getId())));

        // Removes the created user
        deleteUserService.delete(new UserId(expectedUser.getId()));
        Assertions.assertFalse(getUserRepository.existsById(new UserId(expectedUser.getId())));
    }
}

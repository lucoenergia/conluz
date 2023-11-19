package org.lucoenergia.conluz.infrastructure.admin.user;

import com.fasterxml.jackson.databind.ObjectMapper;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class UpdateUserControllerTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper; // ObjectMapper to convert objects to JSON
    @Autowired
    private CreateUserRepository createUserRepository;

    @Test
    @WithMockUser(username = MockUser.USERNAME, authorities = {MockUser.ROLE})
    void testUpdateUser() throws Exception {

        String authHeader = BasicAuthHeaderGenerator.generate();

        // Creates a user
        User user = new User("12345678Z", 1, "John", "Doe", "Fake Street 123",
                "johndoe@email.com", "+34666555444", true);
        createUserRepository.create(user, UserMother.randomPassword());

        // Modify data of the user
        User userModified = new User("12345678Z", 2, "Alice", "Smith", "Fake Street 666",
                "alicesmith@email.com", "+34666555111");
        String body = objectMapper.writeValueAsString(userModified);

        mockMvc.perform(put(String.format("/api/v1/users/%s", user.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("12345678Z"))
                .andExpect(jsonPath("$.number").value("2"))
                .andExpect(jsonPath("$.firstName").value("Alice"))
                .andExpect(jsonPath("$.lastName").value("Smith"))
                .andExpect(jsonPath("$.address").value("Fake Street 666"))
                .andExpect(jsonPath("$.email").value("alicesmith@email.com"))
                .andExpect(jsonPath("$.phoneNumber").value("+34666555111"));
    }
}

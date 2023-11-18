package org.lucoenergia.conluz.infrastructure.admin.user;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.user.CreateUserRepository;
import org.lucoenergia.conluz.domain.admin.user.CreateUserService;
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
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class DeleteUserControllerTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private GetUserRepository getUserRepository;

    @Test
    @WithMockUser(username = MockUser.USERNAME, authorities = {MockUser.ROLE})
    void testDeleteUser() throws Exception {

        // Create a user
        User user = new User("12345678Z", 1, "John", "Doe", "Fake Street 123",
                "johndoe@email.com", "+34666555444", true);
        createUserRepository.create(user, "A good pa!!w0rd");
        Assertions.assertTrue(getUserRepository.existsById(new UserId(user.getId())));

        String authHeader = BasicAuthHeaderGenerator.generate();

        mockMvc.perform(delete("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .param("id", user.getId()))
                .andExpect(status().isOk());
        Assertions.assertFalse(getUserRepository.existsById(new UserId(user.getId())));
    }
}

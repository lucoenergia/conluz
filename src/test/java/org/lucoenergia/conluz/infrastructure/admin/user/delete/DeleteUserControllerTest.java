package org.lucoenergia.conluz.infrastructure.admin.user.delete;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.admin.user.get.GetUserRepository;
import org.lucoenergia.conluz.domain.shared.UserPersonalId;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class DeleteUserControllerTest extends BaseControllerTest {

    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private GetUserRepository getUserRepository;

    @Test
    void testDeleteUser() throws Exception {

        // Create a user
        User user = UserMother.randomUser();
        createUserRepository.create(user, "A good pa!!w0rd");
        Assertions.assertTrue(getUserRepository.existsByPersonalId(UserPersonalId.of(user.getPersonalId())));

        // Login as default admin
        String authHeader = loginAsDefaultAdmin();

        mockMvc.perform(delete(String.format("/api/v1/users/%s", user.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
        Assertions.assertFalse(getUserRepository.existsByPersonalId(UserPersonalId.of(user.getPersonalId())));
    }
}

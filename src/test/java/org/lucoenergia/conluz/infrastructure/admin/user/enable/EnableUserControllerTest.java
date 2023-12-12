package org.lucoenergia.conluz.infrastructure.admin.user.enable;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.admin.user.get.GetUserRepository;
import org.lucoenergia.conluz.domain.shared.UserPersonalId;
import org.lucoenergia.conluz.infrastructure.admin.user.UserMother;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class EnableUserControllerTest extends BaseControllerTest {

    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private GetUserRepository getUserRepository;

    @Test
    void testEnableUser() throws Exception {

        // Create a user
        User user = UserMother.randomUser();
        user.setEnabled(false);
        createUserRepository.create(user, UserMother.randomPassword());
        Assertions.assertTrue(getUserRepository.existsByPersonalId(UserPersonalId.of(user.getPersonalId())));

        String authHeader = loginAsDefaultAdmin();

        mockMvc.perform(post(String.format("/api/v1/users/%s/enable", user.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
        Assertions.assertTrue(getUserRepository.findByPersonalId(UserPersonalId.of(user.getPersonalId())).get().isEnabled());
    }
}
